package dev.flomik.farmerscontracts.testutil;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FakePlayerFactory {

    private static final GameProfile MINECRAFT = new GameProfile(UUID.fromString("41C82C87-7AfB-4024-BA57-13D2C99CAE77"), "[Minecraft]");
    private static final Map<ServerLevel, FakePlayer> fakePlayers = new HashMap<>();

    private FakePlayerFactory() {
    }

    public static FakePlayer getMinecraft(ServerLevel level) {
        return fakePlayers.computeIfAbsent(level, l -> new FakePlayer(l, MINECRAFT));
    }
}
