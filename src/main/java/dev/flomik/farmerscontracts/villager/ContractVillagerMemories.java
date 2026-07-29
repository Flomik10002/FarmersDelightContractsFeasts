package dev.flomik.farmerscontracts.villager;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

public final class ContractVillagerMemories {

    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULE_TYPES =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, FarmersContractsMod.MODID);

    public static final RegistryObject<MemoryModuleType<BlockPos>> NEAREST_BOARD =
            MEMORY_MODULE_TYPES.register("nearest_contract_board", () -> new MemoryModuleType<>(Optional.empty()));

    private ContractVillagerMemories() {
    }
}
