package dev.flomik.farmerscontracts.contract;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class ContractProgress extends SavedData {

    private static final String KEY = "farmerscontracts_progress";

    private long completedContracts = 0L;
    // Keyed by board position rather than stored on the block entity, so breaking and
    // replacing a board can't be used to force an immediate reroll of its contracts - the
    // world remembers when that position was last refilled independently of the physical block.
    private final Map<BlockPos, Long> boardLastRefillDay = new HashMap<>();

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

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("CompletedContracts", completedContracts);
        ListTag boardsTag = new ListTag();
        for (Map.Entry<BlockPos, Long> entry : boardLastRefillDay.entrySet()) {
            CompoundTag boardTag = new CompoundTag();
            boardTag.putLong("Pos", entry.getKey().asLong());
            boardTag.putLong("Day", entry.getValue());
            boardsTag.add(boardTag);
        }
        tag.put("Boards", boardsTag);
        return tag;
    }

    private static ContractProgress load(CompoundTag tag) {
        ContractProgress progress = new ContractProgress();
        progress.completedContracts = tag.getLong("CompletedContracts");
        for (Tag entry : tag.getList("Boards", Tag.TAG_COMPOUND)) {
            CompoundTag boardTag = (CompoundTag) entry;
            progress.boardLastRefillDay.put(BlockPos.of(boardTag.getLong("Pos")), boardTag.getLong("Day"));
        }
        return progress;
    }

    public static ContractProgress get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ContractProgress::load, ContractProgress::new, KEY);
    }
}
