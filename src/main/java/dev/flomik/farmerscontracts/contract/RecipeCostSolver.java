package dev.flomik.farmerscontracts.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RecipeCostSolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double STAGE_MARKUP = 1.15;

    private static final double FALLBACK_COST = 1.0;

    private static final double UNKNOWN_FOOD_BASE = 0.88;
    private static final double UNKNOWN_FOOD_NUTRITION_FACTOR = 0.14;
    private static final double UNKNOWN_FOOD_PROTEIN_BONUS = 1.35;
    private static final TagKey<Item> RAW_MEAT_TAG =
            TagKey.create(Registries.ITEM, new ResourceLocation("forge", "raw_meat"));
    private static final TagKey<Item> RAW_FISH_TAG =
            TagKey.create(Registries.ITEM, new ResourceLocation("forge", "raw_fishes"));

    private static final Map<String, Double> BASE_COSTS = Map.ofEntries(
            Map.entry("minecraft:wheat", 1.0),
            Map.entry("minecraft:egg", 1.0),
            Map.entry("minecraft:sugar", 1.0),
            Map.entry("minecraft:apple", 1.0),
            Map.entry("minecraft:milk_bucket", 3.0),
            Map.entry("minecraft:beef", 3.0),
            Map.entry("minecraft:chicken", 3.0),
            Map.entry("minecraft:mutton", 3.0),
            Map.entry("minecraft:porkchop", 3.0),
            Map.entry("minecraft:rabbit", 2.0),
            Map.entry("minecraft:cod", 2.0),
            Map.entry("minecraft:salmon", 2.0),
            Map.entry("minecraft:potato", 1.0),
            Map.entry("minecraft:carrot", 1.0),
            Map.entry("minecraft:beetroot", 1.0),
            Map.entry("minecraft:pumpkin", 2.0),
            Map.entry("minecraft:melon_slice", 1.0),
            Map.entry("minecraft:cocoa_beans", 1.0),
            Map.entry("minecraft:sweet_berries", 1.0),
            Map.entry("minecraft:glow_berries", 2.0),
            Map.entry("minecraft:kelp", 1.0),
            Map.entry("minecraft:honey_bottle", 2.0),
            Map.entry("minecraft:bone", 1.0),
            Map.entry("minecraft:nether_wart", 1.0),
            Map.entry("minecraft:cactus", 1.0),
            Map.entry("minecraft:melon", 2.0),
            Map.entry("minecraft:sugar_cane", 1.0),
            Map.entry("farmersdelight:cabbage", 1.0),
            Map.entry("farmersdelight:onion", 1.0),
            Map.entry("farmersdelight:tomato", 1.0),
            Map.entry("farmersdelight:rice", 1.0),
            Map.entry("farmersdelight:straw", 1.0),
            Map.entry("farmersdelight:tree_bark", 1.0)
    );

    private record ParsedOutput(ResourceLocation resultId, int count, List<JsonElement> ingredients) {
    }

    private static Map<ResourceLocation, List<ParsedOutput>> recipesByResult = Map.of();
    private static final Map<ResourceLocation, Double> cache = new HashMap<>();

    private RecipeCostSolver() {
    }

    public static void reload(Map<ResourceLocation, JsonElement> rawRecipes) {
        Map<ResourceLocation, List<ParsedOutput>> byResult = new HashMap<>();
        for (JsonElement raw : rawRecipes.values()) {
            for (ParsedOutput output : parse(raw)) {
                byResult.computeIfAbsent(output.resultId(), key -> new ArrayList<>()).add(output);
            }
        }
        recipesByResult = byResult;
        cache.clear();
        LOGGER.info("RecipeCostSolver indexed {} recipe outputs", recipesByResult.size());
    }

    public static double costOf(Item item) {
        return costOf(BuiltInRegistries.ITEM.getKey(item));
    }

    public static double costOf(ResourceLocation itemId) {
        return solve(itemId, new HashSet<>());
    }

    private static double solve(ResourceLocation itemId, Set<ResourceLocation> visiting) {
        Double cached = cache.get(itemId);
        if (cached != null) {
            return cached;
        }

        Double base = BASE_COSTS.get(itemId.toString());
        if (base != null) {
            cache.put(itemId, base);
            return base;
        }

        if (!visiting.add(itemId)) {
            return unknownItemCost(itemId);
        }

        double best = Double.MAX_VALUE;
        for (ParsedOutput recipe : recipesByResult.getOrDefault(itemId, List.of())) {
            double ingredientsCost = 0.0;
            for (JsonElement ingredient : recipe.ingredients()) {
                ingredientsCost += cheapestFor(ingredient, visiting);
            }
            double perUnit = (ingredientsCost * STAGE_MARKUP) / Math.max(recipe.count(), 1);
            best = Math.min(best, perUnit);
        }

        if (best == Double.MAX_VALUE) {
            List<ParsedOutput> blockRecipes = recipesByResult.get(itemId.withSuffix("_block"));
            if (blockRecipes != null && !anyReferencesItem(blockRecipes, itemId)) {
                best = solve(itemId.withSuffix("_block"), visiting);
            }
        }

        visiting.remove(itemId);

        double result = best == Double.MAX_VALUE ? unknownItemCost(itemId) : best;
        cache.put(itemId, result);
        return result;
    }

    private static double unknownItemCost(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        FoodProperties food = item.getFoodProperties(new ItemStack(item), null);
        if (food == null) {
            return FALLBACK_COST;
        }
        boolean isProtein = BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, itemId))
                .map(holder -> holder.is(RAW_MEAT_TAG) || holder.is(RAW_FISH_TAG))
                .orElse(false);
        double estimate = UNKNOWN_FOOD_BASE
                + UNKNOWN_FOOD_NUTRITION_FACTOR * food.getNutrition()
                + (isProtein ? UNKNOWN_FOOD_PROTEIN_BONUS : 0.0);
        return Math.max(FALLBACK_COST, estimate);
    }

    private static boolean anyReferencesItem(List<ParsedOutput> recipes, ResourceLocation itemId) {
        for (ParsedOutput recipe : recipes) {
            for (JsonElement ingredient : recipe.ingredients()) {
                if (referencesItem(ingredient, itemId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean referencesItem(JsonElement ingredient, ResourceLocation itemId) {
        if (ingredient.isJsonArray()) {
            for (JsonElement alt : ingredient.getAsJsonArray()) {
                if (referencesItem(alt, itemId)) {
                    return true;
                }
            }
            return false;
        }
        if (!ingredient.isJsonObject()) {
            return false;
        }
        JsonObject obj = ingredient.getAsJsonObject();
        if (obj.has("item")) {
            return new ResourceLocation(obj.get("item").getAsString()).equals(itemId);
        }
        if (obj.has("tag")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(obj.get("tag").getAsString()));
            return BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, itemId)).map(holder -> holder.is(tag)).orElse(false);
        }
        return false;
    }

    private static double cheapestFor(JsonElement ingredient, Set<ResourceLocation> visiting) {
        if (ingredient.isJsonArray()) {
            double best = Double.MAX_VALUE;
            for (JsonElement alt : ingredient.getAsJsonArray()) {
                best = Math.min(best, cheapestFor(alt, visiting));
            }
            return best == Double.MAX_VALUE ? FALLBACK_COST : best;
        }

        if (!ingredient.isJsonObject()) {
            return FALLBACK_COST;
        }

        JsonObject obj = ingredient.getAsJsonObject();
        if (obj.has("item")) {
            return solve(new ResourceLocation(obj.get("item").getAsString()), visiting);
        }
        if (obj.has("tag")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(obj.get("tag").getAsString()));
            double best = Double.MAX_VALUE;
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                best = Math.min(best, solve(BuiltInRegistries.ITEM.getKey(holder.value()), visiting));
            }
            return best == Double.MAX_VALUE ? FALLBACK_COST : best;
        }
        return FALLBACK_COST;
    }

    private static List<ParsedOutput> parse(JsonElement rawJson) {
        if (!rawJson.isJsonObject()) {
            return List.of();
        }
        JsonObject json = rawJson.getAsJsonObject();
        String type = json.has("type") ? json.get("type").getAsString() : "";

        return switch (type) {
            case "minecraft:crafting_shapeless", "farmersdelight:cooking", "farmersdelight:dough" -> parseFlat(json);
            case "minecraft:crafting_shaped" -> parseShaped(json);
            case "farmersdelight:cutting" -> parseCutting(json);
            case "minecraft:smelting", "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:blasting" ->
                    parseSingleIngredient(json);
            case "forge:conditional" -> parseConditional(json);
            default -> List.of();
        };
    }

    private static List<ParsedOutput> parseConditional(JsonObject json) {
        if (!json.has("recipes")) {
            return List.of();
        }
        List<ParsedOutput> outputs = new ArrayList<>();
        for (JsonElement entry : json.getAsJsonArray("recipes")) {
            if (entry.isJsonObject() && entry.getAsJsonObject().has("recipe")) {
                outputs.addAll(parse(entry.getAsJsonObject().get("recipe")));
            }
        }
        return outputs;
    }

    private static List<ParsedOutput> parseFlat(JsonObject json) {
        if (!json.has("ingredients") || !json.has("result")) {
            return List.of();
        }
        List<JsonElement> ingredients = new ArrayList<>();
        json.getAsJsonArray("ingredients").forEach(ingredients::add);
        return singleResultOutput(json.get("result"), ingredients);
    }

    private static List<ParsedOutput> parseShaped(JsonObject json) {
        if (!json.has("pattern") || !json.has("key") || !json.has("result")) {
            return List.of();
        }
        JsonObject key = json.getAsJsonObject("key");
        List<JsonElement> ingredients = new ArrayList<>();
        for (JsonElement rowElement : json.getAsJsonArray("pattern")) {
            String row = rowElement.getAsString();
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol == ' ') {
                    continue;
                }
                JsonElement keyed = key.get(String.valueOf(symbol));
                if (keyed != null) {
                    ingredients.add(keyed);
                }
            }
        }
        return singleResultOutput(json.get("result"), ingredients);
    }

    private static List<ParsedOutput> parseSingleIngredient(JsonObject json) {
        if (!json.has("ingredient") || !json.has("result")) {
            return List.of();
        }
        return singleResultOutput(json.get("result"), List.of(json.get("ingredient")));
    }

    private static List<ParsedOutput> parseCutting(JsonObject json) {
        if (!json.has("ingredients") || !json.has("result")) {
            return List.of();
        }
        List<JsonElement> ingredients = new ArrayList<>();
        json.getAsJsonArray("ingredients").forEach(ingredients::add);

        List<ParsedOutput> outputs = new ArrayList<>();
        for (JsonElement resultEntry : json.getAsJsonArray("result")) {
            if (!resultEntry.isJsonObject() || !resultEntry.getAsJsonObject().has("item")) {
                continue;
            }
            JsonElement itemStack = resultEntry.getAsJsonObject().get("item");
            resultId(itemStack).ifPresent(id -> outputs.add(new ParsedOutput(id, resultCount(itemStack), ingredients)));
        }
        return outputs;
    }

    private static List<ParsedOutput> singleResultOutput(JsonElement resultElement, List<JsonElement> ingredients) {
        Optional<ResourceLocation> id = resultId(resultElement);
        if (id.isEmpty()) {
            return List.of();
        }
        int count = resultCount(resultElement);
        return List.of(new ParsedOutput(id.get(), count, ingredients));
    }

    private static Optional<ResourceLocation> resultId(JsonElement resultElement) {
        if (resultElement.isJsonPrimitive()) {
            return Optional.of(new ResourceLocation(resultElement.getAsString()));
        }
        if (resultElement.isJsonObject()) {
            JsonObject obj = resultElement.getAsJsonObject();
            String key = obj.has("id") ? "id" : obj.has("item") ? "item" : null;
            if (key != null) {
                return Optional.of(new ResourceLocation(obj.get(key).getAsString()));
            }
        }
        return Optional.empty();
    }

    private static int resultCount(JsonElement resultElement) {
        if (resultElement.isJsonObject()) {
            JsonObject obj = resultElement.getAsJsonObject();
            if (obj.has("count")) {
                return obj.get("count").getAsInt();
            }
        }
        return 1;
    }
}
