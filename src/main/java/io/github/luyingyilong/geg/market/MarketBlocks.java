package io.github.luyingyilong.geg.market;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;


public final class MarketBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GegMod.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GegMod.MODID);

    public static final RegistryObject<Block> MARKET_TERMINAL = BLOCKS.register("market_terminal",
            () -> new MarketTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> MARKET_TERMINAL_ITEM = ITEMS.register("market_terminal",
            () -> new BlockItem(MARKET_TERMINAL.get(), new Item.Properties()));


    private MarketBlocks() {
    }


    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(MarketBlocks::addCreativeTab);
    }


    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MARKET_TERMINAL_ITEM);
        }
    }
}
