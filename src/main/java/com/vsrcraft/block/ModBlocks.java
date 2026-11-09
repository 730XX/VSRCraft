package com.vsrcraft.block;

import com.vsrcraft.VSRCraft;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Registro central de bloques y BlockItems de VSRCraft.
 */
public class ModBlocks {
    public static final Identifier RHYTHM_ARCADE_ID = Identifier.fromNamespaceAndPath(VSRCraft.MOD_ID, "rhythm_arcade");

    public static final ResourceKey<Block> RHYTHM_ARCADE_KEY = ResourceKey.create(Registries.BLOCK, RHYTHM_ARCADE_ID);
    public static final ResourceKey<Item> RHYTHM_ARCADE_ITEM_KEY = ResourceKey.create(Registries.ITEM, RHYTHM_ARCADE_ID);

    public static final Block RHYTHM_ARCADE = Registry.register(
            BuiltInRegistries.BLOCK,
            RHYTHM_ARCADE_KEY,
            new RhythmArcadeBlock(
                    BlockBehaviour.Properties.of()
                            .setId(RHYTHM_ARCADE_KEY)
                            .mapColor(MapColor.COLOR_BROWN)
                            .strength(2.5f, 6.0f)
                            .sound(SoundType.WOOD)
                            .lightLevel(state -> 7)
                            .noOcclusion()
            )
    );

    public static final Item RHYTHM_ARCADE_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            RHYTHM_ARCADE_ITEM_KEY,
            new BlockItem(
                    RHYTHM_ARCADE,
                    new Item.Properties()
                            .setId(RHYTHM_ARCADE_ITEM_KEY)
                            .useBlockDescriptionPrefix()
            )
    );

    public static void registerModBlocks() {
        VSRCraft.LOGGER.info("Registrando bloques de VSRCraft...");

        // Añadir el Mini-Arcade a las pestañas de Bloques Funcionales y Redstone en Creativo
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(RHYTHM_ARCADE_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(RHYTHM_ARCADE_ITEM);
        });
    }
}
