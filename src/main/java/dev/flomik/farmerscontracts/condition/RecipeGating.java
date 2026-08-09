package dev.flomik.farmerscontracts.condition;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Fabric replacement for the NeoForge branch's {@code neoforge:conditions}-gated recipes
 * (BoardCraftableCondition/BoxEnabledCondition) - NeoForge patches datapack-level recipe
 * conditions onto vanilla's {@code RecipeManager}, which Fabric has no equivalent of. Instead,
 * a recipe ID is registered here with a {@link BooleanSupplier} gate, and
 * {@code mixin.RecipeGatingMixin} strips it out of the loaded recipe manager (post-load) whenever
 * the gate is false - same net effect (the recipe simply doesn't exist), no datapack changes
 * needed on the JSON side.
 */
public final class RecipeGating {

    private static final Map<ResourceLocation, BooleanSupplier> GATES = new LinkedHashMap<>();

    private RecipeGating() {
    }

    public static void register(ResourceLocation recipeId, BooleanSupplier enabled) {
        GATES.put(recipeId, enabled);
    }

    public static Map<ResourceLocation, BooleanSupplier> gates() {
        return GATES;
    }
}
