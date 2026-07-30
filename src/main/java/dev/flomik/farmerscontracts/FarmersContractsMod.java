package dev.flomik.farmerscontracts;

import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.board.ContractBoardBlock;
import dev.flomik.farmerscontracts.board.ContractBoardBlockEntity;
import dev.flomik.farmerscontracts.board.ContractBoardMenu;
import dev.flomik.farmerscontracts.board.SelfTest;
import dev.flomik.farmerscontracts.client.ContractBoardScreen;
import dev.flomik.farmerscontracts.contract.BalanceCheck;
import dev.flomik.farmerscontracts.contract.ContractDataReloadListener;
import dev.flomik.farmerscontracts.contract.ContractDebugCommand;
import dev.flomik.farmerscontracts.contract.ContractProgress;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.contract.GeneratedLine;
import dev.flomik.farmerscontracts.item.ContractTicketItem;
import dev.flomik.farmerscontracts.villager.ContractVillagerMemories;
import dev.flomik.farmerscontracts.worldgen.WorldgenRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.List;

@Mod(FarmersContractsMod.MODID)
public class FarmersContractsMod {
    public static final String MODID = "farmerscontracts";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    public static final RegistryObject<ContractBoardBlock> CONTRACT_BOARD = BLOCKS.register(
            "contract_board", () -> new ContractBoardBlock(BlockBehaviour.Properties.of()));
    public static final RegistryObject<BlockItem> CONTRACT_BOARD_ITEM = ITEMS.register(
            "contract_board", () -> new BlockItem(CONTRACT_BOARD.get(), new Item.Properties()));
    public static final RegistryObject<ContractTicketItem> CONTRACT_TICKET =
            ITEMS.register("contract_ticket", () -> new ContractTicketItem(new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ContractBoardBlockEntity>> CONTRACT_BOARD_ENTITY =
            BLOCK_ENTITY_TYPES.register("contract_board", () -> BlockEntityType.Builder.of(
                    ContractBoardBlockEntity::new, CONTRACT_BOARD.get()).build(null));
    public static final RegistryObject<MenuType<ContractBoardMenu>> CONTRACT_BOARD_MENU =
            MENU_TYPES.register("contract_board", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new ContractBoardMenu(windowId, inv)));

    public static final RegistryObject<CreativeModeTab> CONTRACTS_TAB =
            CREATIVE_MODE_TABS.register("contracts_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.farmerscontracts"))
                    .icon(() -> CONTRACT_BOARD_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CONTRACT_BOARD_ITEM.get());
                        output.accept(CONTRACT_TICKET.get());
                    })
                    .build());

    public FarmersContractsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        ContractVillagerMemories.MEMORY_MODULE_TYPES.register(modEventBus);
        WorldgenRegistry.STRUCTURE_PROCESSOR_TYPES.register(modEventBus);

        modEventBus.addListener(this::onClientSetup);

        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(CONTRACT_BOARD_MENU.get(), ContractBoardScreen::new));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Farmer's Contracts server starting");

        if (BalanceCheck.isRequested()) {
            BalanceCheck.run();
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // Needs a fully-ticking world (chunk loading, block placement) - unlike BalanceCheck,
        // which is pure data-driven math and runs earlier in onServerStarting.
        if (SelfTest.isRequested()) {
            boolean passed = SelfTest.run(event.getServer());
            LOGGER.info(passed ? "SelfTest passed" : "SelfTest FAILED");
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ContractDataReloadListener());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ContractDebugCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Fired before the skip is applied, so getDayTime() here is still the pre-skip value -
        // the delta is exactly how many ticks vanilla is about to fast-forward through.
        long ticksSkipped = event.getNewTime() - serverLevel.getDayTime();
        ContractProgress.get(serverLevel).addSleepDayOffset(serverLevel.dimension(), ticksSkipped);
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof ContractTicketItem)) {
            return;
        }
        GeneratedContract data = ContractTicketItem.dataOf(event.getItemStack());
        if (data == null) {
            return;
        }
        Player player = event.getEntity();
        List<Component> tooltip = event.getToolTip();

        tooltip.add(Component.translatable("tooltip.farmerscontracts.needs").withStyle(ChatFormatting.WHITE));
        for (GeneratedLine line : GeneratedLine.mergeByItem(data.objectives())) {
            int have = player == null ? 0 : Math.min(countMatching(player, line.stack().getItem()), line.amount());
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
