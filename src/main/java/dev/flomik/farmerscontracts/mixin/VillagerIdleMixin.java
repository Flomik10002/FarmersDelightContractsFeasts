package dev.flomik.farmerscontracts.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.flomik.farmerscontracts.villager.VisitContractBoardBehavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(VillagerGoalPackages.class)
public class VillagerIdleMixin {

    @Inject(method = "getIdlePackage", at = @At("RETURN"), cancellable = true)
    private static void farmerscontracts$addBoardVisit(
            VillagerProfession profession,
            float speed,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir
    ) {
        List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> tasks = new ArrayList<>(cir.getReturnValue());
        tasks.add(Pair.of(4, new VisitContractBoardBehavior(0.35F)));
        cir.setReturnValue(ImmutableList.copyOf(tasks));
    }
}
