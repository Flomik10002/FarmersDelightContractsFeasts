package dev.flomik.farmerscontracts.box;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ContractBoxItemStorage extends CombinedSlottedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> {
    public ContractBoxItemStorage(ContractBoxBlockEntity box) {
        super(slotsOf(box));
    }

    private static List<SingleSlotStorage<ItemVariant>> slotsOf(ContractBoxBlockEntity box) {
        List<SingleSlotStorage<ItemVariant>> parts = new ArrayList<>(ContractBoxBlockEntity.SLOTS);
        for (int slot = 0; slot < ContractBoxBlockEntity.SLOTS; slot++) {
            parts.add(new BoxSlotStorage(box, slot));
        }
        return parts;
    }

    private static class BoxSlotStorage extends SingleStackStorage {
        private final ContractBoxBlockEntity box;
        private final int slot;

        BoxSlotStorage(ContractBoxBlockEntity box, int slot) {
            this.box = box;
            this.slot = slot;
        }

        @Override
        protected ItemStack getStack() {
            return box.container().getItem(slot);
        }

        @Override
        protected void setStack(ItemStack stack) {
            box.container().setItem(slot, stack);
        }

        @Override
        protected boolean canInsert(ItemVariant variant) {
            return box.container().canPlaceItem(slot, variant.toStack());
        }

        @Override
        protected boolean canExtract(ItemVariant variant) {
            return !box.isSealed();
        }

        @Override
        protected int getCapacity(ItemVariant variant) {
            return ContractBoxBlockEntity.MAX_STACK_SIZE;
        }

        @Override
        protected void onFinalCommit() {
            box.setChanged();
        }
    }
}
