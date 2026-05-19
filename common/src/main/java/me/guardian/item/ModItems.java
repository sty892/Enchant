package me.guardian.item;

import me.guardian.GuardianMod;
import me.guardian.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.world.item.CreativeModeTab;

public class ModItems {

    // Keys
    public static final Item KEY_1 = register("key_1", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item KEY_2 = register("key_2", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item KEY_3 = register("key_3", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item KEY_4 = register("key_4", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item KEY_5 = register("key_5", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item KEY_6 = register("key_6", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item KEY_7 = register("key_7", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item KEY_8 = register("key_8", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    // Fragments
    public static final Item FRAGMENT_OVERWORLD = register("fragment_overworld", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final Item FRAGMENT_NETHER = register("fragment_nether", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final Item FRAGMENT_GENERIC = register("fragment_generic", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final Item TRIGGER_REVEALER = register("trigger_revealer", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item TRIGGER_AREA_CREATOR = register("trigger_area_creator", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final Item TRIGGER_GUARD = register("trigger_guard", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final Item CAMERA = register("camera", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // Creative Tab
    public static final CreativeModeTab GUARDIAN_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "item_group"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(FRAGMENT_OVERWORLD))
                    .title(Component.translatable("itemGroup.guardian_mod.item_group"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(KEY_1);
                        output.accept(KEY_2);
                        output.accept(KEY_3);
                        output.accept(KEY_4);
                        output.accept(KEY_5);
                        output.accept(KEY_6);
                        output.accept(KEY_7);
                        output.accept(KEY_8);
                        output.accept(FRAGMENT_OVERWORLD);
                        output.accept(FRAGMENT_NETHER);
                        output.accept(FRAGMENT_GENERIC);
                        output.accept(TRIGGER_REVEALER);
                        output.accept(TRIGGER_AREA_CREATOR);
                        output.accept(TRIGGER_GUARD);
                        output.accept(CAMERA);
                        output.accept(ModBlocks.ALTAR_CORE);
                        output.accept(ModBlocks.ALTAR_SPEED);
                        output.accept(ModBlocks.ALTAR_PROTECTION);
                        output.accept(ModBlocks.ALTAR_DAMAGE);
                        output.accept(ModBlocks.ALTAR_RECOVERY);
                        output.accept(ModBlocks.KEYHOLE_1);
                        output.accept(ModBlocks.KEYHOLE_2);
                        output.accept(ModBlocks.KEYHOLE_3);
                        output.accept(ModBlocks.KEYHOLE_4);
                        output.accept(ModBlocks.KEYHOLE_5);
                        output.accept(ModBlocks.KEYHOLE_6);
                        output.accept(ModBlocks.KEYHOLE_7);
                        output.accept(ModBlocks.KEYHOLE_8);
                    })
                    .build());

    private static Item register(String name, Item.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = switch (name) {
            case "trigger_area_creator" -> new TriggerAreaCreatorItem(properties.setId(key));
            case "trigger_guard" -> new TriggerGuardItem(properties.setId(key));
            case "camera" -> new CameraItem(properties.setId(key));
            default -> new Item(properties.setId(key));
        };
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        GuardianMod.LOGGER.info("Registering items for " + GuardianMod.MOD_ID);
    }
}
