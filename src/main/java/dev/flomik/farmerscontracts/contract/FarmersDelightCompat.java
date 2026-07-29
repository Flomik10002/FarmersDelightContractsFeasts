package dev.flomik.farmerscontracts.contract;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import vectorwing.farmersdelight.common.Configuration;

import java.util.Set;

final class FarmersDelightCompat {

    private static final Set<String> VANILLA_CROP_CRATES = Set.of(
            "farmersdelight:carrot_crate", "farmersdelight:potato_crate", "farmersdelight:beetroot_crate"
    );

    private FarmersDelightCompat() {
    }

    static boolean isAvailable(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (VANILLA_CROP_CRATES.contains(id.toString())) {
            return Configuration.ENABLE_VANILLA_CROP_CRATES.get();
        }
        return true;
    }
}
