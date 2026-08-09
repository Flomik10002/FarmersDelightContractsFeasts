package dev.flomik.farmerscontracts.item;

import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.contract.GeneratedLine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// Shared by ContractTicketItem/ContractBoxItem's appendHoverText overrides. Pure vanilla logic,
// no client-only dependency - callers decide whether/how to obtain a Player to compute live
// "have X of Y" progress (see ClientSetup#currentPlayer, appendHoverText only ever runs
// client-side, so Minecraft.getInstance() is safe to touch there but not here).
public final class ContractTooltips {

    private ContractTooltips() {
    }

    public static void appendContractTooltip(GeneratedContract data, Player player, List<Component> tooltip, boolean alwaysFulfilled) {
        tooltip.add(Component.translatable("tooltip.farmerscontracts.needs").withStyle(ChatFormatting.WHITE));
        for (GeneratedLine line : GeneratedLine.mergeByItem(data.objectives())) {
            int have = alwaysFulfilled
                    ? line.amount()
                    : (player == null ? 0 : Math.min(countMatching(player, line.stack().getItem()), line.amount()));
            tooltip.add(Component.translatable("tooltip.farmerscontracts.objective_line",
                            have, line.amount(), line.stack().getHoverName())
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.farmerscontracts.rewards").withStyle(ChatFormatting.WHITE));
        for (GeneratedLine line : GeneratedLine.mergeByItem(data.rewards())) {
            tooltip.add(Component.translatable("tooltip.farmerscontracts.reward_line",
                            line.amount(), line.stack().getHoverName())
                    .withStyle(ChatFormatting.GRAY));
        }

        if (player != null) {
            long ticksLeft = data.expiresAtGameTime() - player.level().getGameTime();
            tooltip.add(formatTimeLeft(ticksLeft));
        }
    }

    private static int countMatching(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static Component formatTimeLeft(long ticksLeft) {
        if (ticksLeft <= 0) {
            return Component.translatable("tooltip.farmerscontracts.expired").withStyle(ChatFormatting.RED);
        }
        long totalSeconds = ticksLeft / 20L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        Component text;
        if (days > 0) {
            text = Component.translatable("tooltip.farmerscontracts.time_days", days, hours);
        } else if (hours > 0) {
            text = Component.translatable("tooltip.farmerscontracts.time_hours", hours, minutes);
        } else {
            text = Component.translatable("tooltip.farmerscontracts.time_minutes", minutes, seconds);
        }
        return text.copy().withStyle(ChatFormatting.DARK_GRAY);
    }
}
