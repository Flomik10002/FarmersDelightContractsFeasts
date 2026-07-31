package dev.flomik.farmerscontracts.item;

import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class ContractBoxItem extends BlockItem {

    public ContractBoxItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        GeneratedContract data = stack.get(ContractDataComponents.CONTRACT_DATA.get());
        if (data == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.farmerscontracts.contract_box.named", ContractTicketItem.customerName(data))
                .withStyle(data.rarity().style());
    }
}
