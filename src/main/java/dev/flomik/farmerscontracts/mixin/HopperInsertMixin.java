package dev.flomik.farmerscontracts.mixin;

import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public class HopperInsertMixin {
    @Inject(method = "canPlaceItemInContainer", at = @At("HEAD"), cancellable = true)
    private static void farmerscontracts$refuseContractBox(
            Container container,
            ItemStack stack,
            int slot,
            @Nullable Direction direction,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (ContractBoxItem.isFilledContractBox(stack)) {
            cir.setReturnValue(false);
        }
    }
}
