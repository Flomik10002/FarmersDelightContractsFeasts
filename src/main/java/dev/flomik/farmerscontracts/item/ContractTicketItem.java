package dev.flomik.farmerscontracts.item;

import dev.flomik.farmerscontracts.contract.GeneratedContract;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ContractTicketItem extends Item {

    private static final String CONTRACT_KEY = "Contract";

    public ContractTicketItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    public static GeneratedContract dataOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(CONTRACT_KEY)) {
            return null;
        }
        return GeneratedContract.fromNbt(tag.getCompound(CONTRACT_KEY));
    }

    public static void setData(ItemStack stack, GeneratedContract data) {
        stack.getOrCreateTag().put(CONTRACT_KEY, data.toNbt());
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
        return Component.translatable(key);
    }
}
