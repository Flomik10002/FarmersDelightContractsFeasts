package dev.flomik.farmerscontracts.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class ContractProgress extends SavedData {

    private static final String KEY = "farmerscontracts_progress";

    private long completedContracts = 0L;

    public long completedContracts() {
        return completedContracts;
    }

    public void incrementCompleted() {
        completedContracts++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("CompletedContracts", completedContracts);
        return tag;
    }

    private static ContractProgress load(CompoundTag tag) {
        ContractProgress progress = new ContractProgress();
        progress.completedContracts = tag.getLong("CompletedContracts");
        return progress;
    }

    public static ContractProgress get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ContractProgress::load, ContractProgress::new, KEY);
    }
}
