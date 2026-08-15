package dev.flomik.farmerscontracts.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(JigsawPlacement.class)
public class VillageBoardLimitMixin {
    private static final int MAX_BOARDS_PER_VILLAGE = 1;

    @Inject(method = "addPieces", at = @At("RETURN"), cancellable = true)
    private static void farmerscontracts$limitBoardsPerVillage(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos blockPos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir
    ) {
        if (!isVillageTownCenters(startPool) || cir.getReturnValue().isEmpty()) {
            return;
        }

        Structure.GenerationStub stub = cir.getReturnValue().get();
        Optional<Consumer<StructurePiecesBuilder>> originalGenerator = stub.generator().left();
        if (originalGenerator.isEmpty()) {
            return;
        }
        Consumer<StructurePiecesBuilder> original = originalGenerator.get();

        cir.setReturnValue(Optional.of(new Structure.GenerationStub(stub.position(),
                builder -> original.accept(new BoardLimitingPiecesBuilder(builder)))));
    }

    private static boolean isVillageTownCenters(Holder<StructureTemplatePool> pool) {
        return pool.unwrapKey()
                .map(ResourceKey::location)
                .filter(loc -> loc.getNamespace().equals("minecraft"))
                .map(ResourceLocation::getPath)
                .filter(path -> path.startsWith("village/") && path.endsWith("/town_centers"))
                .isPresent();
    }

    private static boolean isBoardPiece(StructurePiece piece) {
        if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
            return false;
        }
        StructurePoolElement element = poolPiece.getElement();
        if (!(element instanceof SinglePoolElement singleElement)) {
            return false;
        }
        ResourceLocation id = getTemplateId(singleElement);
        return id != null && id.getNamespace().equals("farmerscontracts") && id.getPath().endsWith("_board");
    }

    @SuppressWarnings("unchecked")
    private static ResourceLocation getTemplateId(SinglePoolElement element) {
        try {
            Field templateField = SinglePoolElement.class.getDeclaredField("template");
            templateField.setAccessible(true);
            Either<ResourceLocation, StructureTemplate> template = (Either<ResourceLocation, StructureTemplate>) templateField.get(element);
            return template.left().orElse(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static final class BoardLimitingPiecesBuilder extends StructurePiecesBuilder {
        private final StructurePiecesBuilder delegate;
        private int boardsPlaced = 0;

        BoardLimitingPiecesBuilder(StructurePiecesBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public void addPiece(StructurePiece piece) {
            if (isBoardPiece(piece)) {
                if (boardsPlaced >= MAX_BOARDS_PER_VILLAGE) {
                    return;
                }
                boardsPlaced++;
            }
            delegate.addPiece(piece);
        }
    }
}
