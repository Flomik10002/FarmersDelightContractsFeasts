package dev.flomik.farmerscontracts.board;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class GlobalBoardData extends SavedData {
    private static final String KEY = "farmerscontracts_global_board";

    private static final SavedData.Factory<GlobalBoardData> FACTORY =
            new SavedData.Factory<GlobalBoardData>(GlobalBoardData::new, GlobalBoardData::load, null);

    public final BoardState state = new BoardState();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        state.saveTo(tag, registries);
        return tag;
    }

    private static GlobalBoardData load(CompoundTag tag, HolderLookup.Provider registries) {
        GlobalBoardData data = new GlobalBoardData();
        data.state.loadFrom(tag, registries);
        return data;
    }

    public static GlobalBoardData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }
}
