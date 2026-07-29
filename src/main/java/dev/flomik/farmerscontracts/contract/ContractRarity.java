package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum ContractRarity implements StringRepresentable {
    COMMON("common", 0x87917A, 1000.0, 0, 1.0),
    UNCOMMON("uncommon", 0xB18A52, 350.0, 5, 1.15),
    RARE("rare", 0x687F91, 90.0, 15, 1.35),
    SPECIAL("special", 0x8C6278, 15.0, 30, 1.6);

    private static final double WEIGHT_SCALING = 2.25;

    public static final Codec<ContractRarity> CODEC = StringRepresentable.fromValues(ContractRarity::values);
    public static final StreamCodec<ByteBuf, ContractRarity> STREAM_CODEC =
            ByteBufCodecs.idMapper(id -> ContractRarity.values()[id], ContractRarity::ordinal);

    private final String serializedName;
    private final TextColor color;
    private final double baseWeight;
    private final long repThreshold;
    private final double rewardMultiplier;

    ContractRarity(String serializedName, int rgb, double baseWeight, long repThreshold, double rewardMultiplier) {
        this.serializedName = serializedName;
        this.color = TextColor.fromRgb(rgb);
        this.baseWeight = baseWeight;
        this.repThreshold = repThreshold;
        this.rewardMultiplier = rewardMultiplier;
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
