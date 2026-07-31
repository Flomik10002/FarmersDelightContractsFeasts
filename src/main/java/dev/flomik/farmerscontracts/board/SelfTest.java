package dev.flomik.farmerscontracts.board;

import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.Config;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.box.ContractBoxBlock;
import dev.flomik.farmerscontracts.box.ContractBoxBlockEntity;
import dev.flomik.farmerscontracts.box.ContractBoxMenu;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Headless, server-only regression checks for the board GUI/turn-in bugs fixed in this session.
 * Gated behind -Dfarmerscontracts.selftest=true (see the {@code runSelfTest} Gradle task),
 * mirroring how {@link dev.flomik.farmerscontracts.contract.BalanceCheck} is wired up - never
 * runs for normal players.
 */
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

    private final List<String> failures = new ArrayList<>();
    private int checks = 0;

    private SelfTest() {
    }

    public static boolean isRequested() {
        return Boolean.getBoolean("farmerscontracts.selftest");
    }

    public static boolean run(MinecraftServer server) {
        SelfTest test = new SelfTest();
        ServerLevel overworld = server.overworld();

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
        test.testBoxSealValidatesAndConsumesContents(overworld);
        test.testBoxSealRejectsForeignItemsAndOverfill(overworld);
        test.testBoxSealExpiredContractVoidsTicketWithoutSealing(overworld);
        test.testBoardDeliversSealedBoxAndGrantsReward(overworld);
        test.testBoardDeliverExpiredBoxVoidsWithoutReward(overworld);
        test.testDeliveryModeGatesTurnInPaths(overworld);

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

    // --- Bug 1: MaskedBoardContainer.getItem() must return a copy, not the live board stack ---
    private void testMaskedBoardContainerDoesNotAliasLiveStack() {
        SimpleContainer real = new SimpleContainer(1);
        real.setItem(0, new ItemStack(Items.EMERALD, 5));

        MaskedBoardContainer masked = new MaskedBoardContainer(real, new HashSet<>(), () -> {
        });

        ItemStack viewed = masked.getItem(0);
        viewed.shrink(5);

        check(real.getItem(0).getCount() == 5,
                "MaskedBoardContainer.getItem() must not alias the real container's stack (mutating the returned stack must not affect the shared board)");
    }

    // --- Bug 2: /time set and /time add must not trigger a board refresh. Refresh timing is
    // ported from Bountiful (ContractBoardBlockEntity.tick) and is entirely getGameTime()-based
    // (the world's monotonic tick counter) - /time set and /time add only ever call
    // level.setDayTime(), which getGameTime() is structurally immune to. ---
    private void testTimeSetImmunity(ServerLevel overworld) {
        BlockState state = FarmersContractsMod.CONTRACT_BOARD.get().defaultBlockState();
        ContractBoardBlockEntity entity = new ContractBoardBlockEntity(TICK_TEST_POS, state);

        // Initial population (pristine board) - not the behavior under test, just gets the board
        // into a known non-empty state so a spurious extra refresh would be observable.
        ContractBoardBlockEntity.tick(overworld, TICK_TEST_POS, state, entity);
        int filledBefore = countFilled(entity);

        // Simulate what TimeCommand.setTime()/addTime() actually do: mutate getDayTime() directly,
        // with zero real ticks elapsed (no getGameTime() change).
        overworld.setDayTime(overworld.getDayTime() + 5L * 24000L);
        ContractBoardBlockEntity.tick(overworld, TICK_TEST_POS, state, entity);

        int filledAfter = countFilled(entity);
        check(filledAfter == filledBefore,
                "/time set jump (getDayTime() only) must not trigger a board refresh (filled slots: " + filledBefore + " -> " + filledAfter + ")");
    }

    // --- New: a board whose chunk was unloaded for a long time (or one restored after
    // break+replace with a stale lastUpdateGameTime) must catch up and end up with offers again
    // on its next tick, rather than staying empty or requiring dozens of individual ticks -
    // ported from Bountiful's numUpdates catch-up logic in upkeepBountyGeneration(). ---
    private void testBoardRefreshCatchesUpAfterLongAbsence(ServerLevel overworld) {
        BlockPos pos = TICK_TEST_POS.above();
        BlockState state = FarmersContractsMod.CONTRACT_BOARD.get().defaultBlockState();
        ContractBoardBlockEntity entity = new ContractBoardBlockEntity(pos, state);

        // First tick performs initial population and sets lastUpdateGameTime to "now" - force it
        // far into the past afterward to simulate a chunk that stayed unloaded for a very long time.
        ContractBoardBlockEntity.tick(overworld, pos, state, entity);
        long updateFrequencyTicks = Config.boardUpdateFrequencySeconds() * 20L;
        long veryStale = overworld.getGameTime() - updateFrequencyTicks * (ContractBoardBlockEntity.SLOTS + 10L);
        entity.forceLastUpdateGameTimeForTest(veryStale);
        for (int i = 0; i < ContractBoardBlockEntity.SLOTS; i++) {
            entity.container().setItem(i, ItemStack.EMPTY);
        }
        check(countFilled(entity) == 0, "Sanity check: board must actually be empty before the catch-up tick");

        ContractBoardBlockEntity.tick(overworld, pos, state, entity);

        int filled = countFilled(entity);
        check(filled > 0, "A board that catches up after a long absence must end up with offers again on its very next tick (found " + filled + ")");
        check(filled <= ContractBoardBlockEntity.SLOTS,
                "Catch-up refresh must never exceed the board's own slot count (found " + filled + ")");
    }

    // --- New: Config.boardGlobalState() (ported from Bountiful's board.globalBoardState) must
    // make every Contract Board on the server share the exact same offers/timers instead of each
    // keeping its own independent state - see ContractBoardBlockEntity.activeState/GlobalBoardData. ---
    private void testBoardGlobalStateSharesContentsAcrossBoards(ServerLevel overworld) {
        Config.setBoardGlobalStateForTest(true);
        try {
            BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.get().defaultBlockState();
            overworld.setBlockAndUpdate(BOARD_GLOBAL_TEST_POS_A, boardState);
            overworld.setBlockAndUpdate(BOARD_GLOBAL_TEST_POS_B, boardState);
            ContractBoardBlockEntity entityA = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_GLOBAL_TEST_POS_A);
            ContractBoardBlockEntity entityB = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_GLOBAL_TEST_POS_B);
            check(entityA != null && entityB != null, "Both boards must exist right after placing them");
            if (entityA == null || entityB == null) {
                return;
            }

            // Re-affirmed before each check: ModConfigSpec is backed by a live-reloading TOML
            // file, and an unrelated async reload (triggered by earlier tests toggling other keys
            // in this same config, e.g. setDeliveryModeForTest) can occasionally race in and
            // clobber our in-memory override back to the file's default (false) between steps.
            Config.setBoardGlobalStateForTest(true);
            ContractBoardBlockEntity.tick(overworld, BOARD_GLOBAL_TEST_POS_A, boardState, entityA);
            int filledA = countFilled(entityA);
            check(filledA > 0, "Board A must be populated after its own first tick (global mode) - test is meaningless otherwise");

            Config.setBoardGlobalStateForTest(true);
            int filledB = countFilled(entityB);
            check(filledB == filledA,
                    "Board B must immediately show the same offers as board A in global mode, without ever ticking itself (board A: "
                            + filledA + ", board B: " + filledB + ")");

            // Write through board A's accessor and confirm it's visible through board B's too -
            // proves it's the literal same shared container, not a coincidental matching count.
            Config.setBoardGlobalStateForTest(true);
            entityA.container().setItem(0, new ItemStack(Items.EMERALD));
            check(entityB.container().getItem(0).is(Items.EMERALD),
                    "Writing into board A's container in global mode must be visible through board B's accessor too (shared state)");
        } finally {
            Config.setBoardGlobalStateForTest(false);
        }
    }

    // --- Bug 3: two objective lines on the same item must require their combined amount ---
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
        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
        ticket.set(ContractDataComponents.CONTRACT_DATA.get(), contract);

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD.get();

        // Under-fund case: 6 wheat on hand, contract needs 5 + 3 = 8 combined.
        player.getInventory().add(new ItemStack(Items.WHEAT, 6));
        boolean underfundedResult = block.tryTurnIn(overworld, player, ticket);
        check(!underfundedResult,
                "Contract with duplicate-item objective lines (5 wheat + 3 wheat) must NOT be turned in with only 6 wheat");
        check(countMatching(player, Items.WHEAT) == 6,
                "A failed turn-in must not partially consume the player's items (still had 6 wheat)");
        check(ticket.getCount() == 1,
                "A failed turn-in must not consume the ticket");

        // Fully-funded case: top up to 8, should now succeed and consume exactly 8.
        player.getInventory().add(new ItemStack(Items.WHEAT, 2));
        boolean fundedResult = block.tryTurnIn(overworld, player, ticket);
        check(fundedResult,
                "Contract must be turned in successfully once the player has the full combined 8 wheat");
        check(countMatching(player, Items.WHEAT) == 0,
                "A successful turn-in must consume the full combined amount (8 wheat)");
        check(countMatching(player, Items.EMERALD) == 1,
                "A successful turn-in must grant the reward (1 emerald)");
        check(ticket.isEmpty() || ticket.getCount() == 0,
                "A successful turn-in must consume the ticket");
    }

    // --- Bug 4: the menu must close when the player walks away or the block is destroyed ---
    private void testStillValidClosesOnDistanceAndBlockRemoval(ServerLevel overworld) {
        BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.get().defaultBlockState();
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
        check(!menu.stillValid(player), "Menu must become invalid once the board block is destroyed, even with the player still standing there");
    }

    // --- Bug 5: breaking the board and placing a new one at the same position must not come back
    // empty, and must not reset the Bountiful-ported refresh timer either (which would look like
    // an instant-reroll exploit - break+replace to force a fresh set of offers). Both the
    // container contents (DataComponents.CONTAINER) and the refresh bookkeeping
    // (ContractDataComponents.BOARD_STATE - lastUpdateGameTime/initialized/slotTimestamps) ride
    // on the dropped item and get restored on placement.
    private void testBoardPreservesOffersAcrossBreakAndReplace(ServerLevel overworld) {
        BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.get().defaultBlockState();
        overworld.setBlockAndUpdate(BOARD_PRESERVE_TEST_POS, boardState);
        ContractBoardBlockEntity entity = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_PRESERVE_TEST_POS);
        check(entity != null, "Board block entity must exist right after placing the block");
        if (entity == null) {
            return;
        }

        ContractBoardBlockEntity.tick(overworld, BOARD_PRESERVE_TEST_POS, boardState, entity);
        int filledBefore = countFilled(entity);
        check(filledBefore > 0, "Board must actually contain offers after its initial fill (test is meaningless otherwise)");

        ItemStack drop = new ItemStack(FarmersContractsMod.CONTRACT_BOARD.get());
        drop.applyComponents(entity.collectComponents());

        overworld.setBlockAndUpdate(BOARD_PRESERVE_TEST_POS, Blocks.AIR.defaultBlockState());
        overworld.setBlockAndUpdate(BOARD_PRESERVE_TEST_POS, boardState);
        ContractBoardBlockEntity freshEntity = (ContractBoardBlockEntity) overworld.getBlockEntity(BOARD_PRESERVE_TEST_POS);
        check(freshEntity != null, "A freshly placed board block entity must exist");
        if (freshEntity == null) {
            return;
        }
        check(countFilled(freshEntity) == 0, "Sanity check: a brand new block entity must start with an empty container");

        freshEntity.applyComponentsFromItemStack(drop);
        check(countFilled(freshEntity) == filledBefore,
                "A board's offers must survive being carried on the dropped item and restored on placement (filled slots: "
                        + filledBefore + " -> " + countFilled(freshEntity) + ")");

        ContractBoardBlockEntity.tick(overworld, BOARD_PRESERVE_TEST_POS, boardState, freshEntity);
        check(countFilled(freshEntity) == filledBefore,
                "Ticking the restored board again immediately must not wipe/corrupt the offers or re-run initial population (proves initialized/lastUpdateGameTime were restored too)");
    }

    // --- Contract Box: a slot can hold more of a non-stackable item than that item's own limit,
    // both by direct placement and by repeated shift-click (see ContractBoxBlockEntity's and
    // ContractBoxMenu's getMaxStackSize overrides / custom quickMoveStack merge logic) ---
    private void testBoxAcceptsNonStackableItemsBeyondTheirNormalMax(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.get().defaultBlockState();
        overworld.setBlockAndUpdate(BOX_NONSTACKABLE_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_NONSTACKABLE_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }
        check(new ItemStack(Items.CAKE).getMaxStackSize() == 1,
                "Sanity check: cake must actually be non-stackable in this environment, or this test proves nothing");

        // Direct container-level placement (mirrors what Slot#set/Container#setItem's
        // limitSize(getMaxStackSize(stack)) call would otherwise truncate to 1).
        box.setItem(0, new ItemStack(Items.CAKE, 5));
        check(box.getItem(0).getCount() == 5,
                "A box slot must hold more than 1 non-stackable item when set directly (got " + box.getItem(0).getCount() + ")");
        box.setItem(0, ItemStack.EMPTY);

        // Shift-click simulation: 5 separate cake stacks (as they'd naturally sit in a player's
        // inventory, one per slot, since cake can't stack there either) shift-clicked one at a
        // time into the box must all merge into a single box slot, not consume 5 box slots.
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
        check(filledBoxSlots == 1, "5 shift-clicked non-stackable items must merge into a single box slot, not one each (used " + filledBoxSlots + " slots)");
    }

    // --- Contract Box: manually picking up a stacked non-stackable item from one box slot and
    // clicking it onto another already-occupied box slot must merge the counts, not overwrite
    // them (reported symptom: "moving box slot -> box slot loses count, but placing from
    // inventory or taking out is fine"). This goes through AbstractContainerMenu#clicked
    // (ClickType.PICKUP), a completely different code path from quickMoveStack/shift-click.
    private void testBoxSlotToSlotDragMergesNonStackableItems(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.get().defaultBlockState();
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
        check(menu.getCarried().isEmpty(), "Clicking the carried cakes onto a matching slot must fully merge, leaving nothing on the cursor (got " + menu.getCarried().getCount() + ")");
        check(box.getItem(1).getCount() == 5,
                "Dragging 3 cakes from slot 0 onto slot 1's existing 2 cakes must merge into 5, not overwrite/lose count (got " + box.getItem(1).getCount() + ")");
    }

    // --- Contract Box: the CLIENT's own predicted click merge must not silently truncate a
    // non-stackable item's count back to 1. Opening a menu client-side never wraps the real
    // ContractBoxBlockEntity (server-only) - the client always builds a bare fallback container
    // via ContractBoxMenu's no-arg constructor to run its own local prediction of the click before
    // the server's authoritative correction arrives (MultiPlayerGameMode#handleInventoryMouseClick
    // calls menu.clicked(...) locally). Root cause of the reported "count vanishes on a direct
    // click move" bug: BoxSlot#getMaxStackSize(ItemStack) already forces 64 for the grow
    // arithmetic in Slot#safeInsert, but Slot#set()/setByPlayer() finishes by calling
    // container.setItem(slot, stack), and vanilla's Container#setItem calls
    // stack.limitSize(this.getMaxStackSize(stack)) - the CONTAINER's own getMaxStackSize(ItemStack),
    // which defaults to Math.min(64, stack.getMaxStackSize()) = 1 for Cake on a plain
    // SimpleContainer. This test exercises exactly the constructor the client actually uses (not
    // one wired to a real box block entity) to prove the fix (PredictionSizedContainer) keeps
    // client-side prediction in sync with the server instead of momentarily truncating it. ---
    private void testBoxClientSidePredictionDoesNotTruncateMergedCount(ServerLevel overworld) {
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        // The exact constructor the CLIENT uses when opening the box's GUI - no real block entity
        // behind it, just a bare fallback container for local click prediction.
        ContractBoxMenu clientSideMenu = new ContractBoxMenu(1, player.getInventory());

        // Set up directly on the underlying container (bypassing Slot#set/container.setItem's own
        // truncation path, exactly like the other tests use box.setItem(...) for setup) so the
        // only thing under test is what clicked() itself does with an existing >1 stack.
        clientSideMenu.getSlot(0).container.setItem(0, new ItemStack(Items.CAKE, 3));
        clientSideMenu.getSlot(1).container.setItem(1, new ItemStack(Items.CAKE, 2));

        clientSideMenu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        clientSideMenu.clicked(1, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);

        check(clientSideMenu.getSlot(1).getItem().getCount() == 5,
                "The client's own local click prediction (bare fallback container, no real block entity) must merge non-stackable counts correctly, not truncate them back to 1 (got "
                        + clientSideMenu.getSlot(1).getItem().getCount() + ")");
    }

    // --- Contract Box: diagnostic for the vanilla mouse-drag-paint gesture (holding the button
    // down and sweeping across several box slots), which is a completely different code path
    // (ClickType.QUICK_CRAFT) from a plain click-then-click move. Vanilla's own eligibility check
    // (AbstractContainerMenu#canItemQuickReplace) hardcodes the ITEM's own getMaxStackSize(), not
    // the slot's override, when deciding whether a slot may join the drag - this must never be
    // allowed to overwrite/destroy an already-stacked non-stackable item's count, even though it
    // can legitimately refuse to add such a slot to the drag at all (a silent no-op is acceptable,
    // data loss is not).
    private void testBoxMouseDragAcrossSlotsNeverLosesCount(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.get().defaultBlockState();
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

        // Pick up slot 0's 3 cakes onto the cursor first (a real drag always starts this way).
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().getCount() == 3, "Setup: picking up slot 0 must carry 3 cakes");

        // Now paint-drag across slot 1 (already has 2) and slot 2 (empty) in one continuous hold:
        // header=0 start, header=1 add-slot (x2), header=2 release. Type 0 = even/normal split.
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

    // --- Contract Box: sealing validates the box's own contents (not the player's inventory) ---
    private void testBoxSealValidatesAndConsumesContents(ServerLevel overworld) {
        // Facing set to something other than the default (NORTH) on purpose - sealing must only
        // ever flip SEALED, never reset the block back to whichever way it happened to be placed.
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.get().defaultBlockState()
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
        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
        ticket.set(ContractDataComponents.CONTRACT_DATA.get(), contract);

        ContractBoxBlock block = (ContractBoxBlock) FarmersContractsMod.CONTRACT_BOX.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);

        // Under-funded: 6 wheat in the box, contract needs 5 + 3 = 8 combined.
        box.setItem(0, new ItemStack(Items.WHEAT, 6));
        boolean underfunded = block.trySeal(overworld, player, BOX_SEAL_TEST_POS, ticket);
        check(!underfunded, "Box must not seal with only 6 of the combined 8 wheat needed");
        check(!overworld.getBlockState(BOX_SEAL_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "A failed seal attempt must leave the box unsealed");
        check(box.getItem(0).getCount() == 6, "A failed seal attempt must not consume the box's contents");
        check(ticket.getCount() == 1, "A failed seal attempt must not consume the ticket");

        // Fully-funded: top up to 8, should now seal and consume exactly 8.
        box.setItem(1, new ItemStack(Items.WHEAT, 2));
        boolean sealed = block.trySeal(overworld, player, BOX_SEAL_TEST_POS, ticket);
        check(sealed, "Box must seal once it holds the full combined 8 wheat");
        check(overworld.getBlockState(BOX_SEAL_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "A successful seal must flip the SEALED blockstate to true");
        check(overworld.getBlockState(BOX_SEAL_TEST_POS).getValue(ContractBoxBlock.FACING) == Direction.EAST,
                "Sealing must preserve whichever way the box was originally facing (EAST), not reset it");
        check(countMatching(box, Items.WHEAT) == 0, "A successful seal must consume the full combined amount (8 wheat)");
        check(ticket.isEmpty() || ticket.getCount() == 0, "A successful seal must consume the ticket (it merges into the box)");
        check(box.sealedContract() != null && box.sealedContract().customerId().equals(contract.customerId()),
                "A successful seal must carry the contract's data onto the box block entity");
    }

    // --- Contract Box: the ticket verifies the box holds ONLY the order, nothing extra ---
    private void testBoxSealRejectsForeignItemsAndOverfill(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.get().defaultBlockState();
        overworld.setBlockAndUpdate(BOX_MISMATCH_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_MISMATCH_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 4, 4.0)),
                overworld.getGameTime() + 1_000_000L);
        ContractBoxBlock block = (ContractBoxBlock) FarmersContractsMod.CONTRACT_BOX.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);

        // Foreign item present alongside the exact right amount of wheat - must still be rejected.
        box.setItem(0, new ItemStack(Items.WHEAT, 4));
        box.setItem(1, new ItemStack(Items.STICK, 1));
        ItemStack ticket1 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
        ticket1.set(ContractDataComponents.CONTRACT_DATA.get(), contract);
        boolean withForeignItem = block.trySeal(overworld, player, BOX_MISMATCH_TEST_POS, ticket1);
        check(!withForeignItem, "Box must not seal while it holds an item that isn't part of the order (a stick)");
        check(!overworld.getBlockState(BOX_MISMATCH_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "A foreign item must leave the box unsealed");
        check(box.getItem(1).getCount() == 1, "A rejected seal attempt must not touch the foreign item");
        check(ticket1.getCount() == 1, "A rejected seal attempt must not consume the ticket");

        // Remove the foreign item but overfill wheat past the required amount - still rejected,
        // since the ticket certifies the box holds exactly the order, not "at least" the order.
        box.setItem(1, ItemStack.EMPTY);
        box.setItem(0, new ItemStack(Items.WHEAT, 6));
        ItemStack ticket2 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
        ticket2.set(ContractDataComponents.CONTRACT_DATA.get(), contract);
        boolean overfilled = block.trySeal(overworld, player, BOX_MISMATCH_TEST_POS, ticket2);
        check(!overfilled, "Box must not seal with 6 wheat when the order needs exactly 4");
        check(!overworld.getBlockState(BOX_MISMATCH_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "Overfilling must leave the box unsealed");

        // Exactly right - now it seals.
        box.setItem(0, new ItemStack(Items.WHEAT, 4));
        ItemStack ticket3 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
        ticket3.set(ContractDataComponents.CONTRACT_DATA.get(), contract);
        boolean exact = block.trySeal(overworld, player, BOX_MISMATCH_TEST_POS, ticket3);
        check(exact, "Box must seal once it holds exactly the required 4 wheat and nothing else");
    }

    // --- Contract Box: an expired ticket voids itself without sealing (mirrors the board's ticket path) ---
    private void testBoxSealExpiredContractVoidsTicketWithoutSealing(ServerLevel overworld) {
        BlockState boxState = FarmersContractsMod.CONTRACT_BOX.get().defaultBlockState();
        overworld.setBlockAndUpdate(BOX_EXPIRED_TEST_POS, boxState);
        ContractBoxBlockEntity box = (ContractBoxBlockEntity) overworld.getBlockEntity(BOX_EXPIRED_TEST_POS);
        check(box != null, "Box block entity must exist right after placing the block");
        if (box == null) {
            return;
        }

        GeneratedContract expired = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 1, 1.0)),
                overworld.getGameTime() - 1L);
        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
        ticket.set(ContractDataComponents.CONTRACT_DATA.get(), expired);

        ContractBoxBlock block = (ContractBoxBlock) FarmersContractsMod.CONTRACT_BOX.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);

        boolean result = block.trySeal(overworld, player, BOX_EXPIRED_TEST_POS, ticket);
        check(result, "An expired ticket must be handled (voided), not silently ignored");
        check(ticket.isEmpty() || ticket.getCount() == 0, "An expired ticket must be consumed when voided");
        check(!overworld.getBlockState(BOX_EXPIRED_TEST_POS).getValue(ContractBoxBlock.SEALED),
                "An expired ticket must never seal the box");
        check(box.sealedContract() == null, "An expired ticket must not attach contract data to the box");
    }

    // --- Contract Box: delivering a sealed box at the board grants the reward without re-checking inventory ---
    private void testBoardDeliversSealedBoxAndGrantsReward(ServerLevel overworld) {
        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 8, 8.0)),
                overworld.getGameTime() + 1_000_000L);
        ItemStack sealedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM.get());
        sealedBox.set(ContractDataComponents.CONTRACT_DATA.get(), contract);

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        long completedBefore = ContractProgress.get(overworld).completedContracts();

        boolean delivered = block.tryDeliverBox(overworld, player, sealedBox);
        check(delivered, "A sealed box with valid, unexpired contract data must be delivered successfully");
        check(countMatching(player, Items.EMERALD) == 1, "Delivering a sealed box must grant its reward (1 emerald)");
        check(sealedBox.isEmpty() || sealedBox.getCount() == 0, "Delivering a sealed box must consume the box item");
        check(ContractProgress.get(overworld).completedContracts() == completedBefore + 1,
                "Delivering a sealed box must count toward completed contracts, same as a ticket turn-in");
    }

    // --- Contract Box: a sealed box for an expired contract voids without granting a reward ---
    private void testBoardDeliverExpiredBoxVoidsWithoutReward(ServerLevel overworld) {
        GeneratedContract expired = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 8, 8.0)),
                overworld.getGameTime() - 1L);
        ItemStack sealedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM.get());
        sealedBox.set(ContractDataComponents.CONTRACT_DATA.get(), expired);

        ContractBoardBlock block = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        long completedBefore = ContractProgress.get(overworld).completedContracts();

        boolean result = block.tryDeliverBox(overworld, player, sealedBox);
        check(result, "An expired sealed box must be handled (voided), not silently ignored");
        check(countMatching(player, Items.EMERALD) == 0, "An expired sealed box must not grant its reward");
        check(sealedBox.isEmpty() || sealedBox.getCount() == 0, "An expired sealed box must still be consumed when voided");
        check(ContractProgress.get(overworld).completedContracts() == completedBefore,
                "An expired, voided box must not count toward completed contracts");
    }

    // --- Config.DeliveryMode gates which turn-in path the board accepts ---
    private void testDeliveryModeGatesTurnInPaths(ServerLevel overworld) {
        BlockState boardState = FarmersContractsMod.CONTRACT_BOARD.get().defaultBlockState();
        overworld.setBlockAndUpdate(BOX_GATING_BOARD_POS, boardState);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(BOX_GATING_BOARD_POS), Direction.UP, BOX_GATING_BOARD_POS, false);
        ContractBoardBlock board = (ContractBoardBlock) FarmersContractsMod.CONTRACT_BOARD.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);

        GeneratedContract contract = testContract(
                List.of(new GeneratedLine(new ItemStack(Items.WHEAT), 1, 1.0)),
                overworld.getGameTime() + 1_000_000L);

        Config.DeliveryMode originalMode = Config.deliveryMode();
        try {
            Config.setDeliveryModeForTest(Config.DeliveryMode.BOX_ONLY);

            ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
            ticket.set(ContractDataComponents.CONTRACT_DATA.get(), contract);
            ItemInteractionResult ticketResult = board.useItemOn(
                    ticket, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(ticketResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "BOX_ONLY must refuse a ticket turned in directly (result was " + ticketResult + ")");

            ItemStack sealedBox = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM.get());
            sealedBox.set(ContractDataComponents.CONTRACT_DATA.get(), contract);
            ItemInteractionResult boxResult = board.useItemOn(
                    sealedBox, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(boxResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "BOX_ONLY must accept a sealed box turn-in (result was " + boxResult + ")");

            Config.setDeliveryModeForTest(Config.DeliveryMode.TICKET_ONLY);

            ItemStack sealedBox2 = new ItemStack(FarmersContractsMod.CONTRACT_BOX_ITEM.get());
            sealedBox2.set(ContractDataComponents.CONTRACT_DATA.get(), contract);
            ItemInteractionResult boxResult2 = board.useItemOn(
                    sealedBox2, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(boxResult2 == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "TICKET_ONLY must refuse a sealed box turn-in (result was " + boxResult2 + ")");
            check(sealedBox2.getCount() == 1, "TICKET_ONLY refusing a box turn-in must not consume the box");

            ItemStack ticket2 = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
            ticket2.set(ContractDataComponents.CONTRACT_DATA.get(), contract);
            ItemInteractionResult ticketResult2 = board.useItemOn(
                    ticket2, boardState, overworld, BOX_GATING_BOARD_POS, player, InteractionHand.MAIN_HAND, hit);
            check(ticketResult2 != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "TICKET_ONLY must accept a ticket turned in directly (result was " + ticketResult2 + ")");
        } finally {
            Config.setDeliveryModeForTest(originalMode);
        }

        // Placing a fresh, unsealed box must never come out already sealed (regression guard for
        // getStateForPlacement reading the wrong component off an empty BlockItem).
        overworld.setBlockAndUpdate(BOX_GATING_BOX_POS, FarmersContractsMod.CONTRACT_BOX.get().defaultBlockState());
        check(!overworld.getBlockState(BOX_GATING_BOX_POS).getValue(ContractBoxBlock.SEALED),
                "A freshly placed, empty Contract Box must not start sealed");
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
