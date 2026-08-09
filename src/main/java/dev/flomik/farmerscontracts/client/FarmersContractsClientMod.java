package dev.flomik.farmerscontracts.client;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class FarmersContractsClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(FarmersContractsMod.CONTRACT_BOARD_MENU, ContractBoardScreen::new);
        MenuScreens.register(FarmersContractsMod.CONTRACT_BOX_MENU, ContractBoxScreen::new);
        ClientSetup.registerItemProperties();
    }
}
