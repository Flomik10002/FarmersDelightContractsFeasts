package dev.flomik.farmerscontracts.condition;

import com.google.gson.JsonObject;
import dev.flomik.farmerscontracts.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

// Data-driven gate for the Contract Board recipe: if the board is configured as unbreakable
// (Config.boardCanBreak() == false, ported from Bountiful's board.canBreak - see
// docs/board-lifecycle-audit.md), it must not be craftable either, or a server could end up
// littered with permanent, indestructible boards.
public class BoardCraftableCondition implements ICondition {

    private static final ResourceLocation NAME = new ResourceLocation("farmerscontracts", "board_craftable");

    @Override
    public ResourceLocation getID() {
        return NAME;
    }

    @Override
    public boolean test(IContext context) {
        return Config.boardCanBreak();
    }

    @Override
    public String toString() {
        return "farmerscontracts:board_craftable";
    }

    public static class Serializer implements IConditionSerializer<BoardCraftableCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, BoardCraftableCondition value) {
        }

        @Override
        public BoardCraftableCondition read(JsonObject json) {
            return new BoardCraftableCondition();
        }

        @Override
        public ResourceLocation getID() {
            return NAME;
        }
    }
}
