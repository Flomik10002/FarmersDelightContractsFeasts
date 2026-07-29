package dev.flomik.farmerscontracts.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class WoodByBiomeProcessor extends StructureProcessor {

    public static final MapCodec<WoodByBiomeProcessor> CODEC = MapCodec.unit(WoodByBiomeProcessor::new);

    private static final String PLACEHOLDER_WOOD = "dark_oak";

    private static final Map<String, String> DESERT_OVERRIDES = Map.ofEntries(
            Map.entry("stripped_dark_oak_wood", "cut_sandstone"),
            Map.entry("dark_oak_slab", "smooth_sandstone_slab"),
            Map.entry("dark_oak_stairs", "smooth_sandstone_stairs"),
            Map.entry("dark_oak_fence", "acacia_fence"),
            Map.entry("dark_oak_trapdoor", "acacia_trapdoor"),
            Map.entry("tuff_slab", "smooth_sandstone_slab"),
            Map.entry("tuff_stairs", "smooth_sandstone_stairs"),
            Map.entry("tuff_wall", "sandstone_wall"),
            Map.entry("andesite_wall", "sandstone_wall"),
            Map.entry("grass_block", "sand")
    );

    private static final List<Property<?>> TRANSFERABLE_PROPERTIES = List.of(
            BlockStateProperties.AXIS,
            BlockStateProperties.HALF,
            BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.OPEN,
            BlockStateProperties.POWERED,
            BlockStateProperties.WATERLOGGED,
            BlockStateProperties.NORTH,
            BlockStateProperties.EAST,
            BlockStateProperties.SOUTH,
            BlockStateProperties.WEST,
            BlockStateProperties.NORTH_WALL,
            BlockStateProperties.EAST_WALL,
            BlockStateProperties.SOUTH_WALL,
            BlockStateProperties.WEST_WALL,
            BlockStateProperties.UP,
            BlockStateProperties.SLAB_TYPE,
            BlockStateProperties.STAIRS_SHAPE
    );

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos offset,
            BlockPos pos,
            StructureTemplate.StructureBlockInfo relativeBlockInfo,
            StructureTemplate.StructureBlockInfo absoluteBlockInfo,
            StructurePlaceSettings settings
    ) {
        BlockState state = absoluteBlockInfo.state();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("minecraft")) {
            return absoluteBlockInfo;
        }

        Holder<Biome> biome = level.getBiome(absoluteBlockInfo.pos());
        String newPath = null;

        if (isDesert(biome)) {
            newPath = DESERT_OVERRIDES.get(id.getPath());
        }
        if (newPath == null && id.getPath().contains(PLACEHOLDER_WOOD)) {
            String targetWood = woodFor(biome);
            if (!targetWood.equals(PLACEHOLDER_WOOD)) {
                newPath = id.getPath().replace(PLACEHOLDER_WOOD, targetWood);
            }
        }
        if (newPath == null) {
            return absoluteBlockInfo;
        }

        Block newBlock = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(newPath)).orElse(null);
        if (newBlock == null) {
            return absoluteBlockInfo;
        }

        BlockState newState = transferProperties(state, newBlock.defaultBlockState());
        return new StructureTemplate.StructureBlockInfo(absoluteBlockInfo.pos(), newState, absoluteBlockInfo.nbt());
    }

    private static boolean isDesert(Holder<Biome> biome) {
        return biome.is(BiomeTags.HAS_VILLAGE_DESERT);
    }

    private static String woodFor(Holder<Biome> biome) {
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY) || biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return "spruce";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return "acacia";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_PLAINS)) {
            return "oak";
        }
        return PLACEHOLDER_WOOD;
    }

    private static BlockState transferProperties(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property<?> property : TRANSFERABLE_PROPERTIES) {
            if (from.hasProperty(property) && to.hasProperty(property)) {
                result = copy(from, result, property);
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return WorldgenRegistry.WOOD_BY_BIOME.get();
    }
}
