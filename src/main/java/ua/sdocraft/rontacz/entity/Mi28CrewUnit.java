package ua.sdocraft.rontacz.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

/** Selectable Reign of Nether pilot for the real Superb Warfare Mi-28 entity. */
public final class Mi28CrewUnit extends TankCrewUnit {
    public Mi28CrewUnit(EntityType<? extends Pillager> type, Level level) { super(type, level); }
}
