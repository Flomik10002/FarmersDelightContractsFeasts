package dev.flomik.farmerscontracts.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record GeneratedContract(
        ResourceLocation customerId,
        String customerName,
        ContractRarity rarity,
        List<GeneratedLine> objectives,
        RewardBundle rewardBundle,
        long expiresAtGameTime
) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CustomerId", customerId.toString());
        tag.putString("CustomerName", customerName);
        tag.putString("Rarity", rarity.getSerializedName());
        ListTag objectivesTag = new ListTag();
        for (GeneratedLine line : objectives) {
            objectivesTag.add(line.toNbt());
        }
        tag.put("Objectives", objectivesTag);
        tag.put("Rewards", rewardBundle.toNbt());
        tag.putLong("ExpiresAt", expiresAtGameTime);
        return tag;
    }

    public static GeneratedContract fromNbt(CompoundTag tag) {
        ResourceLocation customerId = new ResourceLocation(tag.getString("CustomerId"));
        String customerName = tag.getString("CustomerName");
        ContractRarity rarity = ContractRarity.byName(tag.getString("Rarity"));
        List<GeneratedLine> objectives = new ArrayList<>();
        for (Tag lineTag : tag.getList("Objectives", Tag.TAG_COMPOUND)) {
            objectives.add(GeneratedLine.fromNbt((CompoundTag) lineTag));
        }
        RewardBundle rewardBundle = RewardBundle.fromNbt(tag.getCompound("Rewards"));
        long expiresAtGameTime = tag.getLong("ExpiresAt");
        return new GeneratedContract(customerId, customerName, rarity, objectives, rewardBundle, expiresAtGameTime);
    }

    public List<GeneratedLine> rewards() {
        return rewardBundle.items();
    }

    public int xp() {
        return rewardBundle.xp();
    }

    public boolean isExpired(long currentGameTime) {
        return currentGameTime > expiresAtGameTime;
    }
}
