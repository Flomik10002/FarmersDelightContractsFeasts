package dev.flomik.farmerscontracts;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

// Fabric common entrypoint (see fabric.mod.json "entrypoints.main"). Registries, config,
// board/box blocks, contract generation, villager AI, and the scoreboard/event integration hook
// are being ported here incrementally from port-reference/neoforge-java-reference - see the
// project task list for progress.
public class FarmersContractsMod implements ModInitializer {
    public static final String MODID = "farmerscontracts";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("Farmer's Contracts (Fabric 1.21.1) initializing");
    }
}
