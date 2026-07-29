package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

public record ContractPoolEntry(
        Ingredient item,
        IntRange amount,
        Optional<Double> unitWorth,
        double weight,
        ContractRarity rarity,
        List<String> markers,
        List<String> forbidMarkers
) {
    public static final Codec<ContractPoolEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.fieldOf("item").forGetter(ContractPoolEntry::item),
            IntRange.CODEC.fieldOf("amount").forGetter(ContractPoolEntry::amount),
            Codec.DOUBLE.optionalFieldOf("unit_worth").forGetter(ContractPoolEntry::unitWorth),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(ContractPoolEntry::weight),
            ContractRarity.CODEC.optionalFieldOf("rarity", ContractRarity.COMMON).forGetter(ContractPoolEntry::rarity),
            Codec.STRING.listOf().optionalFieldOf("markers", List.of()).forGetter(ContractPoolEntry::markers),
            Codec.STRING.listOf().optionalFieldOf("forbid_markers", List.of()).forGetter(ContractPoolEntry::forbidMarkers)
    ).apply(instance, ContractPoolEntry::new));

    public double worthPerUnit(Item resolvedItem) {
        return unitWorth.orElseGet(() -> RecipeCostSolver.costOf(resolvedItem));
    }

    public double worthFor(Item resolvedItem, int count) {
        return worthPerUnit(resolvedItem) * count;
    }

    public boolean conflictsWith(ContractPoolEntry other) {
        if (other == this) {
            return false;
        }
        return forbidMarkers.stream().anyMatch(other.markers()::contains)
                || other.forbidMarkers().stream().anyMatch(markers()::contains);
    }
}
