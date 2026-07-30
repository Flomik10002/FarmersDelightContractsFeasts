package dev.flomik.farmerscontracts.board;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractContent;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.ContractGenerator;
import dev.flomik.farmerscontracts.contract.ContractProgress;
import dev.flomik.farmerscontracts.contract.Customer;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.villager.ContractVillagerMemories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ContractBoardBlockEntity extends BlockEntity implements MenuProvider {

    public static final int ROWS = 2;
    public static final int SLOTS = ROWS * 9;
    private static final long DAY_LENGTH_TICKS = 24000L;
    private static final int[] DAILY_ROTATION_COUNTS = {0, 1, 1, 1, 2};
    private static final long VILLAGER_CHECK_INTERVAL_TICKS = 600L;
    private static final double VILLAGER_SEARCH_RADIUS = 24.0;

    private final SimpleContainer container = new SimpleContainer(SLOTS);
    private final Map<UUID, Set<Integer>> takenSlots = new HashMap<>();

    public ContractBoardBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersContractsMod.CONTRACT_BOARD_ENTITY.get(), pos, state);
    }

    public SimpleContainer container() {
        return container;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.farmerscontracts.contract_board");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        Set<Integer> mask = takenSlots.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        MaskedBoardContainer masked = new MaskedBoardContainer(container, mask, this::setChanged);
        return new ContractBoardMenu(containerId, playerInventory, masked, ROWS);
    }

    private void clearMask(int slot) {
        for (Set<Integer> mask : takenSlots.values()) {
            mask.remove(slot);
        }
    }

    private void clearExpired(ServerLevel level) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            GeneratedContract contract = stack.get(ContractDataComponents.CONTRACT_DATA.get());
            if (contract != null && contract.isExpired(level.getGameTime())) {
                container.setItem(i, ItemStack.EMPTY);
                clearMask(i);
            }
        }
    }

    private static final int INITIAL_FILL_COUNT = 3;

    private void refillInitial(ServerLevel level) {
        List<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                emptySlots.add(i);
            }
        }

        int toFill = Math.min(INITIAL_FILL_COUNT, emptySlots.size());
        for (int i = 0; i < toFill; i++) {
            int pick = level.getRandom().nextInt(emptySlots.size());
            int slot = emptySlots.remove(pick);
            generateInto(level, slot);
        }
    }

    private static final int MAX_DAILY_NEW_FILLS = 3;

    private void refillForNewDay(ServerLevel level) {
        List<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                emptySlots.add(i);
            }
        }

        int toFill = Math.min(MAX_DAILY_NEW_FILLS, emptySlots.size());
        for (int i = 0; i < toFill; i++) {
            int pick = level.getRandom().nextInt(emptySlots.size());
            int slot = emptySlots.remove(pick);
            generateInto(level, slot);
        }

        int rotations = DAILY_ROTATION_COUNTS[level.getRandom().nextInt(DAILY_ROTATION_COUNTS.length)];
        for (int i = 0; i < rotations; i++) {
            Integer slot = pickWeightedEviction(level);
            if (slot == null) {
                break;
            }
            generateInto(level, slot);
        }
    }

    private boolean generateInto(ServerLevel level, int slot) {
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
        container.setItem(slot, ticket);
        clearMask(slot);
        return true;
    }

    private Integer pickWeightedEviction(ServerLevel level) {
        long now = level.getGameTime();
        List<Integer> slots = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            GeneratedContract contract = stack.get(ContractDataComponents.CONTRACT_DATA.get());
            if (contract == null) {
                continue;
            }
            long remaining = Math.max(1, contract.expiresAtGameTime() - now);
            double weight = 1.0 / remaining;
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", container.createTag(registries));

        ListTag masksTag = new ListTag();
        for (Map.Entry<UUID, Set<Integer>> entry : takenSlots.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag maskTag = new CompoundTag();
            maskTag.putUUID("Player", entry.getKey());
            int[] slots = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            maskTag.putIntArray("Slots", slots);
            masksTag.add(maskTag);
        }
        tag.put("TakenMasks", masksTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList("Items", 10), registries);

        takenSlots.clear();
        ListTag masksTag = tag.getList("TakenMasks", 10);
        for (int i = 0; i < masksTag.size(); i++) {
            CompoundTag maskTag = masksTag.getCompound(i);
            UUID player = maskTag.getUUID("Player");
            Set<Integer> slots = new HashSet<>();
            for (int slot : maskTag.getIntArray("Slots")) {
                slots.add(slot);
            }
            takenSlots.put(player, slots);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ContractBoardBlockEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Refill state is keyed by position in ContractProgress (world-level SavedData) rather
        // than stored on this block entity, so breaking and replacing the board at the same spot
        // can't be used to force an instant reroll. Day tracking stays on getDayTime() (not
        // getGameTime()) so sleeping through the night still advances it normally - sleeping
        // only fast-forwards getDayTime(), not the real tick counter.
        ContractProgress progress = ContractProgress.get(serverLevel);
        long currentDay = Math.floorDiv(level.getDayTime(), DAY_LENGTH_TICKS);
        Long persistedDay = progress.boardLastRefillDay(pos);
        boolean freshlyPlaced = persistedDay == null;
        long lastRefillDay = freshlyPlaced ? currentDay - 1 : persistedDay;

        if (currentDay != lastRefillDay) {
            progress.setBoardLastRefillDay(pos, currentDay);
            entity.clearExpired(serverLevel);
            if (freshlyPlaced) {
                entity.refillInitial(serverLevel);
            } else {
                entity.refillForNewDay(serverLevel);
            }
            entity.setChanged();
        }

        if (level.getGameTime() % VILLAGER_CHECK_INTERVAL_TICKS == 0) {
            entity.attractVillager(serverLevel, pos);
        }
    }
}
