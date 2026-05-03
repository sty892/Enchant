package me.sty892.enchant.item;

import me.sty892.enchant.GuardianModCommon;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item KEY_1 = register("key_1", new Item(new Item.Settings().maxCount(1)));
    public static final Item KEY_2 = register("key_2", new Item(new Item.Settings().maxCount(1)));
    public static final Item KEY_3 = register("key_3", new Item(new Item.Settings().maxCount(1)));
    public static final Item KEY_4 = register("key_4", new Item(new Item.Settings().maxCount(1)));
    public static final Item KEY_5 = register("key_5", new Item(new Item.Settings().maxCount(1)));
    public static final Item KEY_6 = register("key_6", new Item(new Item.Settings().maxCount(1)));
    public static final Item KEY_7 = register("key_7", new Item(new Item.Settings().maxCount(1)));
    public static final Item KEY_8 = register("key_8", new Item(new Item.Settings().maxCount(1)));

    public static final Item FRAGMENT_OVERWORLD = register("fragment_overworld", new Item(new Item.Settings().maxCount(1)));
    public static final Item FRAGMENT_NETHER = register("fragment_nether", new Item(new Item.Settings().maxCount(1)));
    public static final Item FRAGMENT_GENERIC = register("fragment_generic", new Item(new Item.Settings().maxCount(1)));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(GuardianModCommon.MOD_ID, name), item);
    }

    public static void registerModItems() {
        GuardianModCommon.LOGGER.info("Registering Mod Items for " + GuardianModCommon.MOD_ID);
    }
}
