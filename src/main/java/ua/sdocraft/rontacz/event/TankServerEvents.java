package ua.sdocraft.rontacz.event;

import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.entity.TankCrewUnit;
import ua.sdocraft.rontacz.entity.TankMoveGoal;
import ua.sdocraft.rontacz.entity.RTSTankCombatController;

import java.util.ArrayList;

/** Always drive SBW vehicles even when Minecraft suppresses a mounted mob's AI tick. */
@Mod.EventBusSubscriber(modid = RonTacZ.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TankServerEvents {
    private TankServerEvents() { }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel server)) return;
        for (LivingEntity unit : new ArrayList<>(UnitServerEvents.getAllUnits())) {
            if (unit instanceof TankCrewUnit crew
                    && crew.level() == server && crew.isAlive()
                    && crew.getMoveGoal() instanceof TankMoveGoal goal) {
                goal.driveTank();
                RTSTankCombatController.tick(crew);
            }
        }
    }
}
