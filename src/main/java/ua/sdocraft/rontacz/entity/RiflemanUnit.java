package ua.sdocraft.rontacz.entity;

import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ua.sdocraft.rontacz.RonTacZ;

public class RiflemanUnit extends PillagerUnit {
    public static final int MAGAZINE = 30;
    private static final int RELOAD_TICKS = 50;
    private UnitRangedAttackGoal<RiflemanUnit> gunGoal;
    private boolean gunDrawn;
    private int reloadCountdown;

    public RiflemanUnit(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
    }

    protected String gunName() { return "ak47"; }
    protected FireMode gunFireMode() { return FireMode.AUTO; }
    protected int magazineSize() { return MAGAZINE; }
    protected float gunRange() { return 28.0f; }
    protected float shotCooldown() { return 5.0f; }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        Goal crossbowGoal = super.getAttackGoal();
        if (crossbowGoal != null) this.goalSelector.removeGoal(crossbowGoal);
        this.gunGoal = new UnitRangedAttackGoal<>(this, 1);
        this.goalSelector.addGoal(2, gunGoal);
    }

    @Override
    public Goal getAttackGoal() {
        return gunGoal != null ? gunGoal : super.getAttackGoal();
    }
    @Override
    public float getAttackCooldown() {
        return shotCooldown() * getAttackCooldownMultiplier();
    }
    @Override
    public float getNonBaseAttacksPerSecond() { return 20f / Math.max(1,shotCooldown()); }
    @Override
    public float getAttacksPerSecond() {
        return 20.0f / Math.max(1.0f, getAttackCooldown());
    }
    @Override
    public float getAttackRange() { return gunRange(); }

    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (this.level().isClientSide()) return;
        if (getMainHandItem().getItem() instanceof IGun) return;
        ItemStack gun = GunItemBuilder.create()
          .setId(new ResourceLocation("tacz", gunName()))
          .setAmmoCount(magazineSize())
          .setAmmoInBarrel(true)
          .setFireMode(gunFireMode())
          .build();
        if (gun.isEmpty()) {
            RonTacZ.LOGGER.error("TaCZ AK-47 missing");
            return;
        }
        setItemSlot(EquipmentSlot.MAINHAND, gun);
        gunDrawn = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        ItemStack gun = getMainHandItem();
        if (!(gun.getItem() instanceof IGun iGun)) return;
        IGunOperator operator = IGunOperator.fromLivingEntity(this);
        if (!gunDrawn) {
            operator.draw(this::getMainHandItem);
            gunDrawn = true;
        }
        if (reloadCountdown > 0 && --reloadCountdown == 0) {
            iGun.setCurrentAmmoCount(gun, magazineSize());
        }
    }

    @Override
    public void performUnitRangedAttack(LivingEntity enemy, float velocity) {
        if (level().isClientSide() || enemy == null || !enemy.isAlive()) return;
        if (reloadCountdown > 0) return;
        ItemStack stack = getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) return;
        if (iGun.getCurrentAmmoCount(stack) <= 0 && !iGun.hasBulletInBarrel(stack)) {
            reloadCountdown = RELOAD_TICKS;
            return;
        }
        if (!getSensing().hasLineOfSight(enemy)) return;
        Vec3 delta = enemy.getEyePosition().subtract(getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(
                 Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        ShootResult shot = IGunOperator.fromLivingEntity(this).shoot(() -> pitch, () -> yaw);
        if (shot == ShootResult.NO_AMMO) reloadCountdown = RELOAD_TICKS;
        else if (shot == ShootResult.NEED_BOLT) IGunOperator.fromLivingEntity(this).bolt();
    }
}
