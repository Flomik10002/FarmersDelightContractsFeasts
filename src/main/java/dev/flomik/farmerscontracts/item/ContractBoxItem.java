package dev.flomik.farmerscontracts.item;

import dev.flomik.farmerscontracts.box.ContractBoxBlockEntity;
import dev.flomik.farmerscontracts.client.ClientSetup;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class ContractBoxItem extends BlockItem {
    public ContractBoxItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    public static boolean isFilledContractBox(ItemStack stack) {
        if (!(stack.getItem() instanceof ContractBoxItem)) {
            return false;
        }
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        return blockEntityTag != null && !blockEntityTag.getList("Items", Tag.TAG_COMPOUND).isEmpty();
    }

    @Override
    public Component getName(ItemStack stack) {
        GeneratedContract data = ContractBoxBlockEntity.sealedContractOf(stack);
        if (data == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.farmerscontracts.contract_box.named", ContractTicketItem.customerName(data))
                .withStyle(data.rarity().style());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        GeneratedContract data = ContractBoxBlockEntity.sealedContractOf(stack);
        if (data == null) {
            return;
        }

        ContractTooltips.appendContractTooltip(data, ClientSetup.currentPlayer(), tooltip, true);
    }
}
