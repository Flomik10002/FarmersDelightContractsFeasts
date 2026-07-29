package dev.flomik.farmerscontracts.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GeneratedLine(ItemStack stack, int amount, double worth) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.put("Stack", stack.save(new CompoundTag()));
        tag.putInt("Amount", amount);
        tag.putDouble("Worth", worth);
        return tag;
    }

    public static GeneratedLine fromNbt(CompoundTag tag) {
        ItemStack stack = ItemStack.of(tag.getCompound("Stack"));
        return new GeneratedLine(stack, tag.getInt("Amount"), tag.getDouble("Worth"));
    }

    public static List<GeneratedLine> mergeByItem(List<GeneratedLine> lines) {
        Map<Item, GeneratedLine> merged = new LinkedHashMap<>();
        for (GeneratedLine line : lines) {
            merged.merge(line.stack().getItem(), line,
                    (a, b) -> new GeneratedLine(a.stack(), a.amount() + b.amount(), a.worth() + b.worth()));
        }
        return new ArrayList<>(merged.values());
    }
}
