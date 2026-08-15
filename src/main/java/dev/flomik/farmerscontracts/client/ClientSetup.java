package dev.flomik.farmerscontracts.client;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.box.ContractBoxBlockEntity;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void registerItemProperties() {
        ItemProperties.register(
                FarmersContractsMod.CONTRACT_BOX_ITEM.get(),
                new ResourceLocation(FarmersContractsMod.MODID, "sealed"),
                (stack, level, entity, seed) -> ContractBoxBlockEntity.sealedContractOf(stack) != null ? 1.0F : 0.0F);
    }
}
