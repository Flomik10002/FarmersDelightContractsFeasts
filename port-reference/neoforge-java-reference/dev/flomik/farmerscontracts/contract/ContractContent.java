package dev.flomik.farmerscontracts.contract;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;

public final class ContractContent {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<ResourceLocation, ContractPool> pools = Map.of();
    private static Map<ResourceLocation, Customer> customers = Map.of();

    private ContractContent() {
    }

    public static void reload(Map<ResourceLocation, ContractPool> newPools, Map<ResourceLocation, Customer> newCustomers) {
        pools = Map.copyOf(newPools);
        customers = Map.copyOf(newCustomers);
        LOGGER.info("Loaded {} contract pool(s) and {} customer(s)", pools.size(), customers.size());
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
