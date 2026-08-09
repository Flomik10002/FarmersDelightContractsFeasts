package dev.flomik.farmerscontracts.box;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Same 5-slot single-row layout as vanilla's HopperMenu - one slot per order line
// (ContractBoxBlockEntity.SLOTS), so it reuses the vanilla hopper GUI texture too.
public class ContractBoxMenu extends AbstractContainerMenu {

    private final Container container;

    public ContractBoxMenu(int containerId, Inventory playerInventory, Container container) {
        super(FarmersContractsMod.CONTRACT_BOX_MENU.get(), containerId);
        checkContainerSize(container, ContractBoxBlockEntity.SLOTS);
        this.container = container;
        container.startOpen(playerInventory.player);

        for (int i = 0; i < ContractBoxBlockEntity.SLOTS; i++) {
            this.addSlot(new BoxSlot(container, i, 44 + i * 18, 20));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, row * 18 + 51));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 109));
        }
    }

    // Root cause of the "count silently drops to 1 for a moment on a direct click merge" visual
    // bug: this no-arg constructor is what the CLIENT actually uses. Opening a menu client-side
    // never wraps the real ContractBoxBlockEntity (that only exists on the server) - the client
    // builds this bare fallback container purely to run its own LOCAL PREDICTION of the click
    // before the server's authoritative correction arrives
    // (MultiPlayerGameMode#handleInventoryMouseClick calls menu.clicked(...) locally, then the
    // server does the same and reconciles via ServerGamePacketListenerImpl#handleContainerClick).
    // BoxSlot#getMaxStackSize(ItemStack) already forces 64 for the GROW arithmetic inside
    // Slot#safeInsert, but Slot#set()/setByPlayer() finishes by calling
    // container.setItem(slot, stack), and vanilla's Container#setItem calls
    // stack.limitSize(this.getMaxStackSize(stack)) - the CONTAINER's own getMaxStackSize(ItemStack).
    // A plain SimpleContainer doesn't override that (Container's default is
    // Math.min(64, stack.getMaxStackSize()), i.e. 1 for Cake), so the client's own predicted merge
    // gets silently truncated back to 1 right after computing the correct total, while the real
    // server-side ContractBoxBlockEntity (which DOES override it) computes the true value - the
    // client shows the wrong number for one round-trip until the server's correction packet
    // arrives. Using the same override here keeps client prediction and server truth in sync.
    public ContractBoxMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new PredictionSizedContainer(ContractBoxBlockEntity.SLOTS));
    }

    private static class PredictionSizedContainer extends SimpleContainer {
        PredictionSizedContainer(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return ContractBoxBlockEntity.MAX_STACK_SIZE;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = slot.getItem();
        ItemStack result = moving.copy();
        int boxSlots = ContractBoxBlockEntity.SLOTS;

        if (index < boxSlots) {
            if (!this.moveItemStackTo(moving, boxSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoBox(moving)) {
            return ItemStack.EMPTY;
        }

        if (moving.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    // Vanilla's moveItemStackTo() skips its own "merge into an already-occupied matching slot"
    // phase entirely whenever ItemStack#isStackable() is false (true for anything with
    // maxStackSize 1) - it falls straight to "only place into an empty slot", so shift-clicking
    // several non-stackable items one at a time would otherwise burn a fresh box slot per item
    // instead of piling into the one slot BoxSlot#getMaxStackSize now allows. This redoes that
    // merge phase without the isStackable() gate, for the into-box direction only.
    private boolean moveIntoBox(ItemStack moving) {
        boolean changed = false;
        for (int i = 0; i < ContractBoxBlockEntity.SLOTS && !moving.isEmpty(); i++) {
            Slot slot = this.slots.get(i);
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, moving)) {
                int room = slot.getMaxStackSize(existing) - existing.getCount();
                if (room > 0) {
                    int move = Math.min(room, moving.getCount());
                    existing.grow(move);
                    moving.shrink(move);
                    slot.setChanged();
                    changed = true;
                }
            }
        }
        for (int i = 0; i < ContractBoxBlockEntity.SLOTS && !moving.isEmpty(); i++) {
            Slot slot = this.slots.get(i);
            if (slot.getItem().isEmpty() && slot.mayPlace(moving)) {
                int max = slot.getMaxStackSize(moving);
                slot.setByPlayer(moving.split(Math.min(moving.getCount(), max)));
                slot.setChanged();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    // Lets a single box slot hold more of an item than that item's own max stack size allows -
    // needed so non-stackable objectives (Cake, Suspicious Stew, whole roasted dishes placed as
    // blocks - all maxStackSize 1) don't need one slot per unit (ContractGenerator already caps
    // how many units a line can ask for, but even that capped amount can exceed 1). Vanilla's
    // container-level Container#getMaxStackSize() isn't enough on its own: both
    // AbstractContainerMenu#clicked and #moveItemStackTo call Slot#getMaxStackSize(ItemStack),
    // which independently re-clamps to Math.min(slot max, stack's own max) - so the override has
    // to live on the Slot itself too (see ContractBoxBlockEntity#getMaxStackSize for the other half).
    private static class BoxSlot extends Slot {
        BoxSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return ContractBoxBlockEntity.MAX_STACK_SIZE;
        }
    }
}
