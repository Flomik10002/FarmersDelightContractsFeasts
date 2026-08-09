package dev.flomik.farmerscontracts.client;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

// Client-only API (Minecraft, ClientLevel via ItemPropertyFunction, etc.) is kept isolated in
// this class - loaded only from the "client" entrypoint (FarmersContractsClientMod), never on a
// dedicated server.
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void registerItemProperties() {
        ItemProperties.register(
                FarmersContractsMod.CONTRACT_BOX_ITEM,
                ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, "sealed"),
                (stack, level, entity, seed) -> stack.has(ContractDataComponents.CONTRACT_DATA) ? 1.0F : 0.0F);
    }

    // Contract Ticket/Box tooltips (ContractTooltips) show a live "have X of Y" progress count,
    // which needs the viewing player - appendHoverText itself never receives one (unlike
    // NeoForge's ItemTooltipEvent). appendHoverText only ever runs client-side during GUI
    // rendering, so reaching for the client player here is safe; kept in this client-only class
    // so the item classes themselves (loaded on both sides) never reference Minecraft directly.
    public static Player currentPlayer() {
        return Minecraft.getInstance().player;
    }
}
