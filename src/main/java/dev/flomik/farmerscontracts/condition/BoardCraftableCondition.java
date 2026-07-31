package dev.flomik.farmerscontracts.condition;

import com.mojang.serialization.MapCodec;
import dev.flomik.farmerscontracts.Config;
import net.neoforged.neoforge.common.conditions.ICondition;

// Data-driven gate for the Contract Board recipe: if the board is configured as unbreakable
// (Config.boardCanBreak() == false, ported from Bountiful's board.canBreak - see
// docs/board-lifecycle-audit.md), it must not be craftable either, or a server could end up
// littered with permanent, indestructible boards.
public record BoardCraftableCondition() implements ICondition {

    public static final MapCodec<BoardCraftableCondition> CODEC = MapCodec.unit(BoardCraftableCondition::new);

    @Override
    public boolean test(IContext context) {
        return Config.boardCanBreak();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "farmerscontracts:board_craftable";
    }
}
