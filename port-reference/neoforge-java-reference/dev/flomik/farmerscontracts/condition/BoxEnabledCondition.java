package dev.flomik.farmerscontracts.condition;

import com.mojang.serialization.MapCodec;
import dev.flomik.farmerscontracts.Config;
import net.neoforged.neoforge.common.conditions.ICondition;

// Data-driven gate for the Contract Box recipe: in TICKET_ONLY delivery mode the box can't be
// crafted at all (see Config.DeliveryMode, docs/contract_box.md).
public record BoxEnabledCondition() implements ICondition {

    public static final MapCodec<BoxEnabledCondition> CODEC = MapCodec.unit(BoxEnabledCondition::new);

    @Override
    public boolean test(IContext context) {
        return Config.deliveryMode() != Config.DeliveryMode.TICKET_ONLY;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "farmerscontracts:box_enabled";
    }
}
