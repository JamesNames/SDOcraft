package ua.sdocraft.rontacz.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

/** Reign of Nether crew for the real Superb Warfare T-90A entity. */
public final class T90CrewUnit extends TankCrewUnit {
    public T90CrewUnit(EntityType<? extends Pillager> type, Level level) { super(type, level); }
}
