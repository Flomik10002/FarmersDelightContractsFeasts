package dev.flomik.farmerscontracts.testutil;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class FakePlayer extends ServerPlayer {
    public FakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, ClientInformation.createDefault());
        this.connection = new FakePlayerNetHandler(level.getServer(), this);
        this.setInvulnerable(true);
    }

    @Override
    public void sendSystemMessage(Component chatComponent, boolean actionBar) {
    }

    private static class FakePlayerNetHandler extends ServerGamePacketListenerImpl {
        private static final Connection DUMMY_CONNECTION = new FakeConnection();

        FakePlayerNetHandler(MinecraftServer server, ServerPlayer player) {
            super(server, DUMMY_CONNECTION, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void tick() {
        }

        @Override
        public void disconnect(DisconnectionDetails details) {
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener sendListener) {
        }
    }

    private static final class FakeConnection extends Connection {
        FakeConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setListenerForServerboundHandshake(PacketListener listener) {
        }
    }
}
