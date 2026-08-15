package dev.flomik.farmerscontracts.client;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void registerItemProperties() {
        ItemProperties.register(
                FarmersContractsMod.CONTRACT_BOX_ITEM,
                ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, "sealed"),
                (stack, level, entity, seed) -> stack.has(ContractDataComponents.CONTRACT_DATA) ? 1.0F : 0.0F);
    }

    public static Player currentPlayer() {
        return Minecraft.getInstance().player;
    }
}
