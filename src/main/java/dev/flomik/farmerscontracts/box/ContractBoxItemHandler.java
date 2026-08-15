package dev.flomik.farmerscontracts.box;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public class ContractBoxItemHandler extends InvWrapper {
    private final ContractBoxBlockEntity box;

    public ContractBoxItemHandler(ContractBoxBlockEntity box) {
        super(box);
        this.box = box;
    }

    @Override
    public int getSlotLimit(int slot) {
        return ContractBoxBlockEntity.MAX_STACK_SIZE;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!isItemValid(slot, stack)) {
            return stack;
        }

        ItemStack existing = getStackInSlot(slot);
        int room = getSlotLimit(slot);
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(stack, existing)) {
                return stack;
            }
            room -= existing.getCount();
        }
        if (room <= 0) {
            return stack;
        }

        int inserted = Math.min(room, stack.getCount());
        if (!simulate) {
            ItemStack updated = existing.isEmpty() ? stack.copyWithCount(inserted) : existing.copy();
            if (!existing.isEmpty()) {
                updated.grow(inserted);
            }

            getInv().setItem(slot, updated);
            getInv().setChanged();
        }
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (box.isSealed()) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(slot, amount, simulate);
    }
}
