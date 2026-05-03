package me.guardian.item;

import me.guardian.GuardianMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.world.item.CreativeModeTab;

public class ModItems {

    // Keys
    public static final Item KEY_1 = register("key_1", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item KEY_2 = register("key_2", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item KEY_3 = register("key_3", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item KEY_4 = register("key_4", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item KEY_5 = register("key_5", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item KEY_6 = register("key_6", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item KEY_7 = register("key_7", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item KEY_8 = register("key_8", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // Fragments
    public static final Item FRAGMENT_OVERWORLD = register("fragment_overworld", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final Item FRAGMENT_NETHER = register("fragment_nether", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final Item FRAGMENT_GENERIC = register("fragment_generic", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

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
                    })
                    .build());

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name), item);
    }

    public static void initialize() {
        GuardianMod.LOGGER.info("Registering items for " + GuardianMod.MOD_ID);
    }
}
