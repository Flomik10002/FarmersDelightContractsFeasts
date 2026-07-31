package dev.flomik.farmerscontracts.box;

import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ContractBoxBlockEntity extends BaseContainerBlockEntity {

    // Matches the highest possible objective-line count across all customers (Feast: 2-5 lines,
    // see data/farmerscontracts/customer/feast.json) - one slot per order line, nothing spare.
    public static final int SLOTS = 5;
    // A box slot may hold more of an item than that item's own max stack size allows - needed so
    // a non-stackable objective (Cake, Suspicious Stew, whole roasted dishes placed as blocks -
    // all maxStackSize 1) still fits in one slot instead of one per unit. Container#setItem()
    // calls stack.limitSize(getMaxStackSize(stack)) unconditionally, so this has to be overridden
    // here (not just on ContractBoxMenu's slots) or every insert would immediately truncate back
    // down to the item's own limit.
    public static final int MAX_STACK_SIZE = 64;
    private static final String SEALED_CONTRACT_KEY = "SealedContract";
    private static final Logger LOGGER = LogUtils.getLogger();

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    @Nullable
    private GeneratedContract sealedContract;

    public ContractBoxBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersContractsMod.CONTRACT_BOX_ENTITY.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return MAX_STACK_SIZE;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.farmerscontracts.contract_box");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new ContractBoxMenu(containerId, playerInventory, this);
    }

    @Override
    public boolean canOpen(Player player) {
        // A sealed box has nothing left to edit - its contents are locked until it's delivered
        // (or, in the current design, never unlocked at all - see docs/contract_box.md).
        return super.canOpen(player) && !isSealed();
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, false, registries);
        if (sealedContract != null) {
            GeneratedContract.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), sealedContract)
                    .resultOrPartial(LOGGER::error)
                    .ifPresent(encoded -> tag.put(SEALED_CONTRACT_KEY, encoded));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);

        sealedContract = null;
        if (tag.contains(SEALED_CONTRACT_KEY)) {
            GeneratedContract.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get(SEALED_CONTRACT_KEY))
                    .resultOrPartial(LOGGER::error)
                    .ifPresent(contract -> sealedContract = contract);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(SEALED_CONTRACT_KEY);
    }

    // Mirrors DataComponents.CONTAINER handled by the superclass: lets a broken sealed box carry
    // both its contents and its contract data as an item (and restores both on re-placement) -
    // see AbstractPresentBlock in Supplementaries, docs/supplementaries-present-audit.md §5.
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (sealedContract != null) {
            components.set(ContractDataComponents.CONTRACT_DATA.get(), sealedContract);
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        sealedContract = input.get(ContractDataComponents.CONTRACT_DATA.get());
    }
}
