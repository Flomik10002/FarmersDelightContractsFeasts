package dev.flomik.farmerscontracts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

// Fabric has no equivalent of NeoForge's ModConfigSpec, so this is a small hand-rolled JSON
// config carrying the same 4 options as the NeoForge/Forge branches. Loaded once at startup
// (see FarmersContractsMod#onInitialize); values are cached in static fields and re-saved
// whenever the file is missing/incomplete, so adding a new option later doesn't require players
// to delete their config.
public final class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("farmerscontracts.json");

    public enum DeliveryMode {
        TICKET_ONLY,
        BOX_ONLY
    }

    private static final class Data {
        // How contracts may be turned in.
        // TICKET_ONLY: turn in a Contract Ticket directly at the Contract Board using items from
        // the player's inventory. The Contract Box recipe/item is disabled entirely.
        // BOX_ONLY: fill a Contract Box, seal it with the ticket, then deliver the sealed box to
        // the Contract Board. Turning in a ticket straight from inventory is disabled.
        DeliveryMode deliveryMode = DeliveryMode.BOX_ONLY;
        // How often (in real seconds) the Contract Board attempts to refill/rotate its offers.
        // Matches Bountiful's default (45).
        int boardUpdateFrequencySeconds = 45;
        // Whether players are allowed to break the Contract Board at all.
        boolean boardCanBreak = true;
        // If true, every Contract Board on the server shares one pool of offers/timers
        // (GlobalBoardData) instead of each board keeping its own independent state. Matches
        // Bountiful's board.globalBoardState default (false).
        boolean boardGlobalState = false;
    }

    private static Data data = new Data();

    private Config() {
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                if (loaded != null) {
                    data = loaded;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read {}, using defaults", CONFIG_PATH, e);
            }
        }
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write {}", CONFIG_PATH, e);
        }
    }

    public static DeliveryMode deliveryMode() {
        return data.deliveryMode;
    }

    public static int boardUpdateFrequencySeconds() {
        return data.boardUpdateFrequencySeconds;
    }

    public static boolean boardCanBreak() {
        return data.boardCanBreak;
    }

    public static boolean boardGlobalState() {
        return data.boardGlobalState;
    }

    // Test-only hook (SelfTest) to exercise both delivery-mode branches without a real config
    // file/restart.
    public static void setDeliveryModeForTest(DeliveryMode mode) {
        data.deliveryMode = mode;
    }

    // Test-only hook (SelfTest), same rationale as setDeliveryModeForTest.
    public static void setBoardGlobalStateForTest(boolean value) {
        data.boardGlobalState = value;
    }
}
