package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record Customer(
        String name,
        List<ResourceLocation> objectivePools,
        List<ResourceLocation> rewardPools,
        List<ResourceLocation> bonusRewardPools,
        IntRange objectiveCount,
        double weight
) {
    private static final IntRange DEFAULT_OBJECTIVE_COUNT = new IntRange(1, 2);

    public static final Codec<Customer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Customer::name),
            ResourceLocation.CODEC.listOf().fieldOf("objectives").forGetter(Customer::objectivePools),
            ResourceLocation.CODEC.listOf().fieldOf("rewards").forGetter(Customer::rewardPools),
            ResourceLocation.CODEC.listOf().optionalFieldOf("bonus_rewards", List.of()).forGetter(Customer::bonusRewardPools),
            IntRange.CODEC.optionalFieldOf("objective_count", DEFAULT_OBJECTIVE_COUNT).forGetter(Customer::objectiveCount),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(Customer::weight)
    ).apply(instance, Customer::new));
}
