package dev.flomik.farmerscontracts.condition;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

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
