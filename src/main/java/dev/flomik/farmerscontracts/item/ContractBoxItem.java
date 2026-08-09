package dev.flomik.farmerscontracts.item;

import dev.flomik.farmerscontracts.client.ClientSetup;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class ContractBoxItem extends BlockItem {

    public ContractBoxItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        GeneratedContract data = stack.get(ContractDataComponents.CONTRACT_DATA);
        if (data == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.farmerscontracts.contract_box.named", ContractTicketItem.customerName(data))
                .withStyle(data.rarity().style());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GeneratedContract data = stack.get(ContractDataComponents.CONTRACT_DATA);
        if (data == null) {
            return;
        }
        // A sealed box's contents are guaranteed to exactly match the order (see
        // ContractBoxBlock.trySeal) - its tooltip is the ticket's tooltip with every line already
        // shown as fulfilled (N/N), not recomputed from anything.
        ContractTooltips.appendContractTooltip(data, ClientSetup.currentPlayer(), tooltip, true);
    }
}
