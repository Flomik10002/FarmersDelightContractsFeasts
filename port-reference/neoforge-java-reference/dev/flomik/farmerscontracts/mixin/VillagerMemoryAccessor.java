package dev.flomik.farmerscontracts.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Villager.class)
public interface VillagerMemoryAccessor {

    @Accessor("MEMORY_TYPES")
    static ImmutableList<MemoryModuleType<?>> farmerscontracts$memoryTypes() {
        throw new UnsupportedOperationException("Mixin accessor not applied");
    }

    @Accessor("SENSOR_TYPES")
    static ImmutableList<SensorType<? extends Sensor<? super Villager>>> farmerscontracts$sensorTypes() {
        throw new UnsupportedOperationException("Mixin accessor not applied");
    }
}
