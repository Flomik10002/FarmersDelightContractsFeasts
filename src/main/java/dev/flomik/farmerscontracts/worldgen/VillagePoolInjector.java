package dev.flomik.farmerscontracts.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public final class VillagePoolInjector {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, String> VILLAGE_TYPE_TO_STRUCTURE = Map.of(
            "plains", "plains_board",
            "desert", "sand_board",
            "savanna", "acacia_board",
            "taiga", "spruce_board",
            "snowy", "spruce_board"
    );

    private static final int WEIGHT = 1;

    private VillagePoolInjector() {
    }

    public static void injectAll(RegistryAccess registryAccess) {
        for (Map.Entry<String, String> entry : VILLAGE_TYPE_TO_STRUCTURE.entrySet()) {
            inject(registryAccess, entry.getKey(), entry.getValue());
        }
    }

    private static void inject(RegistryAccess registryAccess, String villageType, String structureName) {
        ResourceLocation targetPoolId = ResourceLocation.withDefaultNamespace("village/" + villageType + "/houses");
        StructureTemplatePool pool = registryAccess.registryOrThrow(Registries.TEMPLATE_POOL)
                .get(ResourceKey.create(Registries.TEMPLATE_POOL, targetPoolId));
        if (pool == null) {
            LOGGER.warn("Village pool {} not found, skipping Contract Board injection", targetPoolId);
            return;
        }

        Holder<StructureProcessorList> emptyProcessors = registryAccess.registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
        StructurePoolElement element = StructurePoolElement
                .single("farmerscontracts:" + structureName, emptyProcessors)
                .apply(StructureTemplatePool.Projection.RIGID);

        try {
            Field templatesField = StructureTemplatePool.class.getDeclaredField("templates");
            templatesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<StructurePoolElement> templates = (List<StructurePoolElement>) templatesField.get(pool);
            for (int i = 0; i < WEIGHT; i++) {
                templates.add(element);
            }
            LOGGER.info("Injected Contract Board ({}) into {}", structureName, targetPoolId);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to inject Contract Board into village pool {}", targetPoolId, e);
        }
    }
}
