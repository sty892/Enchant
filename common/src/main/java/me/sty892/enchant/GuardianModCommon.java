package me.sty892.enchant;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuardianModCommon implements ModInitializer {
    public static final String MOD_ID = "guardian_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Guardian Mod Common Initialized");
        me.sty892.enchant.networking.HandshakePayload.register();
        me.sty892.enchant.networking.HandshakeOkPayload.register();
        me.sty892.enchant.item.ModItems.registerModItems();
        me.sty892.enchant.item.ModItemGroups.registerItemGroups();
        me.sty892.enchant.block.ModBlocks.registerModBlocks();
        me.sty892.enchant.block.entity.ModBlockEntities.registerBlockEntities();
        me.sty892.enchant.entity.ModEntities.registerModEntities();
    }
}
