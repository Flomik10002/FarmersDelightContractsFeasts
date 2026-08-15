package dev.flomik.farmerscontracts.box;

import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractContent;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import dev.flomik.farmerscontracts.item.ContractBoxItem;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

public class ContractBoxBlockEntity extends BaseContainerBlockEntity
        implements ExtendedScreenHandlerFactory<List<Item>>, SidedStorageBlockEntity {
    public static final int SLOTS = 5;

    public static final int MAX_STACK_SIZE = 64;
    private static final String SEALED_CONTRACT_KEY = "SealedContract";
    private static final Logger LOGGER = LogUtils.getLogger();

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private final ContractBoxItemStorage itemStorage = new ContractBoxItemStorage(this);
    @Nullable
    private GeneratedContract sealedContract;

    public ContractBoxBlockEntity(BlockPos pos, BlockState state) {
        super(FarmersContractsMod.CONTRACT_BOX_ENTITY, pos, state);
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
        return super.canOpen(player) && !isSealed();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !isSealed() && mayContain(stack, ContractContent.objectiveItems());
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

    @Override
    public List<Item> getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
        return List.copyOf(ContractContent.objectiveItems());
    }

    @Override
    public Storage<ItemVariant> getItemStorage(@Nullable Direction side) {
        return itemStorage;
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

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (sealedContract != null) {
            components.set(ContractDataComponents.CONTRACT_DATA, sealedContract);
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        sealedContract = input.get(ContractDataComponents.CONTRACT_DATA);
    }
}
