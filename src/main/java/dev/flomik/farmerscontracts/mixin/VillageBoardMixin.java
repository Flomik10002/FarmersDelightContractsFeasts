package dev.flomik.farmerscontracts.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(JigsawPlacement.class)
public class VillageBoardMixin {

    private static final int[][] OFFSETS = {
            {10, 0, 10}, {-10, 0, 10}, {10, 0, -10}, {-10, 0, -10},
            {16, 0, 0}, {-16, 0, 0}, {0, 0, 16}, {0, 0, -16},
            {20, 0, 20}, {-20, 0, 20}, {20, 0, -20}, {-20, 0, -20}
    };

    private static final int MAX_CORNER_SPREAD = 3;

    @Inject(method = "addPieces", at = @At("RETURN"), cancellable = true)
    private static void farmerscontracts$addBoard(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos blockPos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            PoolAliasLookup poolAliasLookup,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir
    ) {
        if (!isVillageTownCenters(startPool) || isZombieVillage(startPool, poolAliasLookup) || cir.getReturnValue().isEmpty()) {
            return;
        }

        Structure.GenerationStub stub = cir.getReturnValue().get();
        Optional<Consumer<StructurePiecesBuilder>> originalGenerator = stub.generator().left();
        if (originalGenerator.isEmpty()) {
            return;
        }
        Consumer<StructurePiecesBuilder> original = originalGenerator.get();

        cir.setReturnValue(Optional.of(new Structure.GenerationStub(stub.position(), builder -> {
            original.accept(builder);
            farmerscontracts$tryAddBoard(context, stub.position(), builder);
        })));
    }

    private static boolean isVillageTownCenters(Holder<StructureTemplatePool> pool) {
        return pool.unwrapKey()
                .map(ResourceKey::location)
                .filter(loc -> loc.getNamespace().equals("minecraft"))
                .map(ResourceLocation::getPath)
                .filter(path -> path.startsWith("village/") && path.endsWith("/town_centers"))
                .isPresent();
    }

    private static boolean isZombieVillage(Holder<StructureTemplatePool> startPool, PoolAliasLookup lookup) {
        return startPool.unwrapKey()
                .map(key -> {
                    String housesPath = key.location().getPath().replace("/town_centers", "/houses");
                    ResourceKey<StructureTemplatePool> housesKey =
                            ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath(key.location().getNamespace(), housesPath));
                    return lookup.lookup(housesKey).location().getPath().contains("zombie");
                })
                .orElse(false);
    }

    private static final int SAFETY_MARGIN = 0;

    private static void farmerscontracts$tryAddBoard(Structure.GenerationContext context, BlockPos anchor, StructurePiecesBuilder builder) {
        Holder<StructureProcessorList> processors = context.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST,
                        ResourceLocation.fromNamespaceAndPath("farmerscontracts", "village_wood_by_biome")));
        StructurePoolElement element = StructurePoolElement.single("farmerscontracts:board", processors)
                .apply(StructureTemplatePool.Projection.RIGID);
        RandomSource random = context.random();

        for (int[] offset : OFFSETS) {
            BlockPos candidate = anchor.offset(offset[0], offset[1], offset[2]);
            Rotation rotation = Rotation.getRandom(random);

            BoundingBox rawBounds = element.getBoundingBox(context.structureTemplateManager(), candidate, rotation);

            int centerX = (rawBounds.minX() + rawBounds.maxX()) / 2;
            int centerZ = (rawBounds.minZ() + rawBounds.maxZ()) / 2;
            int h1 = surfaceHeightAt(context, rawBounds.minX(), rawBounds.minZ());
            int h2 = surfaceHeightAt(context, rawBounds.maxX(), rawBounds.minZ());
            int h3 = surfaceHeightAt(context, rawBounds.minX(), rawBounds.maxZ());
            int h4 = surfaceHeightAt(context, rawBounds.maxX(), rawBounds.maxZ());
            int minCorner = Math.min(Math.min(h1, h2), Math.min(h3, h4));
            int maxCorner = Math.max(Math.max(h1, h2), Math.max(h3, h4));
            if (maxCorner - minCorner > MAX_CORNER_SPREAD) {
                continue;
            }

            int centerHeight = surfaceHeightAt(context, centerX, centerZ);
            int shift = centerHeight - rawBounds.minY() + SAFETY_MARGIN;
            BlockPos pos = candidate.offset(0, shift, 0);
            BoundingBox bounds = rawBounds.moved(0, shift, 0);
            if (builder.findCollisionPiece(bounds) != null) {
                continue;
            }

            PoolElementStructurePiece piece = new PoolElementStructurePiece(
                    context.structureTemplateManager(), element, pos, element.getGroundLevelDelta(),
                    rotation, bounds, LiquidSettings.APPLY_WATERLOGGING);
            builder.addPiece(piece);
            return;
        }
    }

    private static int surfaceHeightAt(Structure.GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstFreeHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
    }
}
