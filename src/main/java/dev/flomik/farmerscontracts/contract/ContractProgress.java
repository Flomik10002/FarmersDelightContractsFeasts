package dev.flomik.farmerscontracts.contract;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class ContractProgress extends SavedData {

    private static final String KEY = "farmerscontracts_progress";
    private static final SavedData.Factory<ContractProgress> FACTORY =
            new SavedData.Factory<>(ContractProgress::new, ContractProgress::load);

    private long completedContracts = 0L;

    public long completedContracts() {
        return completedContracts;
    }

    public void incrementCompleted() {
        completedContracts++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("CompletedContracts", completedContracts);
        return tag;
    }

    private static ContractProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        ContractProgress progress = new ContractProgress();
        progress.completedContracts = tag.getLong("CompletedContracts");
        return progress;
    }

    public static ContractProgress get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }
}
