package dev.flomik.farmerscontracts.mixin;

import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SidedInvWrapper.class)
public class SidedInvWrapperInsertMixin {
    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void farmerscontracts$refuseContractBox(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (ContractBoxItem.isFilledContractBox(stack)) {
            cir.setReturnValue(stack);
        }
    }
}
