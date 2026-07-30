package dev.flomik.farmerscontracts.contract;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class ContractProgress extends SavedData {

    private static final String KEY = "farmerscontracts_progress";
    private static final SavedData.Factory<ContractProgress> FACTORY =
            new SavedData.Factory<>(ContractProgress::new, ContractProgress::load);

    private long completedContracts = 0L;
    // Keyed by board position rather than stored on the block entity, so breaking and
    // replacing a board can't be used to force an immediate reroll of its contracts - the
    // world remembers when that position was last refilled independently of the physical block.
    private final Map<BlockPos, Long> boardLastRefillDay = new HashMap<>();
    // Cumulative ticks legitimately skipped by players sleeping through the night, per dimension.
    // Day tracking is based on getGameTime() (immune to /time set and /time add, which only ever
    // touch getDayTime()) plus this offset, so sleep-skipped nights still count as a day change.
    private final Map<ResourceKey<Level>, Long> sleepDayOffset = new HashMap<>();

    public long completedContracts() {
        return completedContracts;
    }

    public void incrementCompleted() {
        completedContracts++;
        setDirty();
    }

    public Long boardLastRefillDay(BlockPos pos) {
        return boardLastRefillDay.get(pos.immutable());
    }

    public void setBoardLastRefillDay(BlockPos pos, long day) {
        boardLastRefillDay.put(pos.immutable(), day);
        setDirty();
    }

    public long sleepDayOffset(ResourceKey<Level> dimension) {
        return sleepDayOffset.getOrDefault(dimension, 0L);
    }

    public void addSleepDayOffset(ResourceKey<Level> dimension, long ticksSkipped) {
        if (ticksSkipped <= 0) {
            return;
        }
        sleepDayOffset.merge(dimension, ticksSkipped, Long::sum);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("CompletedContracts", completedContracts);
        ListTag boardsTag = new ListTag();
        for (Map.Entry<BlockPos, Long> entry : boardLastRefillDay.entrySet()) {
            CompoundTag boardTag = new CompoundTag();
            boardTag.putLong("Pos", entry.getKey().asLong());
            boardTag.putLong("Day", entry.getValue());
            boardsTag.add(boardTag);
        }
        tag.put("Boards", boardsTag);

        ListTag sleepOffsetsTag = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Long> entry : sleepDayOffset.entrySet()) {
            CompoundTag offsetTag = new CompoundTag();
            offsetTag.putString("Dimension", entry.getKey().location().toString());
            offsetTag.putLong("Offset", entry.getValue());
            sleepOffsetsTag.add(offsetTag);
        }
        tag.put("SleepDayOffsets", sleepOffsetsTag);
        return tag;
    }

    private static ContractProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        ContractProgress progress = new ContractProgress();
        progress.completedContracts = tag.getLong("CompletedContracts");
        for (Tag entry : tag.getList("Boards", Tag.TAG_COMPOUND)) {
            CompoundTag boardTag = (CompoundTag) entry;
            progress.boardLastRefillDay.put(BlockPos.of(boardTag.getLong("Pos")), boardTag.getLong("Day"));
        }
        for (Tag entry : tag.getList("SleepDayOffsets", Tag.TAG_COMPOUND)) {
            CompoundTag offsetTag = (CompoundTag) entry;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(offsetTag.getString("Dimension")));
            progress.sleepDayOffset.put(dimension, offsetTag.getLong("Offset"));
        }
        return progress;
    }

    public static ContractProgress get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }
}
