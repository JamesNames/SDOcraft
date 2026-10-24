package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Anti-armor infantry with the original Superb Warfare RPG launcher. */
public final class RpgUnit extends MissileSpecialistUnit {
    public RpgUnit(EntityType<? extends Pillager> type, Level level) { super(type,level); }
    @Override protected Item launcher() { return ModItems.RPG.get(); }
    @Override protected int fireInterval() { return 110; }
    @Override protected float engagementRange() { return 55f; }
}
