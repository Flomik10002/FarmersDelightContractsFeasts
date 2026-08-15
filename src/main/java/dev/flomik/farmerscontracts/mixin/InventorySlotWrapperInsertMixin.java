package dev.flomik.farmerscontracts.mixin;

import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.impl.transfer.item.InventoryStorageImpl;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.fabricmc.fabric.impl.transfer.item.InventorySlotWrapper")
public abstract class InventorySlotWrapperInsertMixin {
    @Shadow(remap = false)
    @Final
    private InventoryStorageImpl storage;

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true, remap = false)
    private void farmerscontracts$refuseContractBox(ItemVariant insertedVariant, long maxAmount, TransactionContext transaction, CallbackInfoReturnable<Long> cir) {
        if (!ContractBoxItem.isFilledContractBox(insertedVariant.toStack())) {
            return;
        }
        if (((InventoryStorageAccessor) storage).farmerscontracts$inventory() instanceof Inventory) {
            return;
        }

        cir.setReturnValue(0L);
    }
}
