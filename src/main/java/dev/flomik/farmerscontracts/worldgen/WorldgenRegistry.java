package dev.flomik.farmerscontracts.worldgen;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorldgenRegistry {

    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSOR_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, FarmersContractsMod.MODID);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<WoodByBiomeProcessor>> WOOD_BY_BIOME =
            STRUCTURE_PROCESSOR_TYPES.register("wood_by_biome", () -> () -> WoodByBiomeProcessor.CODEC);

    private WorldgenRegistry() {
    }
}
