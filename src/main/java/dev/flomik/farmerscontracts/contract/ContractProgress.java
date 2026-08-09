package dev.flomik.farmerscontracts.contract;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class ContractProgress extends SavedData {

    private static final String KEY = "farmerscontracts_progress";
    // Vanilla's SavedData.Factory canonical constructor also takes a DataFixTypes for old-save
    // upgrades (NeoForge patches in a 2-arg convenience overload that defaults it to null) -
    // this mod's own save format has no legacy versions to fix up, so null is correct here too.
    private static final SavedData.Factory<ContractProgress> FACTORY =
            new SavedData.Factory<ContractProgress>(ContractProgress::new, ContractProgress::load, null);

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
