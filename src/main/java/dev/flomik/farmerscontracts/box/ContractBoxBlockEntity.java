package dev.flomik.farmerscontracts.box;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ContractBoxBlockEntity extends BlockEntity implements MenuProvider {

    // Matches the highest possible objective-line count across all customers (Feast: 2-5 lines,
    // see data/farmerscontracts/customer/feast.json) - one slot per order line, nothing spare.
    public static final int SLOTS = 5;
    // A box slot may hold more of an item than that item's own max stack size allows - needed so
    // a non-stackable objective (Cake, Suspicious Stew, whole roasted dishes placed as blocks -
    // all maxStackSize 1) still fits in one slot instead of one per unit. SimpleContainer#setItem
    // only clamps against getMaxStackSize() (the no-arg, container-level one, default 99) rather
    // than the item's own limit, so overriding just that here is enough on this container's own
    // insert path - Slot#getMaxStackSize(ItemStack) is a separate clamp used by the menu/click
    // handling, fixed via ContractBoxMenu's own BoxSlot override.
    public static final int MAX_STACK_SIZE = 64;
    private static final String SEALED_CONTRACT_KEY = "SealedContract";

    private final SimpleContainer container = new BoxContainer(SLOTS);
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

    public boolean isSealed() {
        return getBlockState().getValue(ContractBoxBlock.SEALED);
    }

    @Nullable
    public GeneratedContract sealedContract() {
        return sealedContract;
    }

    public void seal(GeneratedContract contract) {
        this.sealedContract = contract;
        setChanged();
    }

    // Reads the contract data a broken-and-picked-up sealed box carries in its BlockEntityTag,
    // without needing a live block entity - used by ContractBoardBlock.tryDeliverBox.
    @Nullable
    public static GeneratedContract sealedContractOf(ItemStack boxItem) {
        CompoundTag blockEntityTag = boxItem.getTagElement("BlockEntityTag");
        if (blockEntityTag == null || !blockEntityTag.contains(SEALED_CONTRACT_KEY)) {
            return null;
        }
        return GeneratedContract.fromNbt(blockEntityTag.getCompound(SEALED_CONTRACT_KEY));
    }

    // Feeds AbstractContainerBlockEntity#saveWithoutMetadata (called by BlockItem when a placed
    // box is broken) so a sealed box, once dropped as an item and re-placed, comes back with both
    // its contents and its contract data intact - the pre-components equivalent of the
    // collectImplicitComponents/applyImplicitComponents pair used on the NeoForge 1.21.1 side.
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

    private static class BoxContainer extends SimpleContainer {
        BoxContainer(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize() {
            return MAX_STACK_SIZE;
        }
    }
}
