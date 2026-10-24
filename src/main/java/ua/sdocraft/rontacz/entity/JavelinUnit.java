package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Guided FGM-148 Javelin using the native Superb Warfare missile entity. */
public final class JavelinUnit extends MissileSpecialistUnit {
    public JavelinUnit(EntityType<? extends Pillager> type, Level level) { super(type,level); }
    @Override protected Item launcher() { return ModItems.JAVELIN.get(); }
    @Override protected int fireInterval() { return 165; }
    @Override protected float engagementRange() { return 110f; }
}
