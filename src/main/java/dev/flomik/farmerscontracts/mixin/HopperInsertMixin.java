package dev.flomik.farmerscontracts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.flomik.farmerscontracts.box.ContractBoxBlockEntity;
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

    @ModifyExpressionValue(
            method = "tryMoveInItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private static int farmerscontracts$boxSlotLimit(
            int original,
            @Nullable Container source,
            Container destination,
            ItemStack stack,
            int slot,
            @Nullable Direction direction
    ) {
        return destination instanceof ContractBoxBlockEntity ? ContractBoxBlockEntity.MAX_STACK_SIZE : original;
    }

    @ModifyExpressionValue(
            method = "tryMoveInItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;canMergeItems(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean farmerscontracts$boxCanMerge(
            boolean original,
            @Nullable Container source,
            Container destination,
            ItemStack stack,
            int slot,
            @Nullable Direction direction
    ) {
        if (original || !(destination instanceof ContractBoxBlockEntity)) {
            return original;
        }
        return ItemStack.isSameItemSameComponents(destination.getItem(slot), stack);
    }
}
