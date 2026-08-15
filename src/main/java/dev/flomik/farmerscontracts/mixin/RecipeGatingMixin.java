package dev.flomik.farmerscontracts.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.flomik.farmerscontracts.condition.RecipeGating;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BooleanSupplier;

@Mixin(RecipeManager.class)
public abstract class RecipeGatingMixin {
    @Shadow
    private Multimap<RecipeType<?>, RecipeHolder<?>> byType;

    @Shadow
    private Map<ResourceLocation, RecipeHolder<?>> byName;

    @Inject(method = "apply", at = @At("TAIL"))
    private void farmerscontracts$gateConditionalRecipes(Map<ResourceLocation, JsonElement> raw, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        Logger logger = LogUtils.getLogger();
        for (Map.Entry<ResourceLocation, BooleanSupplier> gate : RecipeGating.gates().entrySet()) {
            ResourceLocation id = gate.getKey();
            if (gate.getValue().getAsBoolean() || !byName.containsKey(id)) {
                continue;
            }

            RecipeHolder<?> removed = byName.get(id);
            ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> nameBuilder = ImmutableMap.builder();
            byName.forEach((key, value) -> {
                if (!key.equals(id)) {
                    nameBuilder.put(key, value);
                }
            });
            byName = nameBuilder.build();

            ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> typeBuilder = ImmutableMultimap.builder();
            byType.entries().forEach(entry -> {
                if (entry.getValue() != removed) {
                    typeBuilder.put(entry);
                }
            });
            byType = typeBuilder.build();

            logger.debug("Skipping loading recipe {} as its config condition was not met", id);
        }
    }
}
