package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GeneratedLine(ItemStack stack, int amount, double worth) {

    public static final Codec<GeneratedLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(GeneratedLine::stack),
            Codec.INT.fieldOf("amount").forGetter(GeneratedLine::amount),
            Codec.DOUBLE.fieldOf("worth").forGetter(GeneratedLine::worth)
    ).apply(instance, GeneratedLine::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GeneratedLine> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, GeneratedLine::stack,
            ByteBufCodecs.VAR_INT, GeneratedLine::amount,
            ByteBufCodecs.DOUBLE, GeneratedLine::worth,
            GeneratedLine::new
    );

    public static List<GeneratedLine> mergeByItem(List<GeneratedLine> lines) {
        Map<Item, GeneratedLine> merged = new LinkedHashMap<>();
        for (GeneratedLine line : lines) {
            merged.merge(line.stack().getItem(), line,
                    (a, b) -> new GeneratedLine(a.stack(), a.amount() + b.amount(), a.worth() + b.worth()));
        }
        return new ArrayList<>(merged.values());
    }
}
