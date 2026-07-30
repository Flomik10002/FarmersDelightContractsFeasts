package dev.flomik.farmerscontracts.board;

import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.ContractProgress;
import dev.flomik.farmerscontracts.contract.ContractRarity;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.contract.GeneratedLine;
import dev.flomik.farmerscontracts.contract.RewardBundle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
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
        test.testSleepSkipStillCountsTowardDayChange(overworld);
        test.testDuplicateObjectiveConsumption(overworld);
        test.testStillValidClosesOnDistanceAndBlockRemoval(overworld);

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

    // --- Bug 2: /time set and /time add must not trigger a board refill ---
    private void testTimeSetImmunity(ServerLevel overworld) {
        BlockState state = FarmersContractsMod.CONTRACT_BOARD.get().defaultBlockState();
        ContractBoardBlockEntity entity = new ContractBoardBlockEntity(TICK_TEST_POS, state);

        // Initial fill (freshlyPlaced) - not the behavior under test, just gets the board into a
        // known non-empty state so a spurious second fill would be observable.
        ContractBoardBlockEntity.tick(overworld, TICK_TEST_POS, state, entity);
        int filledBefore = countFilled(entity);
        Long dayBefore = ContractProgress.get(overworld).boardLastRefillDay(TICK_TEST_POS);

        // Simulate what TimeCommand.setTime()/addTime() actually do: mutate getDayTime() directly,
        // with zero real ticks elapsed (no getGameTime() change).
        overworld.setDayTime(overworld.getDayTime() + 5L * 24000L);
        ContractBoardBlockEntity.tick(overworld, TICK_TEST_POS, state, entity);

        int filledAfter = countFilled(entity);
        Long dayAfter = ContractProgress.get(overworld).boardLastRefillDay(TICK_TEST_POS);

        check(filledAfter == filledBefore,
                "/time set jump (getDayTime() only) must not refill the board (filled slots: " + filledBefore + " -> " + filledAfter + ")");
        check(dayBefore.equals(dayAfter),
                "/time set jump must not advance the board's persisted last-refill day");
    }

    // --- Bug 2 (other half): sleeping through the night must still bank as a day advance ---
    private void testSleepSkipStillCountsTowardDayChange(ServerLevel overworld) {
        long offsetBefore = ContractProgress.get(overworld).sleepDayOffset(overworld.dimension());
        long dayTimeNow = overworld.getDayTime();
        long newTime = dayTimeNow + 24000L;

        NeoForge.EVENT_BUS.post(new SleepFinishedTimeEvent(overworld, newTime, 0L));

        long offsetAfter = ContractProgress.get(overworld).sleepDayOffset(overworld.dimension());
        check(offsetAfter - offsetBefore == 24000L,
                "SleepFinishedTimeEvent must bank exactly the skipped ticks into sleepDayOffset (delta was " + (offsetAfter - offsetBefore) + ", expected 24000)");
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
