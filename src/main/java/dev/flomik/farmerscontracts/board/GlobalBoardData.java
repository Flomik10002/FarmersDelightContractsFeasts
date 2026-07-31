package dev.flomik.farmerscontracts.board;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// Server-wide board state, ported from Bountiful's GlobalBoardData - opt-in via
// Config.boardGlobalState() (default false, matching Bountiful's own default). When enabled,
// every Contract Board on the server shares this single BoardState instead of each block entity
// keeping its own (see ContractBoardBlockEntity.activeState) - one shared pool of offers/timers,
// not one per physical board. See docs/board-lifecycle-audit.md.
public class GlobalBoardData extends SavedData {

    private static final String KEY = "farmerscontracts_global_board";

    public final BoardState state = new BoardState();

    @Override
    public CompoundTag save(CompoundTag tag) {
        state.saveTo(tag);
        return tag;
    }

    private static GlobalBoardData load(CompoundTag tag) {
        GlobalBoardData data = new GlobalBoardData();
        data.state.loadFrom(tag);
        return data;
    }

    public static GlobalBoardData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(GlobalBoardData::load, GlobalBoardData::new, KEY);
    }
}
