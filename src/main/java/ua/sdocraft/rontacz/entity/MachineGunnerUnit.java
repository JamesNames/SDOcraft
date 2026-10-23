package ua.sdocraft.rontacz.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

/** M249 automatic rifleman with large magazine. */
public final class MachineGunnerUnit extends RiflemanUnit {
    public MachineGunnerUnit(EntityType<? extends Pillager> type, Level level) { super(type, level); }
    @Override protected String gunName() { return "m249"; }
    @Override protected int magazineSize() { return 100; }
    @Override protected float gunRange() { return 35f; }
    @Override protected float shotCooldown() { return 3f; }
}
