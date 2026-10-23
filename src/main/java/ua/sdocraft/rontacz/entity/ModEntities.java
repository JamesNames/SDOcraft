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
    public static final RegistryObject<EntityType<RiflemanUnit>> RIFLEMAN = ENTITIES.register(
        "rifleman_unit", () -> EntityType.Builder
           .of(RiflemanUnit::new, MobCategory.CREATURE)
           .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
           .clientTrackingRange(100)
           .build("rontacz:rifleman_unit")
    );
}
