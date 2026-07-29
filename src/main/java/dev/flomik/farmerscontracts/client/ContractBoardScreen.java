package dev.flomik.farmerscontracts.client;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.board.ContractBoardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ContractBoardScreen extends AbstractContainerScreen<ContractBoardMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(FarmersContractsMod.MODID, "textures/gui/contract_board.png");

    public ContractBoardScreen(ContractBoardMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        int rows = menu.getRowCount();
        this.imageWidth = 176;
        this.imageHeight = (rows * 18 + 17) + 96;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }
}
