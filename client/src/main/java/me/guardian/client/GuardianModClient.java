package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.ModState;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class GuardianModClient implements ClientModInitializer {
    public static boolean waitingForHandshake = false;
    public static int handshakeTicks = 0;

    @Override
    public void onInitializeClient() {
        GuardianMod.LOGGER.info("Guardian Mod client foundation initialized");

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.isSingleplayer()) {
                ModState.serverModPresent = true;
                refreshResourcePackLoaded(client);
                GuardianMod.LOGGER.info("Guardian Mod: Singleplayer detected, enabling features.");
            } else {
                ModState.serverModPresent = false;
                waitingForHandshake = true;
                handshakeTicks = 0;
                refreshResourcePackLoaded(client);
                
                if (ClientPlayNetworking.canSend(HandshakeC2SPayload.TYPE)) {
                    sender.sendPacket(new HandshakeC2SPayload());
                } else {
                    GuardianMod.LOGGER.info("Guardian Mod: Server does not support custom payloads, disabling features.");
                    waitingForHandshake = false;
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ModState.serverModPresent = false;
            waitingForHandshake = false;
            ModState.resourcePackLoaded = false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (waitingForHandshake) {
                handshakeTicks++;
                if (handshakeTicks > 100) { // 5 seconds timeout
                    waitingForHandshake = false;
                    ModState.serverModPresent = false;
                    GuardianMod.LOGGER.info("Guardian Mod server not detected (timeout), disabling spoiler features.");
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(HandshakeOkS2CPayload.TYPE, (payload, context) -> {
            waitingForHandshake = false;
            ModState.serverModPresent = true;
            refreshResourcePackLoaded(context.client());
            GuardianMod.LOGGER.info("Guardian Mod server handshake received, enabling features.");
        });
    }

    private static void refreshResourcePackLoaded(Minecraft client) {
        Identifier bossTexture = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/entity/boss_overworld.png");
        ModState.resourcePackLoaded = client.getResourceManager().getResource(bossTexture).isPresent();
        if (!ModState.resourcePackLoaded) {
            GuardianMod.LOGGER.warn("Guardian Mod server resource pack not detected; boss renderers should use fallback assets.");
        }
    }
}
