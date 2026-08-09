package dev.flomik.farmerscontracts.board;

import dev.flomik.farmerscontracts.Config;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractContent;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.ContractGenerator;
import dev.flomik.farmerscontracts.contract.Customer;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.villager.ContractVillagerMemories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ContractBoardBlockEntity extends BlockEntity implements MenuProvider {

    public static final int ROWS = 2;
    public static final int SLOTS = ROWS * 9;
    private static final long VILLAGER_CHECK_INTERVAL_TICKS = 600L;
    private static final double VILLAGER_SEARCH_RADIUS = 24.0;

    // Refresh model ported 1:1 from Bountiful's BoardBlockEntity.kt (reference/Bountiful), scaled
    // from their 21-slot board (prune at >=12 taken, extra fill at >=18 free) to our 18 slots -
    // see docs/board-lifecycle-audit.md for the full comparison and the scaling math.
    private static final int PRUNE_TAKEN_THRESHOLD = 10;
    private static final int EXTRA_FILL_FREE_THRESHOLD = 15;
    private static final int[] PRUNE_COUNT_WEIGHTS = {1, 1, 1, 1, 2, 2, 2};
    private static final long EXPIRY_CHECK_INTERVAL_TICKS = 100L;

    // Per-block state, used whenever Config.boardGlobalState() is false (the default). When true,
    // every board on the server instead shares GlobalBoardData.get(level).state - see
    // activeState(ServerLevel), mirroring Bountiful's own localState/GlobalBoardData split.
    private final BoardState localState = new BoardState();

    public ContractBoardBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersContractsMod.CONTRACT_BOARD_ENTITY.get(), pos, state);
    }

    private BoardState activeState(ServerLevel level) {
        return Config.boardGlobalState() ? GlobalBoardData.get(level).state : localState;
    }

    private void markDirty(ServerLevel level) {
        if (Config.boardGlobalState()) {
            GlobalBoardData.get(level).setDirty();
        }
        setChanged();
    }

    // Server-only accessor (menu creation, SelfTest) - falls back to local state if this block
    // entity isn't attached to a real ServerLevel yet (e.g. a bare instance built directly in a
    // test without being placed), since there is nothing meaningful to look up global state with.
    public SimpleContainer container() {
        Level level = this.getLevel();
        if (Config.boardGlobalState() && level instanceof ServerLevel serverLevel) {
            return GlobalBoardData.get(serverLevel).state.container;
        }
        return localState.container;
    }

    // Test-only hook (SelfTest) to simulate a block entity whose chunk was unloaded for a long
    // time, without needing to actually fast-forward the server's real gameTime. Always targets
    // localState directly since tests construct bare entities without a real ServerLevel attached.
    public void forceLastUpdateGameTimeForTest(long gameTime) {
        localState.lastUpdateGameTime = gameTime;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.farmerscontracts.contract_board");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        BoardState state = activeState((ServerLevel) player.level());
        Set<Integer> mask = state.takenSlots.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        MaskedBoardContainer masked = new MaskedBoardContainer(state.container, mask, this::setChanged);
        ContainerLevelAccess access = ContainerLevelAccess.create(this.getLevel(), this.getBlockPos());
        return new ContractBoardMenu(containerId, playerInventory, masked, ROWS, access);
    }

    private void clearMask(BoardState state, int slot) {
        for (Set<Integer> mask : state.takenSlots.values()) {
            mask.remove(slot);
        }
    }

    private void clearSlot(BoardState state, int slot) {
        state.container.setItem(slot, ItemStack.EMPTY);
        state.slotTimestamps.remove(slot);
        clearMask(state, slot);
    }

    private void clearExpired(ServerLevel level, BoardState state) {
        for (int i = 0; i < state.container.getContainerSize(); i++) {
            ItemStack stack = state.container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            GeneratedContract contract = stack.get(ContractDataComponents.CONTRACT_DATA.get());
            if (contract != null && contract.isExpired(level.getGameTime())) {
                clearSlot(state, i);
            }
        }
    }

    private static List<Integer> freeSlotIndices(BoardState state) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < state.container.getContainerSize(); i++) {
            if (state.container.getItem(i).isEmpty()) {
                result.add(i);
            }
        }
        return result;
    }

    private static int takenSlotCount(BoardState state) {
        int count = 0;
        for (int i = 0; i < state.container.getContainerSize(); i++) {
            if (!state.container.getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // Ported 1:1 from Bountiful's BoardBlockEntity.randomlyUpdateBoard(): prune (if overfull),
    // then fill one free slot, then fill a second one if still very empty afterward. Thresholds
    // scaled from their 21-slot board to our 18 (see PRUNE_TAKEN_THRESHOLD/EXTRA_FILL_FREE_THRESHOLD).
    private void refillCycle(ServerLevel level, BoardState state) {
        if (takenSlotCount(state) >= PRUNE_TAKEN_THRESHOLD) {
            int pruneCount = PRUNE_COUNT_WEIGHTS[level.getRandom().nextInt(PRUNE_COUNT_WEIGHTS.length)];
            for (int i = 0; i < pruneCount; i++) {
                Integer slot = pickAgeWeightedEviction(level, state);
                if (slot == null) {
                    break;
                }
                clearSlot(state, slot);
            }
        }

        List<Integer> free = freeSlotIndices(state);
        if (free.size() > 1) {
            addToRandomFreeSlot(level, state, free);
            free = freeSlotIndices(state);
            if (free.size() >= EXTRA_FILL_FREE_THRESHOLD) {
                addToRandomFreeSlot(level, state, free);
            }
        }
    }

    private void addToRandomFreeSlot(ServerLevel level, BoardState state, List<Integer> free) {
        if (free.isEmpty()) {
            return;
        }
        int slot = free.get(level.getRandom().nextInt(free.size()));
        generateInto(level, state, slot);
    }

    private boolean generateInto(ServerLevel level, BoardState state, int slot) {
        ResourceLocation customerId = pickWeightedCustomer(level.getRandom());
        if (customerId == null) {
            return false;
        }

        Optional<GeneratedContract> generated = ContractGenerator.generate(customerId, level);
        if (generated.isEmpty()) {
            return false;
        }

        ItemStack ticket = new ItemStack(FarmersContractsMod.CONTRACT_TICKET.get());
        ticket.set(ContractDataComponents.CONTRACT_DATA.get(), generated.get());
        state.container.setItem(slot, ticket);
        state.slotTimestamps.put(slot, level.getGameTime());
        clearMask(state, slot);
        return true;
    }

    // Bountiful's weightedBountySlot(): weight = how long a slot has sat on the board (age), not
    // how close its contract is to expiring - the older an offer, the more likely it gets
    // rotated out to make room for something new.
    private static Integer pickAgeWeightedEviction(ServerLevel level, BoardState state) {
        long now = level.getGameTime();
        List<Integer> slots = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0;
        for (int i = 0; i < state.container.getContainerSize(); i++) {
            if (state.container.getItem(i).isEmpty()) {
                continue;
            }
            long placedAt = state.slotTimestamps.getOrDefault(i, now);
            double weight = Math.max(1L, now - placedAt);
            slots.add(i);
            weights.add(weight);
            totalWeight += weight;
        }
        if (slots.isEmpty()) {
            return null;
        }

        double roll = level.getRandom().nextDouble() * totalWeight;
        double acc = 0;
        for (int i = 0; i < slots.size(); i++) {
            acc += weights.get(i);
            if (roll <= acc) {
                return slots.get(i);
            }
        }
        return slots.get(slots.size() - 1);
    }

    private void attractVillager(ServerLevel level, BlockPos pos) {
        if (level.getRandom().nextInt(3) != 0) {
            return;
        }
        AABB area = AABB.ofSize(Vec3.atCenterOf(pos),
                VILLAGER_SEARCH_RADIUS * 2, VILLAGER_SEARCH_RADIUS * 2, VILLAGER_SEARCH_RADIUS * 2);
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class, area,
                v -> !v.getBrain().hasMemoryValue(ContractVillagerMemories.NEAREST_BOARD.get()));
        if (candidates.isEmpty()) {
            return;
        }
        Villager chosen = candidates.get(level.getRandom().nextInt(candidates.size()));
        chosen.getBrain().setMemory(ContractVillagerMemories.NEAREST_BOARD.get(), pos.immutable());
    }

    private static ResourceLocation pickWeightedCustomer(RandomSource random) {
        Map<ResourceLocation, Customer> customers = ContractContent.customers();
        double totalWeight = customers.values().stream().mapToDouble(Customer::weight).sum();
        if (totalWeight <= 0) {
            List<ResourceLocation> ids = List.copyOf(customers.keySet());
            return ids.isEmpty() ? null : ids.get(random.nextInt(ids.size()));
        }

        double roll = random.nextDouble() * totalWeight;
        double acc = 0;
        for (Map.Entry<ResourceLocation, Customer> entry : customers.entrySet()) {
            acc += entry.getValue().weight();
            if (roll <= acc) {
                return entry.getKey();
            }
        }
        return List.copyOf(customers.keySet()).get(customers.size() - 1);
    }

    // Without this, breaking the board and placing a new one at the same position comes back
    // completely empty (and, now that refresh timing lives on the block entity too, resets
    // lastUpdateGameTime/slotTimestamps back to nothing, which would look like an instant reroll
    // exploit). Carrying the container's contents AND the refresh bookkeeping onto the dropped
    // item (and restoring them on placement) makes break+replace a no-op instead - exactly
    // Bountiful's own getDrops/setPlacedBy pattern (their local-mode board copies its ENTIRE NBT
    // onto the item; ours is the typed-component equivalent, split across DataComponents.CONTAINER
    // for the items and ContractDataComponents.BOARD_STATE for everything else).
    //
    // In global mode there is nothing local worth carrying at all - EVERY board already reflects
    // the same shared truth regardless of which physical block you break, and restoring a
    // break-time snapshot onto that shared state on placement would let breaking+replacing any
    // single board stomp the whole server's offers. So these skip entirely when
    // Config.boardGlobalState() is on, mirroring how Bountiful's saveCustomOnly() ends up empty
    // in global mode (their saveAdditional only writes localState when !isGlobalMode).
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (Config.boardGlobalState()) {
            return;
        }

        NonNullList<ItemStack> items = NonNullList.withSize(localState.container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < localState.container.getContainerSize(); i++) {
            items.set(i, localState.container.getItem(i));
        }
        builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));

        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < localState.container.getContainerSize(); i++) {
            timestamps.add(localState.slotTimestamps.getOrDefault(i, 0L));
        }
        builder.set(ContractDataComponents.BOARD_STATE.get(), new BoardRefreshState(localState.lastUpdateGameTime, localState.initialized, timestamps));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        if (Config.boardGlobalState()) {
            return;
        }

        ItemContainerContents contents = input.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(localState.container.getContainerSize(), ItemStack.EMPTY);
            contents.copyInto(items);
            for (int i = 0; i < items.size(); i++) {
                localState.container.setItem(i, items.get(i));
            }
        }

        BoardRefreshState state = input.get(ContractDataComponents.BOARD_STATE.get());
        if (state != null) {
            localState.lastUpdateGameTime = state.lastUpdateGameTime();
            localState.initialized = state.initialized();
            localState.slotTimestamps.clear();
            List<Long> timestamps = state.slotTimestamps();
            for (int i = 0; i < timestamps.size(); i++) {
                long ts = timestamps.get(i);
                if (ts != 0L) {
                    localState.slotTimestamps.put(i, ts);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Only persist local state when it's actually the active one - matches Bountiful's own
        // saveAdditional (if (!isGlobalMode) localState.saveTo(output)). loadAdditional below
        // still always loads it unconditionally, so toggling global mode back off later doesn't
        // lose whatever was last saved locally.
        if (!Config.boardGlobalState()) {
            localState.saveTo(tag, registries);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        localState.loadFrom(tag, registries);
    }

    // If the board has never been used before (pristine - matches Bountiful's isPristine/
    // upkeepTryInitialPopulation), seed it with a handful of refresh cycles right away instead of
    // waiting a full updateFrequencySeconds for the first offer to appear.
    private void upkeepTryInitialPopulation(ServerLevel level, BoardState state) {
        if (state.initialized) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            refillCycle(level, state);
        }
        state.initialized = true;
        state.lastUpdateGameTime = level.getGameTime();
        markDirty(level);
    }

    // Refresh cadence ported 1:1 from Bountiful's BoardBlockEntity.upkeepBountyGeneration(): real
    // seconds elapsed (via getGameTime(), immune to /time set/add, which only ever touch
    // getDayTime()), with catch-up if the block entity's chunk was unloaded for a while, capped at
    // SLOTS so a very long absence can't dump dozens of refill cycles on the board at once.
    public static void tick(Level level, BlockPos pos, BlockState state, ContractBoardBlockEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BoardState activeState = entity.activeState(serverLevel);

        entity.upkeepTryInitialPopulation(serverLevel, activeState);

        long updateFrequencyTicks = Config.boardUpdateFrequencySeconds() * 20L;
        long elapsed = serverLevel.getGameTime() - activeState.lastUpdateGameTime;
        if (updateFrequencyTicks > 0 && elapsed >= updateFrequencyTicks) {
            long numUpdates = Math.min(elapsed / updateFrequencyTicks, SLOTS);
            activeState.lastUpdateGameTime = serverLevel.getGameTime();
            for (long i = 0; i < numUpdates; i++) {
                entity.refillCycle(serverLevel, activeState);
            }
            entity.markDirty(serverLevel);
        }

        if (level.getGameTime() % EXPIRY_CHECK_INTERVAL_TICKS == 0) {
            entity.clearExpired(serverLevel, activeState);
        }

        if (level.getGameTime() % VILLAGER_CHECK_INTERVAL_TICKS == 0) {
            entity.attractVillager(serverLevel, pos);
        }
    }
}
