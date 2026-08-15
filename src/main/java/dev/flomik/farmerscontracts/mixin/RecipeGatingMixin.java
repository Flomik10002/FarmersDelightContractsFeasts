package dev.flomik.farmerscontracts.mixin;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.condition.RecipeGating;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

@Mixin(RecipeManager.class)
public abstract class RecipeGatingMixin {
    @Shadow
    private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes;

    @Shadow
    private Map<ResourceLocation, Recipe<?>> byName;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void farmerscontracts$gateConditionalRecipes(Map<ResourceLocation, JsonElement> raw, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        Logger logger = LogUtils.getLogger();
        for (Map.Entry<ResourceLocation, BooleanSupplier> gate : RecipeGating.gates().entrySet()) {
            ResourceLocation id = gate.getKey();
            Recipe<?> removed = byName.get(id);
            if (gate.getValue().getAsBoolean() || removed == null) {
                continue;
            }

            Map<ResourceLocation, Recipe<?>> newByName = new HashMap<>(byName);
            newByName.remove(id);
            byName = newByName;

            Map<ResourceLocation, Recipe<?>> byType = recipes.get(removed.getType());
            if (byType != null) {
                Map<ResourceLocation, Recipe<?>> newByType = new HashMap<>(byType);
                newByType.remove(id);
                Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> newRecipes = new HashMap<>(recipes);
                newRecipes.put(removed.getType(), newByType);
                recipes = newRecipes;
            }

            logger.debug("Skipping loading recipe {} as its config condition was not met", id);
        }
    }
}
