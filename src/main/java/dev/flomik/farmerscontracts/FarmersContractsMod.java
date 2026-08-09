package dev.flomik.farmerscontracts;

import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.board.ContractBoardBlock;
import dev.flomik.farmerscontracts.board.ContractBoardBlockEntity;
import dev.flomik.farmerscontracts.board.ContractBoardMenu;
import dev.flomik.farmerscontracts.board.SelfTest;
import dev.flomik.farmerscontracts.box.ContractBoxBlock;
import dev.flomik.farmerscontracts.box.ContractBoxBlockEntity;
import dev.flomik.farmerscontracts.box.ContractBoxMenu;
import dev.flomik.farmerscontracts.contract.BalanceCheck;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.ContractDataReloadListener;
import dev.flomik.farmerscontracts.contract.ContractDebugCommand;
import dev.flomik.farmerscontracts.condition.RecipeGating;
import dev.flomik.farmerscontracts.item.ContractBoxItem;
import dev.flomik.farmerscontracts.item.ContractTicketItem;
import dev.flomik.farmerscontracts.villager.ContractVillagerMemories;
import dev.flomik.farmerscontracts.worldgen.VillagePoolInjector;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;

public class FarmersContractsMod implements ModInitializer {
    public static final String MODID = "farmerscontracts";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ContractBoardBlock CONTRACT_BOARD =
            register(BuiltInRegistries.BLOCK, "contract_board", new ContractBoardBlock(BlockBehaviour.Properties.of()));
    public static final BlockItem CONTRACT_BOARD_ITEM =
            register(BuiltInRegistries.ITEM, "contract_board", new BlockItem(CONTRACT_BOARD, new Item.Properties()));
    public static final ContractTicketItem CONTRACT_TICKET =
            register(BuiltInRegistries.ITEM, "contract_ticket", new ContractTicketItem(new Item.Properties()));
    public static final BlockEntityType<ContractBoardBlockEntity> CONTRACT_BOARD_ENTITY = register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "contract_board",
            BlockEntityType.Builder.of(ContractBoardBlockEntity::new, CONTRACT_BOARD).build(null));
    public static final MenuType<ContractBoardMenu> CONTRACT_BOARD_MENU = register(
            BuiltInRegistries.MENU, "contract_board",
            new MenuType<>(ContractBoardMenu::new, FeatureFlags.VANILLA_SET));

    public static final ContractBoxBlock CONTRACT_BOX =
            register(BuiltInRegistries.BLOCK, "contract_box", new ContractBoxBlock(BlockBehaviour.Properties.of()));
    public static final ContractBoxItem CONTRACT_BOX_ITEM =
            register(BuiltInRegistries.ITEM, "contract_box", new ContractBoxItem(CONTRACT_BOX, new Item.Properties()));
    public static final BlockEntityType<ContractBoxBlockEntity> CONTRACT_BOX_ENTITY = register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "contract_box",
            BlockEntityType.Builder.of(ContractBoxBlockEntity::new, CONTRACT_BOX).build(null));
    public static final MenuType<ContractBoxMenu> CONTRACT_BOX_MENU = register(
            BuiltInRegistries.MENU, "contract_box",
            new MenuType<>(ContractBoxMenu::new, FeatureFlags.VANILLA_SET));

    public static final CreativeModeTab CONTRACTS_TAB = register(
            BuiltInRegistries.CREATIVE_MODE_TAB, "contracts_tab",
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.farmerscontracts"))
                    .icon(() -> new ItemStack(CONTRACT_BOARD_ITEM))
                    .displayItems((parameters, output) -> {
                        // Unbreakable boards (Config.boardCanBreak() == false, ported from
                        // Bountiful's board.canBreak) must not be craftable either - see
                        // RecipeGating, registered below.
                        if (Config.boardCanBreak()) {
                            output.accept(CONTRACT_BOARD_ITEM);
                        }
                        output.accept(CONTRACT_TICKET);
                        if (Config.deliveryMode() != Config.DeliveryMode.TICKET_ONLY) {
                            output.accept(CONTRACT_BOX_ITEM);
                        }
                    })
                    .build());

    @Override
    public void onInitialize() {
        Config.load();

        // Force registration-holder classes to load before Fabric freezes the built-in
        // registries at the end of mod init - their fields are otherwise only touched lazily
        // from block/item/villager-AI code paths, which by then run too late and crash with
        // "Registry is already frozen".
        var ignoredComponents = ContractDataComponents.CONTRACT_DATA;
        var ignoredMemory = ContractVillagerMemories.NEAREST_BOARD;

        // Fabric equivalent of the NeoForge branch's neoforge:conditions-gated recipes - see
        // RecipeGating/mixin.RecipeGatingMixin.
        RecipeGating.register(ResourceLocation.fromNamespaceAndPath(MODID, "contract_board"), Config::boardCanBreak);
        RecipeGating.register(ResourceLocation.fromNamespaceAndPath(MODID, "contract_box"),
                () -> Config.deliveryMode() != Config.DeliveryMode.TICKET_ONLY);

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ContractDataReloadListener());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ContractDebugCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Farmer's Contracts server starting");

            VillagePoolInjector.injectAll(server.registryAccess());

            if (BalanceCheck.isRequested()) {
                BalanceCheck.run();
                server.halt(false);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Needs a fully-ticking world (chunk loading, block placement) - unlike BalanceCheck,
            // which is pure data-driven math and runs earlier at SERVER_STARTING.
            if (SelfTest.isRequested()) {
                boolean passed = SelfTest.run(server);
                LOGGER.info(passed ? "SelfTest passed" : "SelfTest FAILED");
                server.halt(false);
            }
        });

        LOGGER.info("Farmer's Contracts initialized");
    }

    private static <V, T extends V> T register(Registry<V> registry, String name, T value) {
        return Registry.register(registry, ResourceLocation.fromNamespaceAndPath(MODID, name), value);
    }
}
