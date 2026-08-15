package dev.flomik.farmerscontracts.contract;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ContractContent {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<ResourceLocation, ContractPool> pools = Map.of();
    private static Map<ResourceLocation, Customer> customers = Map.of();

    private static volatile Set<Item> objectiveItems;

    private ContractContent() {
    }

    public static void reload(Map<ResourceLocation, ContractPool> newPools, Map<ResourceLocation, Customer> newCustomers) {
        pools = Map.copyOf(newPools);
        customers = Map.copyOf(newCustomers);
        objectiveItems = null;
        LOGGER.info("Loaded {} contract pool(s) and {} customer(s)", pools.size(), customers.size());
    }

    public static Set<Item> objectiveItems() {
        Set<Item> cached = objectiveItems;
        if (cached == null) {
            cached = computeObjectiveItems();
            objectiveItems = cached;
        }
        return cached;
    }

    private static Set<Item> computeObjectiveItems() {
        Set<Item> items = new HashSet<>();
        for (Customer customer : customers.values()) {
            for (ResourceLocation poolId : customer.objectivePools()) {
                ContractPool pool = pools.get(poolId);
                if (pool == null) {
                    continue;
                }
                for (ContractPoolEntry entry : pool.entries()) {
                    for (ItemStack stack : entry.item().getItems()) {
                        items.add(stack.getItem());
                    }
                }
            }
        }
        return Set.copyOf(items);
    }

    public static Map<ResourceLocation, ContractPool> pools() {
        return pools;
    }

    public static Map<ResourceLocation, Customer> customers() {
        return customers;
    }

    public static Optional<ContractPool> pool(ResourceLocation id) {
        return Optional.ofNullable(pools.get(id));
    }
}
