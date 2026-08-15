package dev.flomik.farmerscontracts.condition;

import com.mojang.serialization.MapCodec;
import dev.flomik.farmerscontracts.Config;
import net.neoforged.neoforge.common.conditions.ICondition;

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
