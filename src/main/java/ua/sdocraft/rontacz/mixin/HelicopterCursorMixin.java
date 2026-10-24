package ua.sdocraft.rontacz.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.sdocraft.rontacz.entity.Mi28CrewUnit;

/** Find RTS pilots by ray hit on the aircraft, even >30 blocks above the ground cursor. */
@Mixin(value = CursorClientEvents.class, remap = false)
public abstract class HelicopterCursorMixin {
    @Inject(method="onDrawScreen", at=@At("TAIL"), remap=false)
    private static void rontacz$findAircraftPilot(ScreenEvent.Render evt, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !OrthoviewClientEvents.isEnabled()) return;
        if (!UnitClientEvents.getPreselectedUnits().isEmpty()) return;

        Vector3d ground = CursorClientEvents.getCursorWorldPos();
        Vector3d look = MiscUtil.getPlayerLookVector(mc);
        Vec3 near = new Vec3(ground.x - look.x*200, ground.y - look.y*200, ground.z - look.z*200);
        Vec3 far = new Vec3(ground.x + look.x*200, ground.y + look.y*200, ground.z + look.z*200);

        Mi28CrewUnit best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (var entity : UnitClientEvents.getAllUnits()) {
            if (!(entity instanceof Mi28CrewUnit crew)) continue;
            if (!(crew.getVehicle() instanceof VehicleEntity heli)) continue;
            AABB box = heli.getBoundingBox().inflate(1.2, 1.1, 1.2);
            var hit = box.clip(near, far);
            if (hit.isEmpty()) continue;
            double dist = hit.get().distanceToSqr(near);
            if (dist < nearest) { nearest = dist; best = crew; }
        }
        if (best != null) UnitClientEvents.addPreselectedUnit(best);
    }
}
