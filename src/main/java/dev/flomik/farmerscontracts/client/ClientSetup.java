package dev.flomik.farmerscontracts.client;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void registerItemProperties() {
        ItemProperties.register(
                FarmersContractsMod.CONTRACT_BOX_ITEM.get(),
                ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, "sealed"),
                (stack, level, entity, seed) -> stack.has(ContractDataComponents.CONTRACT_DATA.get()) ? 1.0F : 0.0F);
    }
}
