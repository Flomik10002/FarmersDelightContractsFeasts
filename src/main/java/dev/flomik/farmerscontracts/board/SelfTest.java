package dev.flomik.farmerscontracts.board;

import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.Config;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.api.ContractScoreboard;
import dev.flomik.farmerscontracts.api.event.ContractFulfilledCallback;
import dev.flomik.farmerscontracts.box.ContractBoxBlock;
import dev.flomik.farmerscontracts.box.ContractBoxBlockEntity;
import dev.flomik.farmerscontracts.box.ContractBoxMenu;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.item.ContractBoxItem;
import dev.flomik.farmerscontracts.contract.ContractContent;
import dev.flomik.farmerscontracts.contract.ContractProgress;
import dev.flomik.farmerscontracts.contract.ContractRarity;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.contract.GeneratedLine;
import dev.flomik.farmerscontracts.contract.RewardBundle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import dev.flomik.farmerscontracts.testutil.FakePlayer;
import dev.flomik.farmerscontracts.testutil.FakePlayerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class SelfTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos TICK_TEST_POS = new BlockPos(1_000_000, 100, 1_000_000);
    private static final BlockPos STILL_VALID_TEST_POS = new BlockPos(1_000_010, 100, 1_000_000);
    private static final BlockPos BOARD_PRESERVE_TEST_POS = new BlockPos(1_000_015, 100, 1_000_000);
    private static final BlockPos BOX_SEAL_TEST_POS = new BlockPos(1_000_020, 100, 1_000_000);
    private static final BlockPos BOX_MISMATCH_TEST_POS = new BlockPos(1_000_025, 100, 1_000_000);
    private static final BlockPos BOX_EXPIRED_TEST_POS = new BlockPos(1_000_030, 100, 1_000_000);
    private static final BlockPos BOX_GATING_BOARD_POS = new BlockPos(1_000_040, 100, 1_000_000);
    private static final BlockPos BOX_GATING_BOX_POS = new BlockPos(1_000_050, 100, 1_000_000);
    private static final BlockPos BOX_NONSTACKABLE_TEST_POS = new BlockPos(1_000_060, 100, 1_000_000);
    private static final BlockPos BOARD_GLOBAL_TEST_POS_A = new BlockPos(1_000_070, 100, 1_000_000);
    private static final BlockPos BOARD_GLOBAL_TEST_POS_B = new BlockPos(1_000_080, 100, 1_000_000);
    private static final BlockPos BOX_CONTENTS_TEST_POS = new BlockPos(1_000_090, 100, 1_000_000);
    private static final BlockPos CAPABILITY_TEST_POS = new BlockPos(1_000_100, 100, 1_000_000);
    private static final BlockPos HOPPER_FILL_TEST_POS = new BlockPos(1_000_110, 100, 1_000_000);

    private final List<String> failures = new ArrayList<>();
    private int checks = 0;

    record FiredCompletion(net.minecraft.server.level.ServerPlayer player, GeneratedContract contract) {
    }

    private final List<FiredCompletion> firedCompletions = new ArrayList<>();

    private SelfTest() {
    }

    public static boolean isRequested() {
        return Boolean.getBoolean("farmerscontracts.selftest");
    }

    public static boolean run(MinecraftServer server) {
        SelfTest test = new SelfTest();
        ServerLevel overworld = server.overworld();

        ContractFulfilledCallback.EVENT.register((player, contract) ->
                test.firedCompletions.add(new FiredCompletion(player, contract)));

        test.testMaskedBoardContainerDoesNotAliasLiveStack();
        test.testTimeSetImmunity(overworld);
        test.testBoardRefreshCatchesUpAfterLongAbsence(overworld);
        test.testBoardGlobalStateSharesContentsAcrossBoards(overworld);
        test.testDuplicateObjectiveConsumption(overworld);
        test.testStillValidClosesOnDistanceAndBlockRemoval(overworld);
        test.testBoardPreservesOffersAcrossBreakAndReplace(overworld);
        test.testBoxAcceptsNonStackableItemsBeyondTheirNormalMax(overworld);
        test.testBoxSlotToSlotDragMergesNonStackableItems(overworld);
        test.testBoxClientSidePredictionDoesNotTruncateMergedCount(overworld);
        test.testBoxMouseDragAcrossSlotsNeverLosesCount(overworld);
        test.testBoxOnlyAcceptsContractObjectiveItems(overworld);
        test.testHopperCanFillTheBoxWithinTheWhitelist(overworld);
        test.testContractBoxCannotBeStoredInOtherContainers(overworld);
        test.testContractBoxCannotBeStoredThroughItemStorage(overworld);
        test.testContractBoxItemStorageMatchesTheHandFilledRules(overworld);
        test.testBoxSealValidatesAndConsumesContents(overworld);
        test.testBoxSealRejectsForeignItemsAndOverfill(overworld);
        test.testBoxSealExpiredContractVoidsTicketWithoutSealing(overworld);
        test.testBoardDeliversSealedBoxAndGrantsReward(overworld);
        test.testBoardDeliverExpiredBoxVoidsWithoutReward(overworld);
        test.testDeliveryModeGatesTurnInPaths(overworld);
        test.testContractRarityTierPointsMapping();
        test.testTicketTurnInAwardsProgressScoreboardAndEvent(overworld);
        test.testBoxDeliveryAwardsProgressScoreboardAndEvent(overworld);

        LOGGER.info("=== SelfTest: {}/{} checks passed ===", test.checks - test.failures.size(), test.checks);
        if (test.failures.isEmpty()) {
            LOGGER.info("SELF-TEST RESULT: ALL PASSED");
            return true;
        }
        for (String failure : test.failures) {
            LOGGER.error("[FAIL] {}", failure);
        }
        LOGGER.error("SELF-TEST RESULT: FAILED ({} of {} checks)", test.failures.size(), test.checks);
        return false;
    }

    private void check(boolean condition, String description) {
        checks++;
        if (condition) {
            LOGGER.info("[PASS] {}", description);
        } else {
            failures.add(description);
        }
    }

    private void testMaskedBoardContainerDoesNotAliasLiveStack() {
        SimpleContainer real = new SimpleContainer(1);
        real.setItem(0, new ItemStack(Items.EMERALD, 5));

        MaskedBoardContainer masked = new MaskedBoardContainer(real, new HashSet<>(), () -> {
        });

        ItemStack viewed = masked.getItem(0);
        viewed.shrink(5);

        check(real.getItem(0).getCount() == 5,
                "MaskedBoardContainer.getItem must not alias the real container's stack");
    }

    private void testTimeSetImmunity(ServerLevel overworld) {
        BlockState state = FarmersContractsMod.CONTRACT_BOARD.defaultBlockState();
        ContractBoardBlockEntity entity = new ContractBoardBlockEntity(TICK_TEST_POS, state);

        ContractBoardBlockEntity.tick(overworld, TICK_TEST_POS, state, entity);
        int filledBefore = countFilled(entity);

        overworld.setDayTime(overworld.getDayTime() + 5L * 24000L);
        ContractBoardBlockEntity.tick(overworld, TICK_TEST_POS, state, entity);

        int filledAfter = countFilled(entity);
        check(filledAfter == filledBefore,
                "/time set jump (getDayTime only) must not trigger a board refresh (filled slots: " + filledBefore + " -> " + filledAfter + ")");
    }

    private void testBoardRefreshCatchesUpAfterLongAbsence(ServerLevel overworld) {
        BlockPos pos = TICK_TEST_POS.above();
        BlockState state = FarmersContractsMod.CONTRACT_BOARD.defaultBlockState();
        ContractBoardBlockEntity entity = new ContractBoardBlockEntity(pos, state);

        ContractBoardBlockEntity.tick(overworld, pos, state, entity);
        long updateFrequencyTicks = Config.boardUpdateFrequencySeconds() * 20L;
        long veryStale = overworld.getGameTime() - updateFrequencyTicks * (ContractBoardBlockEntity.SLOTS + 10L);
        entity.forceLastUpdateGameTimeForTest(veryStale);
        for (int i = 0; i < ContractBoardBlockEntity.SLOTS; i++) {
            entity.container().setItem(i, ItemStack.EMPTY);
        }
        check(countFilled(entity) == 0, "Sanity: board must actually be empty before the catch-up tick");

        ContractBoardBlockEntity.tick(overworld, pos, state, entity);

        int filled = countFilled(entity);
        check(filled > 0, "A board that catches up after a long absence must end up with offers again (found " + filled + ")");
        check(filled <= ContractBoardBlockEntity.SLOTS,
                "Catch-up refresh must never exceed the board's own slot count (found " + filled + ")");
    }

    private void testBoardGlobalStateSharesContentsAcrossBoards(ServerLevel overworld) {
        Config.setBoardGlobalStateForTest(true);
        try {
            BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.defaultBlockState();
            overworld.setBlockAndUpdate(BOARD_GLOBAL_TEST_POS_A, boardState);
            overworld.setBlockAndUpdate(BOARD_GLOBAL_TEST_POS_B, boardState);
            ContractBoardBlockEntity entityA = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_GLOBAL_TEST_POS_A);
            ContractBoardBlockEntity entityB = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_GLOBAL_TEST_POS_B);
            check(entityA != null && entityB != null, "Both boards must exist right after placing them");
            if (entityA == null || entityB == null) {
                return;
            }

            Config.setBoardGlobalStateForTest(true);
            ContractBoardBlockEntity.tick(overworld, BOARD_GLOBAL_TEST_POS_A, boardState, entityA);
            int filledA = countFilled(entityA);
            check(filledA > 0, "Board A must be populated after its own first tick");

            Config.setBoardGlobalStateForTest(true);
            int filledB = countFilled(entityB);
            check(filledB == filledA,
                    "Board B must immediately show the same offers as board A in global mode (board A: "
                            + filledA + ", board B: " + filledB + ")");

            Config.setBoardGlobalStateForTest(true);
            entityA.container().setItem(0, new ItemStack(Items.EMERALD));
            check(entityB.container().getItem(0).is(Items.EMERALD),
                    "Writing into board A's container in global mode must be visible through board B's accessor too");
        } finally {
            Config.setBoardGlobalStateForTest(false);
        }
    }

    private void testDuplicateObjectiveConsumption(ServerLevel overworld) {
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();

        GeneratedContract contract = new GeneratedContract(
                ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, "selftest_customer"),
                "SelfTest Customer",
                ContractRarity.COMMON,
                List.of(
                        new GeneratedLine(new ItemStack(Items.WHEAT), 5, 5.0),
                        new GeneratedLine(new ItemStack(Items.WHEAT), 3, 3.0)
                ),
                new RewardBundle(List.of(new GeneratedLine(new ItemStack(Items.EMERALD), 1, 1.0)), 10),
                overworld.getGameTime() + 1_000_000L
        );
        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
        ticket.set(ContractDataComponents.CONTRACT_DATA, contract);

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD;

        player.getInventory().add(new ItemStack(Items.WHEAT, 6));
        boolean underfundedResult = block.tryTurnIn(overworld, player, ticket);
        check(!underfundedResult,
                "Contract with duplicate-item objective lines must NOT be turned in with only 6 wheat");
        check(countMatching(player, Items.WHEAT) == 6,
                "A failed turn-in must not partially consume the player's items");
        check(ticket.getCount() == 1,
                "A failed turn-in must not consume the ticket");

        player.getInventory().add(new ItemStack(Items.WHEAT, 2));
        boolean fundedResult = block.tryTurnIn(overworld, player, ticket);
        check(fundedResult,
                "Contract must be turned in successfully once the player has the full combined 8 wheat");
        check(countMatching(player, Items.WHEAT) == 0,
                "A successful turn-in must consume the full combined amount");
        check(countMatching(player, Items.EMERALD) == 1,
                "A successful turn-in must grant the reward (1 emerald)");
        check(ticket.isEmpty() || ticket.getCount() == 0,
                "A successful turn-in must consume the ticket");
    }

    private void testStillValidClosesOnDistanceAndBlockRemoval(ServerLevel overworld) {
        BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.defaultBlockState();
        overworld.setBlockAndUpdate(STILL_VALID_TEST_POS, boardState);
        ContractBoardBlockEntity entity = (ContractBoardBlockEntity) overworld.getBlockEntity(STILL_VALID_TEST_POS);
        check(entity != null, "Board block entity must exist right after placing the block");
        if (entity == null) {
            return;
        }

        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.setPos(STILL_VALID_TEST_POS.getX() + 0.5, STILL_VALID_TEST_POS.getY(), STILL_VALID_TEST_POS.getZ() + 0.5);

        Inventory inventory = player.getInventory();
        AbstractContainerMenu menu = entity.createMenu(1, inventory, player);

        check(menu.stillValid(player), "Menu must be valid while the player stands next to the board");

        player.setPos(STILL_VALID_TEST_POS.getX() + 1000, STILL_VALID_TEST_POS.getY(), STILL_VALID_TEST_POS.getZ());
        check(!menu.stillValid(player), "Menu must become invalid once the player walks far away");

        player.setPos(STILL_VALID_TEST_POS.getX() + 0.5, STILL_VALID_TEST_POS.getY(), STILL_VALID_TEST_POS.getZ() + 0.5);
        check(menu.stillValid(player), "Menu must become valid again once the player is back in range");

        overworld.setBlockAndUpdate(STILL_VALID_TEST_POS, Blocks.AIR.defaultBlockState());
        check(!menu.stillValid(player), "Menu must become invalid once the board block is destroyed");
    }

    private void testBoardPreservesOffersAcrossBreakAndReplace(ServerLevel overworld) {
        BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.defaultBlockState();
        overworld.setBlockAndUpdate(BOARD_PRESERVE_TEST_POS, boardState);
        ContractBoardBlockEntity entity = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_PRESERVE_TEST_POS);
        check(entity != null, "Board block entity must exist right after placing the block");
        if (entity == null) {
            return;
        }

        ContractBoardBlockEntity.tick(overworld, BOARD_PRESERVE_TEST_POS, boardState, entity);
        int filledBefore = countFilled(entity);
        check(filledBefore > 0, "Board must actually contain offers after its initial fill");

        ItemStack drop = new ItemStack(FarmersContractsMod.CONTRACT_BOARD);
        drop.applyComponents(entity.collectComponents());

        overworld.setBlockAndUpdate(BOARD_PRESERVE_TEST_POS, Blocks.AIR.defaultBlockState());
        overworld.setBlockAndUpdate(BOARD_PRESERVE_TEST_POS, boardState);
        ContractBoardBlockEntity freshEntity = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_PRESERVE_TEST_POS);
        check(freshEntity != null, "A freshly placed board block entity must exist");
        if (freshEntity == null) {
            return;
        }
        check(countFilled(freshEntity) == 0, "Sanity: a brand new block entity must start with an empty container");

        freshEntity.applyComponentsFromItemStack(drop);
        check(countFilled(freshEntity) == filledBefore,
                "A board's offers must survive being carried on the dropped item and restored on placement (filled slots: "
                        + filledBefore + " -> " + countFilled(freshEntity) + ")");

        ContractBoardBlockEntity.tick(overworld, BOARD_PRESERVE_TEST_POS, boardState, freshEntity);
        check(countFilled(freshEntity) == filledBefore,
                "Ticking the restored board again immediately must not wipe/corrupt the offers or re-run initial population");
    }

    private void testBoxAcceptsNonStackableItemsBeyondTheirNormalMax(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        overworld.setBlockAndUpdate(BOX_NONSTACKABLE_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_NONSTACKABLE_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }
        check(new ItemStack(Items.CAKE).getMaxStackSize() == 1,
                "Sanity: cake must actually be non-stackable");

        box.setItem(0, new ItemStack(Items.CAKE, 5));
        check(box.getItem(0).getCount() == 5,
                "A box slot must hold more than 1 non-stackable item when set directly (got " + box.getItem(0).getCount() + ")");
        box.setItem(0, ItemStack.EMPTY);

        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        for (int i = 0; i < 5; i++) {
            player.getInventory().items.set(9 + i, new ItemStack(Items.CAKE, 1));
        }

        ContractBoxMenu menu = new ContractBoxMenu(1, player.getInventory(), box);
        for (int i = 0; i < 5; i++) {
            menu.quickMoveStack(player, 5 + i);
        }

        int filledBoxSlots = 0;
        int totalCakes = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.getItem(i);
            if (!stack.isEmpty()) {
                filledBoxSlots++;
                totalCakes += stack.getCount();
            }
        }
        check(totalCakes == 5, "All 5 shift-clicked cakes must end up in the box (found " + totalCakes + ")");
        check(filledBoxSlots == 1, "5 shift-clicked non-stackable items must merge into a single box slot (used " + filledBoxSlots + " slots)");
    }

    private void testBoxSlotToSlotDragMergesNonStackableItems(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        BlockPos pos = BOX_NONSTACKABLE_TEST_POS.above();
        overworld.setBlockAndUpdate(pos, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(pos);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        box.setItem(0, new ItemStack(Items.CAKE, 3));
        box.setItem(1, new ItemStack(Items.CAKE, 2));

        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        ContractBoxMenu menu = new ContractBoxMenu(1, player.getInventory(), box);

        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().getCount() == 3, "Picking up slot 0 must carry all 3 cakes on the cursor (got " + menu.getCarried().getCount() + ")");
        check(box.getItem(0).isEmpty(), "Slot 0 must be empty after picking up its whole stack");

        menu.clicked(1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().isEmpty(), "Clicking the carried cakes onto a matching slot must fully merge (got " + menu.getCarried().getCount() + ")");
        check(box.getItem(1).getCount() == 5,
                "Dragging 3 cakes from slot 0 onto slot 1's existing 2 cakes must merge into 5 (got " + box.getItem(1).getCount() + ")");
    }

    private void testBoxClientSidePredictionDoesNotTruncateMergedCount(ServerLevel overworld) {
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();

        ContractBoxMenu clientSideMenu = new ContractBoxMenu(1, player.getInventory());

        clientSideMenu.getSlot(0).container.setItem(0, new ItemStack(Items.CAKE, 3));
        clientSideMenu.getSlot(1).container.setItem(1, new ItemStack(Items.CAKE, 2));

        clientSideMenu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        clientSideMenu.clicked(1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);

        check(clientSideMenu.getSlot(1).getItem().getCount() == 5,
                "The client's own local click prediction must merge non-stackable counts correctly (got "
                        + clientSideMenu.getSlot(1).getItem().getCount() + ")");
    }

    private void testBoxMouseDragAcrossSlotsNeverLosesCount(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        BlockPos pos = BOX_NONSTACKABLE_TEST_POS.above().above();
        overworld.setBlockAndUpdate(pos, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(pos);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        box.setItem(0, new ItemStack(Items.CAKE, 3));
        box.setItem(1, new ItemStack(Items.CAKE, 2));
        box.setItem(2, ItemStack.EMPTY);

        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        ContractBoxMenu menu = new ContractBoxMenu(1, player.getInventory(), box);

        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().getCount() == 3, "Setup: picking up slot 0 must carry 3 cakes");

        menu.clicked(1, 0, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
        menu.clicked(1, 1, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
        menu.clicked(2, 1, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);
        menu.clicked(2, 2, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, player);

        int total = box.getItem(1).getCount() + box.getItem(2).getCount() + menu.getCarried().getCount();
        check(total == 5,
                "A multi-slot drag touching an already-stacked non-stackable item must never destroy items (slot1="
                        + box.getItem(1).getCount() + ", slot2=" + box.getItem(2).getCount() + ", cursor=" + menu.getCarried().getCount()
                        + ", total=" + total + ", expected 5)");
    }

    private void testBoxOnlyAcceptsContractObjectiveItems(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        overworld.setBlockAndUpdate(BOX_CONTENTS_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_CONTENTS_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        check(ContractContent.objectiveItems().contains(Items.CAKE),
                "Sanity: cake must be reachable as a contract objective");
        check(!ContractContent.objectiveItems().contains(Items.DIRT),
                "Sanity: dirt must not be reachable as a contract objective");

        ItemStack nestedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);

        check(box.canPlaceItem(0, new ItemStack(Items.CAKE)),
                "The box must still accept an item a contract can ask for");
        check(!box.canPlaceItem(0, new ItemStack(Items.DIRT)),
                "The box must refuse an item no contract could ever ask for");
        check(!box.canPlaceItem(0, nestedBox),
                "The box must refuse another Contract Box");

        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        ContractBoxMenu menu = new ContractBoxMenu(1, player.getInventory(), box);

        check(menu.getSlot(0).mayPlace(new ItemStack(Items.CAKE)),
                "The box GUI must still accept an item a contract can ask for");
        check(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)),
                "The box GUI must refuse an item no contract could ever ask for");
        check(!menu.getSlot(0).mayPlace(nestedBox),
                "The box GUI must refuse another Contract Box");

        player.getInventory().items.set(9, new ItemStack(Items.DIRT, 8));
        menu.quickMoveStack(player, 5);
        check(box.getItem(0).isEmpty() && player.getInventory().items.get(9).getCount() == 8,
                "Shift-clicking a refused item must leave it in the player's inventory (box slot 0 empty="
                        + box.getItem(0).isEmpty() + ", inventory count=" + player.getInventory().items.get(9).getCount() + ")");
        player.getInventory().clearContent();

        overworld.setBlockAndUpdate(BOX_CONTENTS_TEST_POS, boxState.setValue(ContractBoxBlock.SEALED, true));
        ContractBoxBlockEntity sealedBox = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_CONTENTS_TEST_POS);
        check(sealedBox != null && !sealedBox.canPlaceItem(0, new ItemStack(Items.CAKE)),
                "A sealed box must refuse everything, including otherwise-valid objective items");
    }

    private void testContractBoxCannotBeStoredInOtherContainers(ServerLevel overworld) {
        ItemStack boxItem = filledBox();

        check(!boxItem.getItem().canFitInsideContainerItems(),
                "A Contract Box must refuse to fit inside container items");

        SimpleContainer destination = new SimpleContainer(27);
        ItemStack leftover = HopperBlockEntity.addItem(null, destination, boxItem.copy(), null);
        check(leftover.getCount() == 1 && destination.isEmpty(),
                "A hopper must not push a Contract Box into a container (leftover=" + leftover.getCount()
                        + ", destination empty=" + destination.isEmpty() + ")");

        check(!new Slot(destination, 0, 0, 0).mayPlace(boxItem),
                "A plain container slot must refuse a Contract Box");
        check(new Slot(destination, 0, 0, 0).mayPlace(new ItemStack(Items.DIRT)),
                "Sanity: the same plain container slot must still accept ordinary items");

        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        check(new Slot(player.getInventory(), 0, 0, 0).mayPlace(boxItem),
                "The player's own inventory must still accept a Contract Box");

        ItemStack emptyBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
        check(new Slot(destination, 0, 0, 0).mayPlace(emptyBox),
                "An empty Contract Box must still go into a chest");
        check(HopperBlockEntity.addItem(null, new SimpleContainer(27), emptyBox.copy(), null).isEmpty(),
                "A hopper must still be able to put an empty Contract Box into a container");
        check(!ContractBoxItem.isFilledContractBox(sealedBoxItem()),
                "A sealed box must not count as filled");
    }

    private static ItemStack filledBox() {
        ItemStack stack = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.CAKE))));
        return stack;
    }

    private static ItemStack sealedBoxItem() {
        ItemStack stack = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
        stack.set(ContractDataComponents.CONTRACT_DATA, testContract(List.of(new GeneratedLine(new ItemStack(Items.CAKE), 1, 1.0)), Long.MAX_VALUE));
        return stack;
    }

    private void testContractBoxCannotBeStoredThroughItemStorage(ServerLevel overworld) {
        ItemStack boxItem = filledBox();

        BlockPos chestPos = CAPABILITY_TEST_POS;
        overworld.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        Storage<ItemVariant> chestStorage = ItemStorage.SIDED.find(overworld, chestPos, null);
        check(chestStorage != null, "Sanity: a chest must expose an item storage");
        if (chestStorage != null) {
            check(insertViaStorage(chestStorage, boxItem.copy()) == 0,
                    "Automation must not insert a Contract Box into a chest through the transfer API");
            check(insertViaStorage(chestStorage, new ItemStack(Items.DIRT)) == 1,
                    "Sanity: ordinary items must still insert into the chest");
        }

        BlockPos furnacePos = CAPABILITY_TEST_POS.above();
        overworld.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
        Storage<ItemVariant> furnaceStorage = ItemStorage.SIDED.find(overworld, furnacePos, Direction.UP);
        check(furnaceStorage != null, "Sanity: a furnace must expose a sided item storage");
        if (furnaceStorage != null) {
            check(insertViaStorage(furnaceStorage, boxItem.copy()) == 0,
                    "Automation must not insert a Contract Box through a sided item storage");
        }
    }

    private void testContractBoxItemStorageMatchesTheHandFilledRules(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        BlockPos boxPos = CAPABILITY_TEST_POS.above().above();
        overworld.setBlockAndUpdate(boxPos, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(boxPos);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        box.clearContent();

        Storage<ItemVariant> storage = ItemStorage.SIDED.find(overworld, boxPos, null);
        check(storage != null,
                "The Contract Box must expose an item storage");
        if (storage == null) {
            return;
        }

        check(insertViaStorage(storage, new ItemStack(Items.DIRT, 4)) == 0,
                "Automation must not put an item into the box that no contract could ever ask for");
        check(insertViaStorage(storage, new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM)) == 0,
                "Automation must not put a Contract Box into a Contract Box");

        check(insertViaStorage(storage, new ItemStack(Items.CAKE, 8)) == 8,
                "Automation must fill the box with an item a contract can ask for");

        int filledSlots = 0;
        int totalCakes = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.getItem(i);
            if (!stack.isEmpty()) {
                filledSlots++;
                totalCakes += stack.getCount();
            }
        }
        check(totalCakes == 8, "All 8 automated cakes must end up in the box (found " + totalCakes + ")");
        check(filledSlots == 1,
                "Automation must stack non-stackable objectives into a single box slot (used " + filledSlots + " slots)");

        overworld.setBlockAndUpdate(boxPos, boxState.setValue(ContractBoxBlock.SEALED, true));
        Storage<ItemVariant> sealedStorage = ItemStorage.SIDED.find(overworld, boxPos, null);
        check(sealedStorage != null && insertViaStorage(sealedStorage, new ItemStack(Items.CAKE)) == 0,
                "Automation must not be able to insert into a sealed box");
        check(sealedStorage != null && extractViaStorage(sealedStorage, ItemVariant.of(Items.CAKE), 64) == 0,
                "Automation must not be able to extract from a sealed box");
    }

    private static long insertViaStorage(Storage<ItemVariant> storage, ItemStack stack) {
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static long extractViaStorage(Storage<ItemVariant> storage, ItemVariant variant, long amount) {
        try (Transaction transaction = Transaction.openOuter()) {
            long extracted = storage.extract(variant, amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    private void testHopperCanFillTheBoxWithinTheWhitelist(ServerLevel overworld) {
        BlockPos boxPos = HOPPER_FILL_TEST_POS;
        BlockPos hopperPos = boxPos.above();
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        overworld.setBlockAndUpdate(boxPos, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(boxPos);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        box.clearContent();

        pushOnce(overworld, hopperPos, new ItemStack(Items.CAKE, 3));
        check(countInBox(box, Items.CAKE) == 1,
                "A hopper must fill the box with an item a contract can ask for (moved "
                        + countInBox(box, Items.CAKE) + " of the expected 1 cake per tick)");

        pushOnce(overworld, hopperPos, new ItemStack(Items.CAKE, 3));
        pushOnce(overworld, hopperPos, new ItemStack(Items.CAKE, 3));
        int cakeSlots = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (!box.getItem(i).isEmpty()) {
                cakeSlots++;
            }
        }
        check(countInBox(box, Items.CAKE) == 3 && cakeSlots == 1,
                "A hopper must stack a non-stackable objective into a single box slot (got "
                        + countInBox(box, Items.CAKE) + " cakes across " + cakeSlots + " slots, expected 3 in 1)");
        box.clearContent();

        HopperBlockEntity dirtHopper = pushOnce(overworld, hopperPos, new ItemStack(Items.DIRT, 3));
        check(dirtHopper != null && dirtHopper.getItem(0).getCount() == 3 && countInBox(box, Items.DIRT) == 0,
                "A hopper must not put an item into the box that no contract could ever ask for");

        HopperBlockEntity boxHopper = pushOnce(overworld, hopperPos, new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM));
        check(boxHopper != null && boxHopper.getItem(0).getCount() == 1,
                "A hopper must not put a Contract Box into a Contract Box");

        overworld.setBlockAndUpdate(boxPos, boxState.setValue(ContractBoxBlock.SEALED, true));
        HopperBlockEntity sealedHopper = pushOnce(overworld, hopperPos, new ItemStack(Items.CAKE));
        check(sealedHopper != null && sealedHopper.getItem(0).getCount() == 1,
                "A hopper must not be able to top up a sealed box");
    }

    private static HopperBlockEntity pushOnce(ServerLevel level, BlockPos hopperPos, ItemStack contents) {
        level.setBlockAndUpdate(hopperPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(hopperPos, Blocks.HOPPER.defaultBlockState());
        if (!(level.getBlockEntity(hopperPos) instanceof HopperBlockEntity hopper)) {
            return null;
        }
        hopper.setItem(0, contents);
        HopperBlockEntity.pushItemsTick(level, hopperPos, level.getBlockState(hopperPos), hopper);
        return hopper;
    }

    private static int countInBox(Container boxContainer, Item item) {
        int count = 0;
        for (int i = 0; i < boxContainer.getContainerSize(); i++) {
            ItemStack stack = boxContainer.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void testBoxSealValidatesAndConsumesContents(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState()
                .setValue(ContractBoxBlock.FACING, Direction.EAST);
        overworld.setBlockAndUpdate(BOX_SEAL_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_SEAL_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        GeneratedContract contract = testContract(List.of(
                new GeneratedLine(new ItemStack(Items.WHEAT), 5, 5.0),
                new GeneratedLine(new ItemStack(Items.WHEAT), 3, 3.0)
        ), overworld.getGameTime() + 1_000_000L);
        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
        ticket.set(ContractDataComponents.CONTRACT_DATA, contract);

        ContractBoxBlock block = (ContractBoxBlock) FarmersContractsMod.CONTRACT_BOX;
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);

        box.setItem(0, new ItemStack(Items.WHEAT, 6));
        boolean underfunded = block.trySeal(overworld, player, BOX_SEAL_TEST_POS, ticket);
        check(!underfunded, "Box must not seal with only 6 of the combined 8 wheat needed");
        check(!overworld.getBlockState(BOX_SEAL_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "A failed seal attempt must leave the box unsealed");
        check(box.getItem(0).getCount() == 6, "A failed seal attempt must not consume the box's contents");
        check(ticket.getCount() == 1, "A failed seal attempt must not consume the ticket");

        box.setItem(1, new ItemStack(Items.WHEAT, 2));
        boolean sealed = block.trySeal(overworld, player, BOX_SEAL_TEST_POS, ticket);
        check(sealed, "Box must seal once it holds the full combined 8 wheat");
        check(overworld.getBlockState(BOX_SEAL_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "A successful seal must flip the SEALED blockstate to true");
        check(overworld.getBlockState(BOX_SEAL_TEST_POS).getValue(ContractBoxBlock.FACING) == Direction.EAST,
                "Sealing must preserve whichever way the box was originally facing");
        check(countMatching(box, Items.WHEAT) == 0, "A successful seal must consume the full combined amount");
        check(ticket.isEmpty() || ticket.getCount() == 0, "A successful seal must consume the ticket");
        check(box.sealedContract() != null && box.sealedContract().customerId().equals(contract.customerId()),
                "A successful seal must carry the contract's data onto the box block entity");
    }

    private void testBoxSealRejectsForeignItemsAndOverfill(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        overworld.setBlockAndUpdate(BOX_MISMATCH_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_MISMATCH_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 4, 4.0)),
                overworld.getGameTime() + 1_000_000L);
        ContractBoxBlock block = (ContractBoxBlock) FarmersContractsMod.CONTRACT_BOX;
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);

        box.setItem(0, new ItemStack(Items.WHEAT, 4));
        box.setItem(1, new ItemStack(Items.STICK, 1));
        ItemStack ticket1 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
        ticket1.set(ContractDataComponents.CONTRACT_DATA, contract);
        boolean withForeignItem = block.trySeal(overworld, player, BOX_MISMATCH_TEST_POS, ticket1);
        check(!withForeignItem, "Box must not seal while it holds an item that isn't part of the order");
        check(!overworld.getBlockState(BOX_MISMATCH_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "A foreign item must leave the box unsealed");
        check(box.getItem(1).getCount() == 1, "A rejected seal attempt must not touch the foreign item");
        check(ticket1.getCount() == 1, "A rejected seal attempt must not consume the ticket");

        box.setItem(1, ItemStack.EMPTY);
        box.setItem(0, new ItemStack(Items.WHEAT, 6));
        ItemStack ticket2 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
        ticket2.set(ContractDataComponents.CONTRACT_DATA, contract);
        boolean overfilled = block.trySeal(overworld, player, BOX_MISMATCH_TEST_POS, ticket2);
        check(!overfilled, "Box must not seal with 6 wheat when the order needs exactly 4");
        check(!overworld.getBlockState(BOX_MISMATCH_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "Overfilling must leave the box unsealed");

        box.setItem(0, new ItemStack(Items.WHEAT, 4));
        ItemStack ticket3 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
        ticket3.set(ContractDataComponents.CONTRACT_DATA, contract);
        boolean exact = block.trySeal(overworld, player, BOX_MISMATCH_TEST_POS, ticket3);
        check(exact, "Box must seal once it holds exactly the required 4 wheat and nothing else");
    }

    private void testBoxSealExpiredContractVoidsTicketWithoutSealing(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.defaultBlockState();
        overworld.setBlockAndUpdate(BOX_EXPIRED_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_EXPIRED_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        GeneratedContract expired = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 1, 1.0)),
                overworld.getGameTime() - 1L);
        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
        ticket.set(ContractDataComponents.CONTRACT_DATA, expired);

        ContractBoxBlock block = (ContractBoxBlock) FarmersContractsMod.CONTRACT_BOX;
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);

        boolean result = block.trySeal(overworld, player, BOX_EXPIRED_TEST_POS, ticket);
        check(result, "An expired ticket must be handled");
        check(ticket.isEmpty() || ticket.getCount() == 0, "An expired ticket must be consumed when voided");
        check(!overworld.getBlockState(BOX_EXPIRED_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "An expired ticket must never seal the box");
        check(box.sealedContract() == null, "An expired ticket must not attach contract data to the box");
    }

    private void testBoardDeliversSealedBoxAndGrantsReward(ServerLevel overworld) {
        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 8, 8.0)),
                overworld.getGameTime() + 1_000_000L);
        ItemStack sealedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
        sealedBox.set(ContractDataComponents.CONTRACT_DATA, contract);

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD;
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        long completedBefore = ContractProgress.get(overworld).completedContracts();

        boolean delivered = block.tryDeliverBox(overworld, player, sealedBox);
        check(delivered, "A sealed box with valid, unexpired contract data must be delivered successfully");
        check(countMatching(player, Items.EMERALD) == 1, "Delivering a sealed box must grant its reward (1 emerald)");
        check(sealedBox.isEmpty() || sealedBox.getCount() == 0, "Delivering a sealed box must consume the box item");
        check(ContractProgress.get(overworld).completedContracts() == completedBefore + 1,
                "Delivering a sealed box must count toward completed contracts");
    }

    private void testBoardDeliverExpiredBoxVoidsWithoutReward(ServerLevel overworld) {
        GeneratedContract expired = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 8, 8.0)),
                overworld.getGameTime() - 1L);
        ItemStack sealedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
        sealedBox.set(ContractDataComponents.CONTRACT_DATA, expired);

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD;
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        long completedBefore = ContractProgress.get(overworld).completedContracts();

        boolean result = block.tryDeliverBox(overworld, player, sealedBox);
        check(result, "An expired sealed box must be handled");
        check(countMatching(player, Items.EMERALD) == 0, "An expired sealed box must not grant its reward");
        check(sealedBox.isEmpty() || sealedBox.getCount() == 0, "An expired sealed box must still be consumed when voided");
        check(ContractProgress.get(overworld).completedContracts() == completedBefore,
                "An expired, voided box must not count toward completed contracts");
    }

    private void testDeliveryModeGatesTurnInPaths(ServerLevel overworld) {
        BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.defaultBlockState();
        overworld.setBlockAndUpdate(BOX_GATING_BOARD_POS, boardState);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(BOX_GATING_BOARD_POS), Direction.UP, BOX_GATING_BOARD_POS, false);
        ContractBoardBlock board = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD;
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);

        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 1, 1.0)),
                overworld.getGameTime() + 1_000_000L);

        Config.DeliveryMode originalMode = Config.deliveryMode();
        try {
            Config.setDeliveryModeForTest(Config.DeliveryMode.BOX_ONLY);

            ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
            ticket.set(ContractDataComponents.CONTRACT_DATA, contract);
            ItemInteractionResult ticketResult = board.useItemOn(
                    ticket, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(ticketResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "BOX_ONLY must refuse a ticket turned in directly (result was " + ticketResult + ")");

            ItemStack sealedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
            sealedBox.set(ContractDataComponents.CONTRACT_DATA, contract);
            ItemInteractionResult boxResult = board.useItemOn(
                    sealedBox, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(boxResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "BOX_ONLY must accept a sealed box turn-in (result was " + boxResult + ")");

            Config.setDeliveryModeForTest(Config.DeliveryMode.TICKET_ONLY);

            ItemStack sealedBox2 = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
            sealedBox2.set(ContractDataComponents.CONTRACT_DATA, contract);
            ItemInteractionResult boxResult2 = board.useItemOn(
                    sealedBox2, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(boxResult2 == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "TICKET_ONLY must refuse a sealed box turn-in (result was " + boxResult2 + ")");
            check(sealedBox2.getCount() == 1, "TICKET_ONLY refusing a box turn-in must not consume the box");

            ItemStack ticket2 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
            ticket2.set(ContractDataComponents.CONTRACT_DATA, contract);
            ItemInteractionResult ticketResult2 = board.useItemOn(
                    ticket2, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(ticketResult2 != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "TICKET_ONLY must accept a ticket turned in directly (result was " + ticketResult2 + ")");
        } finally {
            Config.setDeliveryModeForTest(originalMode);
        }

        overworld.setBlockAndUpdate(BOX_GATING_BOX_POS, FarmersContractsMod.CONTRACT_BOX.defaultBlockState());
        check(!overworld.getBlockState(BOX_GATING_BOX_POS).getValue(ContractBoxBlock.SEALED),
                "A freshly placed, empty Contract Box must not start sealed");
    }

    private void testContractRarityTierPointsMapping() {
        check(ContractRarity.COMMON.tierPoints() == 1, "COMMON contracts must award 1 tier point");
        check(ContractRarity.UNCOMMON.tierPoints() == 2, "UNCOMMON contracts must award 2 tier points");
        check(ContractRarity.RARE.tierPoints() == 3, "RARE contracts must award 3 tier points");
        check(ContractRarity.SPECIAL.tierPoints() == 4, "SPECIAL contracts must award 4 tier points");
    }

    private void testTicketTurnInAwardsProgressScoreboardAndEvent(ServerLevel overworld) {
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();

        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 1, 1.0)),
                overworld.getGameTime() + 1_000_000L);
        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET);
        ticket.set(ContractDataComponents.CONTRACT_DATA, contract);
        player.getInventory().add(new ItemStack(Items.WHEAT, 1));

        CompletionSideEffects before = CompletionSideEffects.capture(overworld, player, firedCompletions.size());

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD;
        boolean result = block.tryTurnIn(overworld, player, ticket);

        checkCompletionSideEffects(overworld, player, contract, result, before, "Ticket turn-in");
    }

    private void testBoxDeliveryAwardsProgressScoreboardAndEvent(ServerLevel overworld) {
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();

        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 1, 1.0)),
                overworld.getGameTime() + 1_000_000L);
        ItemStack sealedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM);
        sealedBox.set(ContractDataComponents.CONTRACT_DATA, contract);

        CompletionSideEffects before = CompletionSideEffects.capture(overworld, player, firedCompletions.size());

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD;
        boolean result = block.tryDeliverBox(overworld, player, sealedBox);

        checkCompletionSideEffects(overworld, player, contract, result, before, "Box delivery");
    }

    private record CompletionSideEffects(long completedContracts, int score, int firedCompletionsCount) {
        static CompletionSideEffects capture(ServerLevel overworld, FakePlayer player, int firedCompletionsCount) {
            Objective objective = overworld.getServer().getScoreboard().getObjective(ContractScoreboard.OBJECTIVE_NAME);
            int score = objective == null ? 0 : overworld.getServer().getScoreboard().getOrCreatePlayerScore(player, objective).get();
            return new CompletionSideEffects(ContractProgress.get(overworld).completedContracts(), score, firedCompletionsCount);
        }
    }

    private void checkCompletionSideEffects(
            ServerLevel overworld,
            FakePlayer player,
            GeneratedContract contract,
            boolean result,
            CompletionSideEffects before,
            String pathLabel
    ) {
        check(result, pathLabel + ": completion must succeed (test setup sanity check)");

        check(ContractProgress.get(overworld).completedContracts() == before.completedContracts() + 1,
                pathLabel + ": completion must increment ContractProgress by exactly 1");

        Scoreboard scoreboard = overworld.getServer().getScoreboard();
        Objective objectiveAfter = scoreboard.getObjective(ContractScoreboard.OBJECTIVE_NAME);
        check(objectiveAfter != null, pathLabel + ": completion must create the fc_points scoreboard objective if it doesn't already exist");
        if (objectiveAfter != null) {
            int scoreAfter = scoreboard.getOrCreatePlayerScore(player, objectiveAfter).get();
            check(scoreAfter == before.score() + ContractRarity.COMMON.tierPoints(),
                    pathLabel + ": a completed COMMON contract must award exactly " + ContractRarity.COMMON.tierPoints()
                            + " fc_points (before=" + before.score() + ", after=" + scoreAfter + ")");
        }

        int firedSinceBefore = firedCompletions.size() - before.firedCompletionsCount();
        check(firedSinceBefore == 1, pathLabel + ": completion must fire ContractFulfilledCallback exactly once (fired " + firedSinceBefore + " times)");
        if (firedSinceBefore == 1) {
            FiredCompletion event = firedCompletions.get(firedCompletions.size() - 1);
            check(event.player() == player, pathLabel + ": ContractFulfilledCallback must carry the completing player");
            check(event.contract().equals(contract), pathLabel + ": ContractFulfilledCallback must carry the completed contract");
            check(event.contract().rarity() == ContractRarity.COMMON, pathLabel + ": ContractFulfilledCallback must report the contract's rarity");
            check(event.contract().rarity().tierPoints() == ContractRarity.COMMON.tierPoints(),
                    pathLabel + ": ContractFulfilledCallback must report the rarity's tier points");
        }
    }

    private static GeneratedContract testContract(List<GeneratedLine> objectives, long expiresAtGameTime) {
        return new GeneratedContract(
                ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, "selftest_customer"),
                "SelfTest Customer",
                ContractRarity.COMMON,
                objectives,
                new RewardBundle(List.of(new GeneratedLine(new ItemStack(Items.EMERALD), 1, 1.0)), 10),
                expiresAtGameTime
        );
    }

    private static int countMatching(Container container, Item item) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countFilled(ContractBoardBlockEntity entity) {
        int filled = 0;
        for (int i = 0; i < entity.container().getContainerSize(); i++) {
            if (!entity.container().getItem(i).isEmpty()) {
                filled++;
            }
        }
        return filled;
    }

    private static int countMatching(FakePlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
