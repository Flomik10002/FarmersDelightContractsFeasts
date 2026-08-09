package dev.flomik.farmerscontracts.board;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ContractBoardMenu extends AbstractContainerMenu {

    private final Container container;
    private final int rows;
    private final ContainerLevelAccess access;

    public ContractBoardMenu(int containerId, Inventory playerInventory, Container container, int rows, ContainerLevelAccess access) {
        super(FarmersContractsMod.CONTRACT_BOARD_MENU.get(), containerId);
        checkContainerSize(container, rows * 9);
        this.container = container;
        this.rows = rows;
        this.access = access;
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
        this(containerId, playerInventory, new SimpleContainer(ContractBoardBlockEntity.SLOTS), ContractBoardBlockEntity.ROWS, ContainerLevelAccess.NULL);
    }

    public int getRowCount() {
        return rows;
    }

    @Override
    public boolean stillValid(Player player) {
        // SimpleContainer/MaskedBoardContainer.stillValid() is unconditionally true - it has no
        // notion of world position. Without this, the GUI would never auto-close even after the
        // player walks away or the board block is broken while the menu is still open.
        return stillValid(access, player, FarmersContractsMod.CONTRACT_BOARD.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        int boardSlots = rows * 9;
        ItemStack visible = slot.getItem();
        if (visible.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = visible.copy();

        if (index < boardSlots) {
            // slot.getItem() here is the live reference into the shared board container, not a
            // per-player masked copy - moveItemStackTo mutates its argument in place, so it must
            // never be handed the live reference directly, or a shift-click would shrink the
            // shared contract for every viewer instead of just marking it taken for this player.
            ItemStack moving = visible.copy();
            if (!this.moveItemStackTo(moving, boardSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            container.removeItem(index, visible.getCount() - moving.getCount());
            return result;
        }

        if (!this.moveItemStackTo(visible, 0, boardSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (visible.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
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
