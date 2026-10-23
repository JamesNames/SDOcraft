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
import ua.sdocraft.rontacz.production.ArmyUnitProd;

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
        event.put(ModEntities.SNIPER.get(), RiflemanUnit.createAttributes().build());
        event.put(ModEntities.MACHINE_GUNNER.get(), RiflemanUnit.createAttributes().build());
        event.put(ModEntities.TANK_CREW.get(), RiflemanUnit.createAttributes().build());
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

            var sniper=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,
                new ResourceLocation(MOD_ID,"sniper"),
                new ArmyUnitProd("Sniper [AWM]",ModEntities.SNIPER::get,false));
            var machineGunner=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,
                new ResourceLocation(MOD_ID,"machine_gunner"),
                new ArmyUnitProd("Machine Gunner [M249]",ModEntities.MACHINE_GUNNER::get,false));
            var tank=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,
                new ResourceLocation(MOD_ID,"m1a2"),
                new ArmyUnitProd("M1A2 Abrams",ModEntities.TANK_CREW::get,true));
            Buildings.BARRACKS.productions.add(sniper, Keybindings.abilitySlot4);
            Buildings.BARRACKS.productions.add(machineGunner, Keybindings.abilitySlot5);
            Buildings.BARRACKS.productions.add(tank, Keybindings.abilitySlot6);
            LOGGER.info("RoN x TaCZ x SBW: rifleman, sniper, MG, M1A2 registered");
        });
    }
}
