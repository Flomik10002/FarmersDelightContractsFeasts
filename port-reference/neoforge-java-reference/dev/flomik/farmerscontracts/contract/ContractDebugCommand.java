package dev.flomik.farmerscontracts.contract;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ContractDebugCommand {

    private ContractDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("farmerscontracts")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug").executes(ContractDebugCommand::run))
                .then(Commands.literal("generate")
                        .then(Commands.argument("customer", ResourceLocationArgument.id())
                                .executes(ContractDebugCommand::generate))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        long completed = ContractProgress.get(source.getLevel()).completedContracts();
        source.sendSuccess(() -> Component.literal(
                "=== completed contracts (hidden, dev-only): " + completed + " (unlocked tier: "
                        + ContractRarity.forReputation(completed).getSerializedName() + ") ==="
        ), false);

        source.sendSuccess(() -> Component.literal("=== rarity color palette (mod.md §13) ==="), false);
        for (ContractRarity rarity : ContractRarity.values()) {
            Component line = Component.literal(
                    "  [" + rarity.getSerializedName() + "] Example " + rarity.getSerializedName() + " contract offer"
            ).withStyle(rarity.style());
            source.sendSuccess(() -> line, false);
        }

        Map<ResourceLocation, Customer> customers = ContractContent.customers();
        Map<ResourceLocation, ContractPool> pools = ContractContent.pools();

        source.sendSuccess(() -> Component.literal(
                "=== farmerscontracts: " + customers.size() + " customer(s), " + pools.size() + " pool(s) ==="
        ), false);

        for (Map.Entry<ResourceLocation, Customer> entry : customers.entrySet()) {
            Customer customer = entry.getValue();
            source.sendSuccess(() -> Component.literal("Customer " + entry.getKey() + ": \"" + customer.name() + "\""), false);
            source.sendSuccess(() -> Component.literal("  objectives: " + customer.objectivePools()), false);
            source.sendSuccess(() -> Component.literal("  rewards: " + customer.rewardPools()), false);
        }

        for (Map.Entry<ResourceLocation, ContractPool> entry : pools.entrySet()) {
            source.sendSuccess(() -> Component.literal("Pool " + entry.getKey() + ":"), false);
            for (ContractPoolEntry poolEntry : entry.getValue().entries()) {
                ItemStack[] matches = poolEntry.item().getItems();
                String items = java.util.Arrays.stream(matches)
                        .map(ItemStack::getHoverName)
                        .map(Component::getString)
                        .collect(Collectors.joining(" / "));
                String worthText = poolEntry.unitWorth()
                        .map(w -> String.format("%.1f", w))
                        .orElseGet(() -> matches.length == 0 ? "auto" : String.format("%.1f (auto)", poolEntry.worthPerUnit(matches[0].getItem())));
                Component line = Component.literal(String.format(
                        "  [%s] %s x%d-%d (worth %s each)",
                        poolEntry.rarity().getSerializedName(), items, poolEntry.amount().min(), poolEntry.amount().max(), worthText
                )).withStyle(poolEntry.rarity().style());
                source.sendSuccess(() -> line, false);
            }
        }

        return customers.size() + pools.size();
    }

    private static int generate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation customerId = ResourceLocationArgument.getId(ctx, "customer");
        ServerLevel level = source.getLevel();

        Optional<GeneratedContract> generated = ContractGenerator.generate(customerId, level);
        if (generated.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Could not generate a contract for " + customerId + " (unknown customer, or empty/missing pools?)"
            ));
            return 0;
        }

        GeneratedContract contract = generated.get();

        Component header = Component.literal(
                "=== " + contract.customerName() + " [" + contract.rarity().getSerializedName() + "] ==="
        ).withStyle(contract.rarity().style());
        source.sendSuccess(() -> header, false);

        source.sendSuccess(() -> Component.literal("Needs:"), false);
        for (GeneratedLine line : GeneratedLine.mergeByItem(contract.objectives())) {
            source.sendSuccess(() -> lineComponent(line), false);
        }

        source.sendSuccess(() -> Component.literal("Rewards:"), false);
        for (GeneratedLine line : GeneratedLine.mergeByItem(contract.rewards())) {
            source.sendSuccess(() -> lineComponent(line), false);
        }
        source.sendSuccess(() -> Component.literal("  " + contract.xp() + " XP"), false);

        return 1;
    }

    private static Component lineComponent(GeneratedLine line) {
        return Component.literal(String.format(
                "  %dx %s (worth %.1f)",
                line.amount(), line.stack().getHoverName().getString(), line.worth()
        ));
    }
}
