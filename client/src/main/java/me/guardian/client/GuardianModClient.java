package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.ModState;
import me.guardian.entity.ModEntities;
import me.guardian.network.GuardianNetworking;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GuardianModClient implements ClientModInitializer {
    public static boolean waitingForHandshake = false;
    public static int handshakeTicks = 0;

    @Override
    public void onInitializeClient() {
        GuardianNetworking.registerPayloadTypes();
        GuardianMod.LOGGER.info("Guardian Mod client foundation initialized");
        registerEntityRenderers();
        registerResourceReloadListener();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.isSingleplayer()) {
                ModState.serverModPresent = true;
                refreshResourcePackLoaded(client.getResourceManager());
                GuardianMod.LOGGER.info("Guardian Mod: Singleplayer detected, enabling features.");
            } else {
                ModState.serverModPresent = false;
                waitingForHandshake = true;
                handshakeTicks = 0;
                refreshResourcePackLoaded(client.getResourceManager());

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
            refreshResourcePackLoaded(context.client().getResourceManager());
            GuardianMod.LOGGER.info("Guardian Mod server handshake received, enabling features.");
        });
    }

    private static void registerEntityRenderers() {
        EntityRendererRegistry.register(ModEntities.OVERWORLD_GUARDIAN,
                context -> new GeoEntityRenderer<>(context, new GuardianBossModel<>("boss_overworld")));
        EntityRendererRegistry.register(ModEntities.NETHER_GUARDIAN,
                context -> new GeoEntityRenderer<>(context, new GuardianBossModel<>("boss_nether")));
        EntityRendererRegistry.register(ModEntities.GENERIC_BOSS,
                context -> new GeoEntityRenderer<>(context, new GuardianBossModel<>("boss_generic")));
    }

    private static void registerResourceReloadListener() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            private final Identifier id = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "boss_asset_reload");

            @Override
            public Identifier getFabricId() {
                return id;
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                refreshResourcePackLoaded(manager);
            }
        });
    }

    private static void refreshResourcePackLoaded(ResourceManager manager) {
        Identifier bossTexture = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/entity/boss_overworld.png");
        ModState.resourcePackLoaded = manager.getResource(bossTexture).isPresent();
        if (!ModState.resourcePackLoaded) {
            GuardianMod.LOGGER.warn("Guardian Mod boss resource pack texture not detected; fallback boss assets will be used.");
        }
    }

    @SuppressWarnings("unused")
    private static void refreshResourcePackLoaded(Minecraft client) {
        refreshResourcePackLoaded(client.getResourceManager());
    }
}
