package dev.flomik.farmerscontracts.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record RewardBundle(List<GeneratedLine> items, int xp) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag itemsTag = new ListTag();
        for (GeneratedLine line : items) {
            itemsTag.add(line.toNbt());
        }
        tag.put("Items", itemsTag);
        tag.putInt("Xp", xp);
        return tag;
    }

    public static RewardBundle fromNbt(CompoundTag tag) {
        List<GeneratedLine> items = new ArrayList<>();
        for (Tag itemTag : tag.getList("Items", Tag.TAG_COMPOUND)) {
            items.add(GeneratedLine.fromNbt((CompoundTag) itemTag));
        }
        return new RewardBundle(items, tag.getInt("Xp"));
    }
}
