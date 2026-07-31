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

// Injects the Contract Board directly into vanilla's own village jigsaw pools
// (minecraft:village/<type>/houses) at server start, instead of hand-computing offset/rotation/
// height the way the old VillageBoardMixin did. Mirrors exactly how Bountiful adds its own
// bounty_gazebo (BountifulSharedApi.kt, Kambrik.Structure.addToStructurePool): once our piece is a
// first-class candidate in the SAME pool vanilla draws houses from, vanilla's own jigsaw placer
// handles rotation/connection/collision correctly for free - no more crooked/backwards spawns.
//
// StructureTemplatePool.templates (private, ObjectArrayList<StructurePoolElement>) is the actual
// list getRandomTemplate()/getShuffledTemplates() draw from - rawTemplates (the codec-facing
// field) is NOT consulted for placement, only for re-serialization, so mutating templates alone
// is sufficient. This is a plain reflective mutation of a live mutable list, not bytecode
// patching - safe post-registry-freeze since the pool OBJECT itself is still a normal mutable
// Java object, only the registry's id->object MAPPING is frozen.
//
// Requires each injected structure's NBT to carry its own jigsaw connector block (see
// docs/village-board-spawn.md for the exact block-entity values) - without one, vanilla's jigsaw
// placer has no attachment point to align against and will simply never pick the candidate.
public final class VillagePoolInjector {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Village type -> dedicated hand-built structure variant, each already built with the
    // correct materials for that village - no runtime block substitution needed or wanted.
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
        ResourceLocation targetPoolId = new ResourceLocation("village/" + villageType + "/houses");
        StructureTemplatePool pool = registryAccess.registryOrThrow(Registries.TEMPLATE_POOL)
                .get(ResourceKey.create(Registries.TEMPLATE_POOL, targetPoolId));
        if (pool == null) {
            LOGGER.warn("Village pool {} not found, skipping Contract Board injection", targetPoolId);
            return;
        }

        Holder<StructureProcessorList> emptyProcessors = registryAccess.registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, new ResourceLocation("empty")));
        // RIGID, not TERRAIN_MATCHING - see docs/village-board-spawn.md: GravityProcessor (added
        // automatically by TERRAIN_MATCHING) moves every block of the structure independently
        // based on the terrain height under that block's own x/z column, which tears a rigid
        // multi-block structure apart on anything but dead-flat ground. Vanilla's own house pool
        // pieces all use RIGID for exactly this reason.
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
