package dev.flomik.farmerscontracts.item;

import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ContractTicketItem extends Item {

    public ContractTicketItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    public static GeneratedContract dataOf(ItemStack stack) {
        return stack.get(ContractDataComponents.CONTRACT_DATA.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        GeneratedContract data = dataOf(stack);
        if (data == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.farmerscontracts.contract_ticket.named", customerName(data))
                .withStyle(data.rarity().style());
    }

    public static Component customerName(GeneratedContract data) {
        String key = "customer.farmerscontracts." + data.customerId().getPath();
        return Component.translatableWithFallback(key, data.customerName());
    }
}
