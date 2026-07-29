package dev.flomik.farmerscontracts.board;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ContractBoardMenu extends AbstractContainerMenu {

    private final Container container;
    private final int rows;

    public ContractBoardMenu(int containerId, Inventory playerInventory, Container container, int rows) {
        super(FarmersContractsMod.CONTRACT_BOARD_MENU.get(), containerId);
        checkContainerSize(container, rows * 9);
        this.container = container;
        this.rows = rows;
        container.startOpen(playerInventory.player);

        int topOffset = (rows - 4) * 18;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new OfferSlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + topOffset));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + topOffset));
        }
    }

    public ContractBoardMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(ContractBoardBlockEntity.SLOTS), ContractBoardBlockEntity.ROWS);
    }

    public int getRowCount() {
        return rows;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();
            int boardSlots = rows * 9;
            if (index < boardSlots) {
                if (!this.moveItemStackTo(slotStack, boardSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(slotStack, 0, boardSlots, false)) {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private static class OfferSlot extends Slot {
        OfferSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
