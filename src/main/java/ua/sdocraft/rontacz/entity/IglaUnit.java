package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 9K38 Igla MANPADS: engage aircraft, not friendly ground infantry. */
public final class IglaUnit extends MissileSpecialistUnit {
    public IglaUnit(EntityType<? extends Pillager> type, Level level) { super(type,level); }
    @Override protected Item launcher() { return ModItems.IGLA_9K38.get(); }
    @Override protected int fireInterval() { return 145; }
    @Override protected float engagementRange() { return 125f; }
    @Override protected boolean canEngage(LivingEntity target) {
        return target.isAlive() && target.getVehicle() instanceof VehicleEntity v
            && (v.getVehicleType() == VehicleType.HELICOPTER || v.getVehicleType() == VehicleType.PLANE);
    }
}
