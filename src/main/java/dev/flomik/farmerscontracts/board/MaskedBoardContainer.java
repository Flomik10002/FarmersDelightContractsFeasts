package dev.flomik.farmerscontracts.board;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public class MaskedBoardContainer implements Container {

    private final Container real;
    private final Set<Integer> takenByViewer;
    private final Runnable onChange;

    public MaskedBoardContainer(Container real, Set<Integer> takenByViewer, Runnable onChange) {
        this.real = real;
        this.takenByViewer = takenByViewer;
        this.onChange = onChange;
    }

    @Override
    public int getContainerSize() {
        return real.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        // Must be a copy, not the live stack from the shared board container: vanilla click
        // handling (e.g. the number-key hotbar swap path) can hand this straight into a player's
        // inventory without ever going through removeItem(), which would alias the same
        // ItemStack instance between the board and the player's inventory.
        return takenByViewer.contains(slot) ? ItemStack.EMPTY : real.getItem(slot).copy();
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (takenByViewer.contains(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack visible = real.getItem(slot);
        if (visible.isEmpty()) {
            return ItemStack.EMPTY;
        }
        takenByViewer.add(slot);
        onChange.run();
        return visible.copy();
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 1);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            takenByViewer.add(slot);
            onChange.run();
        }
    }

    @Override
    public void setChanged() {
        onChange.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return real.stillValid(player);
    }

    @Override
    public void startOpen(Player player) {
        real.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        real.stopOpen(player);
    }

    @Override
    public void clearContent() {
    }
}
