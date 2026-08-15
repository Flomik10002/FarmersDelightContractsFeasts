package dev.flomik.farmerscontracts.box;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class ContractBoxMenu extends AbstractContainerMenu {
    private final Container container;

    public ContractBoxMenu(int containerId, Inventory playerInventory, Container container) {
        this(containerId, playerInventory, container,
                stack -> ContractBoxBlockEntity.mayContain(stack, ContractContent.objectiveItems()));
    }

    private ContractBoxMenu(int containerId, Inventory playerInventory, Container container, Predicate<ItemStack> insertFilter) {
        super(FarmersContractsMod.CONTRACT_BOX_MENU.get(), containerId);
        checkContainerSize(container, ContractBoxBlockEntity.SLOTS);
        this.container = container;
        container.startOpen(playerInventory.player);

        for (int i = 0; i < ContractBoxBlockEntity.SLOTS; i++) {
            this.addSlot(new BoxSlot(container, i, 44 + i * 18, 20, insertFilter));
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

    public ContractBoxMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, (FriendlyByteBuf) null);
    }

    public ContractBoxMenu(int containerId, Inventory playerInventory, @Nullable FriendlyByteBuf data) {
        this(containerId, playerInventory, new SimpleContainer(ContractBoxBlockEntity.SLOTS), readInsertFilter(data));
    }

    public static void writeAllowedItems(FriendlyByteBuf buffer) {
        buffer.writeCollection(ContractContent.objectiveItems(),
                (buf, item) -> buf.writeVarInt(BuiltInRegistries.ITEM.getId(item)));
    }

    private static Predicate<ItemStack> readInsertFilter(@Nullable FriendlyByteBuf data) {
        if (data == null) {
            return stack -> true;
        }
        Set<Item> allowedItems = new HashSet<>(data.readList(buf -> BuiltInRegistries.ITEM.byId(buf.readVarInt())));
        return stack -> ContractBoxBlockEntity.mayContain(stack, allowedItems);
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

    private boolean moveIntoBox(ItemStack moving) {
        boolean changed = false;
        for (int i = 0; i < ContractBoxBlockEntity.SLOTS && !moving.isEmpty(); i++) {
            Slot slot = this.slots.get(i);
            ItemStack existing = slot.getItem();

            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, moving) && slot.mayPlace(moving)) {
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

    private static class BoxSlot extends Slot {
        private final Predicate<ItemStack> insertFilter;

        BoxSlot(Container container, int index, int x, int y, Predicate<ItemStack> insertFilter) {
            super(container, index, x, y);
            this.insertFilter = insertFilter;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return ContractBoxBlockEntity.MAX_STACK_SIZE;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return insertFilter.test(stack);
        }
    }
}
