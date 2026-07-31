package dev.flomik.farmerscontracts.condition;

import com.google.gson.JsonObject;
import dev.flomik.farmerscontracts.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

// Data-driven gate for the Contract Box recipe: in TICKET_ONLY delivery mode the box can't be
// crafted at all (see Config.DeliveryMode, docs/contract_box.md).
public class BoxEnabledCondition implements ICondition {

    private static final ResourceLocation NAME = new ResourceLocation("farmerscontracts", "box_enabled");

    @Override
    public ResourceLocation getID() {
        return NAME;
    }

    @Override
    public boolean test(IContext context) {
        return Config.deliveryMode() != Config.DeliveryMode.TICKET_ONLY;
    }

    @Override
    public String toString() {
        return "farmerscontracts:box_enabled";
    }

    public static class Serializer implements IConditionSerializer<BoxEnabledCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, BoxEnabledCondition value) {
        }

        @Override
        public BoxEnabledCondition read(JsonObject json) {
            return new BoxEnabledCondition();
        }

        @Override
        public ResourceLocation getID() {
            return NAME;
        }
    }
}
