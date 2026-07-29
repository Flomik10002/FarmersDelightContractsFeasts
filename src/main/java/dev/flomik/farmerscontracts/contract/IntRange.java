package dev.flomik.farmerscontracts.contract;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

public record IntRange(int min, int max) {

    public IntRange {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") is greater than max (" + max + ")");
        }
    }

    private static final Codec<IntRange> RANGE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min").forGetter(IntRange::min),
            Codec.INT.fieldOf("max").forGetter(IntRange::max)
    ).apply(instance, IntRange::new));

    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, RANGE_CODEC).xmap(
            either -> either.map(exact -> new IntRange(exact, exact), range -> range),
            range -> range.min == range.max ? Either.left(range.min) : Either.right(range)
    );

    public int pick(RandomSource random) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }
}
