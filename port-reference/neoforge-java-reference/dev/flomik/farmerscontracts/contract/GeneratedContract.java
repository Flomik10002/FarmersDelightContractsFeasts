package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record GeneratedContract(
        ResourceLocation customerId,
        String customerName,
        ContractRarity rarity,
        List<GeneratedLine> objectives,
        RewardBundle rewardBundle,
        long expiresAtGameTime
) {

    public static final Codec<GeneratedContract> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("customer_id").forGetter(GeneratedContract::customerId),
            Codec.STRING.fieldOf("customer_name").forGetter(GeneratedContract::customerName),
            ContractRarity.CODEC.fieldOf("rarity").forGetter(GeneratedContract::rarity),
            GeneratedLine.CODEC.listOf().fieldOf("objectives").forGetter(GeneratedContract::objectives),
            RewardBundle.CODEC.fieldOf("rewards").forGetter(GeneratedContract::rewardBundle),
            Codec.LONG.fieldOf("expires_at").forGetter(GeneratedContract::expiresAtGameTime)
    ).apply(instance, GeneratedContract::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GeneratedContract> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, GeneratedContract::customerId,
            ByteBufCodecs.STRING_UTF8, GeneratedContract::customerName,
            ContractRarity.STREAM_CODEC, GeneratedContract::rarity,
            GeneratedLine.STREAM_CODEC.apply(ByteBufCodecs.list()), GeneratedContract::objectives,
            RewardBundle.STREAM_CODEC, GeneratedContract::rewardBundle,
            ByteBufCodecs.VAR_LONG, GeneratedContract::expiresAtGameTime,
            GeneratedContract::new
    );

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
