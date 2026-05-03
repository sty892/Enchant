package me.sty892.enchant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import me.sty892.enchant.networking.HandshakePayload;
import me.sty892.enchant.networking.HandshakeOkPayload;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import me.sty892.enchant.entity.ModEntities;
import me.sty892.enchant.entity.renderer.GuardianBossRenderer;
import me.sty892.enchant.networking.HandshakePayload;
import me.sty892.enchant.networking.HandshakeOkPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GuardianModClient implements ClientModInitializer {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void onInitializeClient() {
        GuardianModCommon.LOGGER.info("Guardian Mod Client Initialized");

        EntityRendererRegistry.register(ModEntities.OVERWORLD_GUARDIAN, (ctx) -> new GuardianBossRenderer<>(ctx, "boss_overworld"));
        EntityRendererRegistry.register(ModEntities.NETHER_GUARDIAN, (ctx) -> new GuardianBossRenderer<>(ctx, "boss_nether"));
        EntityRendererRegistry.register(ModEntities.GENERIC_BOSS, (ctx) -> new GuardianBossRenderer<>(ctx, "boss_generic"));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ModState.serverModPresent = client.isInSingleplayer();
            checkResourcePack(client);
            
            if (!client.isInSingleplayer()) {
                sender.sendPacket(new HandshakePayload());
                SCHEDULER.schedule(() -> {
                    if (!ModState.serverModPresent) {
                        GuardianModCommon.LOGGER.warn("Server mod not present or handshake timed out.");
                    }
                }, 5, TimeUnit.SECONDS);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(HandshakeOkPayload.ID, (payload, context) -> {
            ModState.serverModPresent = true;
            GuardianModCommon.LOGGER.info("Server mod handshake successful.");
        });
    }

    private void checkResourcePack(MinecraftClient client) {
        // Check for a specific texture that should be in the server resource pack
        Identifier testId = Identifier.of("guardian_mod", "textures/entity/boss_overworld.png");
        ModState.resourcePackLoaded = client.getResourceManager().getResource(testId).isPresent();
    }
}
