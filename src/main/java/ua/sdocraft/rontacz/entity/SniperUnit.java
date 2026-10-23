package ua.sdocraft.rontacz.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

/** Precision AWM infantry; inherits native Reign of Nether targeting and orders. */
public final class SniperUnit extends RiflemanUnit {
    public SniperUnit(EntityType<? extends Pillager> type, Level level) { super(type, level); }
    @Override protected String gunName() { return "ai_awp"; }
    @Override protected int magazineSize() { return 10; }
    @Override protected float gunRange() { return 60f; }
    @Override protected float shotCooldown() { return 35f; }
}
