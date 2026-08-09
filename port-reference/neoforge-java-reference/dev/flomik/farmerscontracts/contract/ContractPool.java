package dev.flomik.farmerscontracts.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record ContractPool(List<ContractPoolEntry> entries) {
    public static final Codec<ContractPool> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ContractPoolEntry.CODEC.listOf().fieldOf("entries").forGetter(ContractPool::entries)
    ).apply(instance, ContractPool::new));
}
