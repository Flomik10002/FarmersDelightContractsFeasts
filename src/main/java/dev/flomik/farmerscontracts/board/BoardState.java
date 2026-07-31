package dev.flomik.farmerscontracts.board;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Everything a Contract Board needs to run its Bountiful-ported refresh cycle, factored out of
// ContractBoardBlockEntity so the exact same shape can be used either per-block (kept directly on
// the block entity, the default) or server-wide (GlobalBoardData, config-gated) - mirrors
// Bountiful's own BoardBlockEntity.localState/GlobalBoardData split (both literally the same
// Kotlin class there; this is the Java equivalent). See docs/board-lifecycle-audit.md.
public final class BoardState {

    public final SimpleContainer container = new SimpleContainer(ContractBoardBlockEntity.SLOTS);
    public final Map<Integer, Long> slotTimestamps = new HashMap<>();
    public final Map<UUID, Set<Integer>> takenSlots = new HashMap<>();
    public long lastUpdateGameTime = 0L;
    public boolean initialized = false;

    public void saveTo(CompoundTag tag) {
        ListTag itemsTag = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag itemTag = new CompoundTag();
            itemTag.putInt("Slot", i);
            stack.save(itemTag);
            itemsTag.add(itemTag);
        }
        tag.put("Items", itemsTag);
        tag.putLong("LastUpdateGameTime", lastUpdateGameTime);
        tag.putBoolean("Initialized", initialized);

        ListTag timestampsTag = new ListTag();
        for (Map.Entry<Integer, Long> entry : slotTimestamps.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("Slot", entry.getKey());
            entryTag.putLong("Time", entry.getValue());
            timestampsTag.add(entryTag);
        }
        tag.put("SlotTimestamps", timestampsTag);

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

    public void loadFrom(CompoundTag tag) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        ListTag itemsTag = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemsTag.size(); i++) {
            CompoundTag itemTag = itemsTag.getCompound(i);
            int slot = itemTag.getInt("Slot");
            if (slot >= 0 && slot < container.getContainerSize()) {
                container.setItem(slot, ItemStack.of(itemTag));
            }
        }
        lastUpdateGameTime = tag.getLong("LastUpdateGameTime");
        initialized = tag.getBoolean("Initialized");

        slotTimestamps.clear();
        ListTag timestampsTag = tag.getList("SlotTimestamps", Tag.TAG_COMPOUND);
        for (int i = 0; i < timestampsTag.size(); i++) {
            CompoundTag entryTag = timestampsTag.getCompound(i);
            slotTimestamps.put(entryTag.getInt("Slot"), entryTag.getLong("Time"));
        }

        takenSlots.clear();
        ListTag masksTag = tag.getList("TakenMasks", Tag.TAG_COMPOUND);
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
}
