package dev.flomik.farmerscontracts.mixin;

import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InvWrapper.class, remap = false)
public class InvWrapperInsertMixin {
    @Shadow(remap = false)
    public Container getInv() {
        throw new AssertionError();
    }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void farmerscontracts$refuseContractBox(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (ContractBoxItem.isFilledContractBox(stack) && !(getInv() instanceof Inventory)) {
            cir.setReturnValue(stack);
        }
    }
}
