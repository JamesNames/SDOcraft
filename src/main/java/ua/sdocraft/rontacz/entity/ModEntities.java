package ua.sdocraft.rontacz.entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ua.sdocraft.rontacz.RonTacZ;

public final class ModEntities {
    private ModEntities() {}
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RonTacZ.MOD_ID);
    public static final RegistryObject<EntityType<SniperUnit>> SNIPER = ENTITIES.register(
        "sniper_unit", () -> EntityType.Builder
            .of(SniperUnit::new, MobCategory.CREATURE)
            .sized(0.6f, 1.95f).clientTrackingRange(100).build("rontacz:sniper_unit"));
    public static final RegistryObject<EntityType<MachineGunnerUnit>> MACHINE_GUNNER = ENTITIES.register(
        "machine_gunner_unit", () -> EntityType.Builder
            .of(MachineGunnerUnit::new, MobCategory.CREATURE)
            .sized(0.6f, 1.95f).clientTrackingRange(100).build("rontacz:machine_gunner_unit"));
    public static final RegistryObject<EntityType<T90CrewUnit>> T90_CREW = ENTITIES.register(
        "t90_crew_unit", () -> EntityType.Builder
            .of(T90CrewUnit::new, MobCategory.CREATURE)
            .sized(0.6f, 1.95f).clientTrackingRange(100).build("rontacz:t90_crew_unit"));
    public static final RegistryObject<EntityType<Mi28CrewUnit>> MI28_CREW = ENTITIES.register(
        "mi28_crew_unit", () -> EntityType.Builder
            .of(Mi28CrewUnit::new, MobCategory.CREATURE)
            .sized(0.6f, 1.95f).clientTrackingRange(100).build("rontacz:mi28_crew_unit"));
    public static final RegistryObject<EntityType<TankCrewUnit>> TANK_CREW = ENTITIES.register(
        "tank_crew_unit", () -> EntityType.Builder
            .of(TankCrewUnit::new, MobCategory.CREATURE)
            .sized(0.6f, 1.95f).clientTrackingRange(100).build("rontacz:tank_crew_unit"));
    public static final RegistryObject<EntityType<RiflemanUnit>> RIFLEMAN = ENTITIES.register(
        "rifleman_unit", () -> EntityType.Builder
           .of(RiflemanUnit::new, MobCategory.CREATURE)
           .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
           .clientTrackingRange(100)
           .build("rontacz:rifleman_unit")
    );
}
