package ua.sdocraft.rontacz;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.keybinds.Keybindings;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ua.sdocraft.rontacz.entity.ModEntities;
import ua.sdocraft.rontacz.entity.RiflemanUnit;
import ua.sdocraft.rontacz.production.RiflemanProd;

@Mod(RonTacZ.MOD_ID)
public final class RonTacZ {
    public static final String MOD_ID = "rontacz";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registeredProduction;

    public RonTacZ() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        bus.addListener(RonTacZ::registerAttributes);
        bus.addListener(RonTacZ::commonSetup);
    }
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.RIFLEMAN.get(), RiflemanUnit.createAttributes().build());
    }
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (registeredProduction) return;
            registeredProduction = true;
            RiflemanProd rifleman = Registry.register(
                    ReignOfNetherRegistries.PRODUCTION_ITEM,
                    new ResourceLocation(MOD_ID, "rifleman"),
                    new RiflemanProd());
            Buildings.BARRACKS.productions.add(rifleman, Keybindings.abilitySlot3);
            LOGGER.info("RoN x TaCZ: rifleman added to villager Barracks");
        });
    }
}
