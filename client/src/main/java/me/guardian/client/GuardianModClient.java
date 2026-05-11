package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.ModState;
import me.guardian.block.ModBlocks;
import me.guardian.client.trigger.TriggerAreaClient;
import me.guardian.entity.ModEntities;
import me.guardian.item.ModItems;
import me.guardian.network.GuardianNetworking;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GuardianModClient implements ClientModInitializer {
    public static boolean waitingForHandshake = false;
    public static int handshakeTicks = 0;
    private static boolean triggerRevealVisible = false;

    @Override
    public void onInitializeClient() {
        GuardianNetworking.registerPayloadTypes();
        GuardianMod.LOGGER.info("Guardian Mod client foundation initialized");
        registerEntityRenderers();
        registerTriggerVisibility();
        TriggerAreaClient.initialize();
        registerResourceReloadListener();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.isSingleplayer()) {
                ModState.serverModPresent = true;
                reloadBossAssets(client.getResourceManager());
                GuardianMod.LOGGER.info("Guardian Mod: Singleplayer detected, enabling features.");
            } else {
                ModState.serverModPresent = false;
                waitingForHandshake = true;
                handshakeTicks = 0;
                reloadBossAssets(client.getResourceManager());

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
            TriggerAreaClient.clear();
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
            refreshTriggerVisibility(client);
            TriggerAreaClient.tick(client);
        });

        ClientPlayNetworking.registerGlobalReceiver(HandshakeOkS2CPayload.TYPE, (payload, context) -> {
            waitingForHandshake = false;
            ModState.serverModPresent = true;
            reloadBossAssets(context.client().getResourceManager());
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

    private static void registerTriggerVisibility() {
        BlockRenderLayerMap.putBlocks(ChunkSectionLayer.TRANSLUCENT, ModBlocks.DIMENSION_TRIGGER, ModBlocks.DIMENSION_RETURN_TRIGGER);
        ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> triggerRevealVisible ? 0x88FF4FD8 : 0x00FFFFFF,
                ModBlocks.DIMENSION_TRIGGER);
        ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> triggerRevealVisible ? 0x884DFFB8 : 0x00FFFFFF,
                ModBlocks.DIMENSION_RETURN_TRIGGER);
    }

    private static void refreshTriggerVisibility(Minecraft client) {
        boolean visible = client.player != null && client.player.isHolding(ModItems.TRIGGER_REVEALER);
        if (visible != triggerRevealVisible) {
            triggerRevealVisible = visible;
            if (client.levelRenderer != null) {
                client.levelRenderer.allChanged();
            }
        }
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
                reloadBossAssets(manager);
            }
        });
    }

    private static void reloadBossAssets(ResourceManager manager) {
        GuardianBossAssets.reload(manager);
    }

    @SuppressWarnings("unused")
    private static void reloadBossAssets(Minecraft client) {
        reloadBossAssets(client.getResourceManager());
    }
}
