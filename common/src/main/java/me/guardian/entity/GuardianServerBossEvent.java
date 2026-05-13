package me.guardian.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class GuardianServerBossEvent extends BossEvent {
    private final Set<ServerPlayer> players = new HashSet<>();
    private final Set<ServerPlayer> unmodifiablePlayers = Collections.unmodifiableSet(players);
    private boolean visible = true;

    public GuardianServerBossEvent(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {
        super(id, name, color, overlay);
    }

    @Override
    public void setProgress(float progress) {
        if (progress == this.progress) {
            return;
        }
        super.setProgress(progress);
        broadcast(ClientboundBossEventPacket::createUpdateProgressPacket);
    }

    @Override
    public void setColor(BossBarColor color) {
        if (color == this.color) {
            return;
        }
        super.setColor(color);
        broadcast(ClientboundBossEventPacket::createUpdateStylePacket);
    }

    @Override
    public void setOverlay(BossBarOverlay overlay) {
        if (overlay == this.overlay) {
            return;
        }
        super.setOverlay(overlay);
        broadcast(ClientboundBossEventPacket::createUpdateStylePacket);
    }

    @Override
    public void setName(Component name) {
        if (name.equals(this.name)) {
            return;
        }
        super.setName(name);
        broadcast(ClientboundBossEventPacket::createUpdateNamePacket);
    }

    @Override
    public BossEvent setDarkenScreen(boolean darkenScreen) {
        if (darkenScreen == this.darkenScreen) {
            return this;
        }
        super.setDarkenScreen(darkenScreen);
        broadcast(ClientboundBossEventPacket::createUpdatePropertiesPacket);
        return this;
    }

    @Override
    public BossEvent setPlayBossMusic(boolean playBossMusic) {
        if (playBossMusic == this.playBossMusic) {
            return this;
        }
        super.setPlayBossMusic(playBossMusic);
        broadcast(ClientboundBossEventPacket::createUpdatePropertiesPacket);
        return this;
    }

    @Override
    public BossEvent setCreateWorldFog(boolean createWorldFog) {
        if (createWorldFog == this.createWorldFog) {
            return this;
        }
        super.setCreateWorldFog(createWorldFog);
        broadcast(ClientboundBossEventPacket::createUpdatePropertiesPacket);
        return this;
    }

    public void addPlayer(ServerPlayer player) {
        if (players.add(player) && visible) {
            player.connection.send(ClientboundBossEventPacket.createAddPacket(this));
        }
    }

    public void removePlayer(ServerPlayer player) {
        if (players.remove(player) && visible) {
            player.connection.send(ClientboundBossEventPacket.createRemovePacket(getId()));
        }
    }

    public void removeAllPlayers() {
        for (ServerPlayer player : Set.copyOf(players)) {
            removePlayer(player);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        if (visible == this.visible) {
            return;
        }
        this.visible = visible;
        for (ServerPlayer player : players) {
            player.connection.send(visible
                    ? ClientboundBossEventPacket.createAddPacket(this)
                    : ClientboundBossEventPacket.createRemovePacket(getId()));
        }
    }

    public Set<ServerPlayer> getPlayers() {
        return unmodifiablePlayers;
    }

    private void broadcast(Function<BossEvent, ClientboundBossEventPacket> packetFactory) {
        if (!visible) {
            return;
        }
        ClientboundBossEventPacket packet = packetFactory.apply(this);
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }
}
