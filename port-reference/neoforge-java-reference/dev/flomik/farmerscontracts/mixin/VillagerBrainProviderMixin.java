package dev.flomik.farmerscontracts.mixin;

import com.google.common.collect.ImmutableList;
import dev.flomik.farmerscontracts.villager.ContractVillagerMemories;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public class VillagerBrainProviderMixin {

    @Inject(method = "brainProvider", at = @At("RETURN"), cancellable = true)
    private void farmerscontracts$extendMemoryTypes(CallbackInfoReturnable<Brain.Provider<Villager>> cir) {
        ImmutableList<MemoryModuleType<?>> extended = ImmutableList.<MemoryModuleType<?>>builder()
                .addAll(VillagerMemoryAccessor.farmerscontracts$memoryTypes())
                .add(ContractVillagerMemories.NEAREST_BOARD.get())
                .build();
        cir.setReturnValue(Brain.provider(extended, VillagerMemoryAccessor.farmerscontracts$sensorTypes()));
    }
}
