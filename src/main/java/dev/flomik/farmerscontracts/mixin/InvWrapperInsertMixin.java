package dev.flomik.farmerscontracts.mixin;

import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InvWrapper.class)
public class InvWrapperInsertMixin {
    @Shadow
    public Container getInv() {
        throw new AssertionError();
    }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void farmerscontracts$refuseContractBox(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (ContractBoxItem.isFilledContractBox(stack) && !(getInv() instanceof Inventory)) {
            cir.setReturnValue(stack);
        }
    }
}
