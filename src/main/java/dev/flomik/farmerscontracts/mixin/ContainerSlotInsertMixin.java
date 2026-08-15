package dev.flomik.farmerscontracts.mixin;

import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class ContainerSlotInsertMixin {
    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void farmerscontracts$refuseContractBoxOutsidePlayerInventory(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ContractBoxItem.isFilledContractBox(stack) && !(this.container instanceof Inventory)) {
            cir.setReturnValue(false);
        }
    }
}
