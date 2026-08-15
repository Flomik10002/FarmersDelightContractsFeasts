package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringRepresentable;

public enum ContractRarity implements StringRepresentable {
    COMMON("common", 0x87917A, 1000.0, 0, 1.0, 1),
    UNCOMMON("uncommon", 0xB18A52, 350.0, 5, 1.15, 2),
    RARE("rare", 0x687F91, 90.0, 15, 1.35, 3),
    SPECIAL("special", 0x8C6278, 15.0, 30, 1.6, 4);

    private static final double WEIGHT_SCALING = 2.25;

    public static final Codec<ContractRarity> CODEC = StringRepresentable.fromEnum(ContractRarity::values);

    public static ContractRarity byName(String name) {
        for (ContractRarity rarity : values()) {
            if (rarity.serializedName.equals(name)) {
                return rarity;
            }
        }
        return COMMON;
    }

    private final String serializedName;
    private final TextColor color;
    private final double baseWeight;
    private final long repThreshold;
    private final double rewardMultiplier;
    private final int tierPoints;

    ContractRarity(String serializedName, int rgb, double baseWeight, long repThreshold, double rewardMultiplier, int tierPoints) {
        this.serializedName = serializedName;
        this.color = TextColor.fromRgb(rgb);
        this.baseWeight = baseWeight;
        this.repThreshold = repThreshold;
        this.rewardMultiplier = rewardMultiplier;
        this.tierPoints = tierPoints;
    }

    public TextColor color() {
        return color;
    }

    public Style style() {
        return Style.EMPTY.withColor(color);
    }

    public double baseWeight() {
        return baseWeight;
    }

    public double rewardMultiplier() {
        return rewardMultiplier;
    }

    public int tierPoints() {
        return tierPoints;
    }

    public static ContractRarity forReputation(long completedContracts) {
        ContractRarity result = COMMON;
        for (ContractRarity rarity : values()) {
            if (completedContracts >= rarity.repThreshold) {
                result = rarity;
            }
        }
        return result;
    }

    public double weightAdjustedFor(long completedContracts) {
        ContractRarity unlocked = forReputation(completedContracts);
        int gap = Math.max(unlocked.ordinal() - this.ordinal(), 0);
        return baseWeight / Math.pow(WEIGHT_SCALING, gap);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
