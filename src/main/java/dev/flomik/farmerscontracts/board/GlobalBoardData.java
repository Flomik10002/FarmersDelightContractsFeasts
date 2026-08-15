package dev.flomik.farmerscontracts.board;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

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
