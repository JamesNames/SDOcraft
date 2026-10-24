package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ua.sdocraft.rontacz.RonTacZ;

/**
 * Real Superb Warfare RPG, 9K38 Igla and Javelin infantry inside RoN RTS.
 * Projectiles are created by the real SBW GunData / launcher gun item.
 * Targeting and move orders remain RoN-native, never automatic target scan.
 */
public abstract class MissileSpecialistUnit extends RiflemanUnit {
    protected MissileSpecialistUnit(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
    }

    protected abstract Item launcher();
    protected abstract int fireInterval();
    protected abstract float engagementRange();
    protected boolean canEngage(LivingEntity target) { return target.isAlive(); }

    @Override protected float gunRange() { return engagementRange(); }
    @Override protected float shotCooldown() { return fireInterval(); }

    @Override public void setupEquipmentAndUpgradesServer() {
        if (level().isClientSide()) return;
        if (!getMainHandItem().is(launcher())) {
            ItemStack stack=new ItemStack(launcher());
            GunData gun=GunData.from(stack);
            if (!gun.initialized()) gun.initialize();
            gun.ammo.set(1);
            gun.save();
            setItemSlot(EquipmentSlot.MAINHAND,stack);
        }
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (tickCount % 80 == Math.floorMod(getId(), 80)) {
            if (!getMainHandItem().is(launcher())) {
                setupEquipmentAndUpgradesServer();
            } else {
                GunData gun=GunData.from(getMainHandItem());
                if (!gun.initialized()) gun.initialize();
                if (gun.ammo.get() <= 0) {
                    gun.ammo.set(1); // RTS sandbox unlimited rockets: 1 per interval
                    gun.save();
                }
            }
        }
    }

    @Override public void performUnitRangedAttack(LivingEntity target, float velocity) {
        if (level().isClientSide() || target==null || !canEngage(target)) return;
        if (!getSensing().hasLineOfSight(target)) return;
        ItemStack stack=getMainHandItem();
        if (!stack.is(launcher())) return;
        GunData gun=GunData.from(stack);
        if (!gun.initialized()) gun.initialize();
        if (gun.ammo.get() <= 0) gun.ammo.set(1);

        // Aim the physical launcher before SBW computes shooter's look direction.
        var victim=target.getVehicle() instanceof VehicleEntity veh ? veh : target;
        Vec3 aim=victim.getBoundingBox().getCenter();
        Vec3 delta=aim.subtract(getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot=yaw;
        setXRot(pitch);

        if (!gun.canShoot(this)) {
            if (tickCount % 100 == 0)
                RonTacZ.LOGGER.info("RTS launcher {} waiting: gun={} ammo={} target={}",
                    getId(),launcher(),gun.ammo.get(),victim.getId());
            return;
        }
        // RPG uses direct ballistics, Igla/Javelin create genuine guided missiles
        // with an entity lock and the clicked target's actual vehicle center.
        gun.shoot(this,0.0,true,victim.getUUID(),aim);
        gun.save();
        RonTacZ.LOGGER.info("RTS {} fired real SBW launcher at entity={}", getName().getString(),victim.getId());
    }
}
