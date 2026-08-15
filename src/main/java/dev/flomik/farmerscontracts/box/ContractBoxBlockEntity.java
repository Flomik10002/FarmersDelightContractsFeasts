package dev.flomik.farmerscontracts.box;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractContent;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ContractBoxBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 5;

    public static final int MAX_STACK_SIZE = 64;
    private static final String SEALED_CONTRACT_KEY = "SealedContract";

    private final SimpleContainer container = new BoxContainer(SLOTS);

    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new ContractBoxItemHandler(this));
    @Nullable
    private GeneratedContract sealedContract;

    public ContractBoxBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersContractsMod.CONTRACT_BOX_ENTITY.get(), pos, state);
    }

    public SimpleContainer container() {
        return container;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.farmerscontracts.contract_box");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContractBoxMenu(containerId, playerInventory, container);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandler = LazyOptional.of(() -> new ContractBoxItemHandler(this));
    }

    public boolean isSealed() {
        return getBlockState().getValue(ContractBoxBlock.SEALED);
    }

    public static boolean mayContain(ItemStack stack, Set<Item> allowedItems) {
        if (stack.isEmpty()) {
            return true;
        }

        if (stack.getItem() instanceof ContractBoxItem) {
            return false;
        }
        return allowedItems.contains(stack.getItem());
    }

    @Nullable
    public GeneratedContract sealedContract() {
        return sealedContract;
    }

    public void seal(GeneratedContract contract) {
        this.sealedContract = contract;
        setChanged();
    }

    @Nullable
    public static GeneratedContract sealedContractOf(ItemStack boxItem) {
        CompoundTag blockEntityTag = boxItem.getTagElement("BlockEntityTag");
        if (blockEntityTag == null || !blockEntityTag.contains(SEALED_CONTRACT_KEY)) {
            return null;
        }
        return GeneratedContract.fromNbt(blockEntityTag.getCompound(SEALED_CONTRACT_KEY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", container.createTag());
        if (sealedContract != null) {
            tag.put(SEALED_CONTRACT_KEY, sealedContract.toNbt());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        container.fromTag(tag.getList("Items", 10));
        sealedContract = tag.contains(SEALED_CONTRACT_KEY)
                ? GeneratedContract.fromNbt(tag.getCompound(SEALED_CONTRACT_KEY))
                : null;
    }

    private class BoxContainer extends SimpleContainer {
        BoxContainer(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize() {
            return MAX_STACK_SIZE;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !isSealed() && mayContain(stack, ContractContent.objectiveItems());
        }
    }
}
