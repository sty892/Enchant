package me.sty892.enchant.item;

import me.sty892.enchant.GuardianModCommon;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static final ItemGroup GUARDIAN_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(GuardianModCommon.MOD_ID, "guardian_group"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.KEY_1))
                    .displayName(Text.translatable("itemGroup.guardian_mod.guardian_group"))
                    .entries((context, entries) -> {
                        entries.add(ModItems.KEY_1);
                        entries.add(ModItems.KEY_2);
                        entries.add(ModItems.KEY_3);
                        entries.add(ModItems.KEY_4);
                        entries.add(ModItems.KEY_5);
                        entries.add(ModItems.KEY_6);
                        entries.add(ModItems.KEY_7);
                        entries.add(ModItems.KEY_8);
                        entries.add(ModItems.FRAGMENT_OVERWORLD);
                        entries.add(ModItems.FRAGMENT_NETHER);
                        entries.add(ModItems.FRAGMENT_GENERIC);
                    })
                    .build());

    public static void registerItemGroups() {
        GuardianModCommon.LOGGER.info("Registering Item Groups for " + GuardianModCommon.MOD_ID);
    }
}
