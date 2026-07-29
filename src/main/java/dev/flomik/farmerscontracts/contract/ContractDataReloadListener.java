package dev.flomik.farmerscontracts.contract;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ContractDataReloadListener extends SimplePreparableReloadListener<ContractDataReloadListener.LoadedData> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public static final String POOLS_DIRECTORY = "contract_pool";
    public static final String CUSTOMERS_DIRECTORY = "customer";
    public static final String RECIPES_DIRECTORY = "recipes";

    record LoadedData(Map<ResourceLocation, JsonElement> pools, Map<ResourceLocation, JsonElement> customers,
                       Map<ResourceLocation, JsonElement> recipes) {
    }

    @Override
    protected LoadedData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> pools = new HashMap<>();
        Map<ResourceLocation, JsonElement> customers = new HashMap<>();
        Map<ResourceLocation, JsonElement> recipes = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(resourceManager, POOLS_DIRECTORY, GSON, pools);
        SimpleJsonResourceReloadListener.scanDirectory(resourceManager, CUSTOMERS_DIRECTORY, GSON, customers);
        SimpleJsonResourceReloadListener.scanDirectory(resourceManager, RECIPES_DIRECTORY, GSON, recipes);
        return new LoadedData(pools, customers, recipes);
    }

    @Override
    protected void apply(LoadedData data, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ContractPool> pools = decodeAll(data.pools(), ContractPool.CODEC, "contract pool");
        Map<ResourceLocation, Customer> customers = decodeAll(data.customers(), Customer.CODEC, "customer");
        RecipeCostSolver.reload(data.recipes());
        ContractContent.reload(pools, customers);
    }

    private static <T> Map<ResourceLocation, T> decodeAll(Map<ResourceLocation, JsonElement> raw, Codec<T> codec, String kind) {
        Map<ResourceLocation, T> result = new HashMap<>();
        raw.forEach((id, json) -> codec.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOGGER.error("Couldn't parse {} '{}': {}", kind, id, error))
                .ifPresent(value -> result.put(id, value)));
        return result;
    }
}
