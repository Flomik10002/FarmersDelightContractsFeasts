package dev.flomik.farmerscontracts.villager;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Optional;

public final class ContractVillagerMemories {
    public static final MemoryModuleType<BlockPos> NEAREST_BOARD = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, "nearest_contract_board"),
            new MemoryModuleType<>(Optional.empty()));

    private ContractVillagerMemories() {
    }
}
