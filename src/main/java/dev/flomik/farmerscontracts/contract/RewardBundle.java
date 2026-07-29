package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record RewardBundle(List<GeneratedLine> items, int xp) {

    public static final Codec<RewardBundle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GeneratedLine.CODEC.listOf().fieldOf("items").forGetter(RewardBundle::items),
            Codec.INT.fieldOf("xp").forGetter(RewardBundle::xp)
    ).apply(instance, RewardBundle::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RewardBundle> STREAM_CODEC = StreamCodec.composite(
            GeneratedLine.STREAM_CODEC.apply(ByteBufCodecs.list()), RewardBundle::items,
            ByteBufCodecs.VAR_INT, RewardBundle::xp,
            RewardBundle::new
    );
}
