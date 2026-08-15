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

public final class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("farmerscontracts.json");

    public enum DeliveryMode {
        TICKET_ONLY,
        BOX_ONLY
    }

    private static final class Data {
        DeliveryMode deliveryMode = DeliveryMode.BOX_ONLY;

        int boardUpdateFrequencySeconds = 45;

        boolean boardCanBreak = true;

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

    public static void setDeliveryModeForTest(DeliveryMode mode) {
        data.deliveryMode = mode;
    }

    public static void setBoardGlobalStateForTest(boolean value) {
        data.boardGlobalState = value;
    }
}
