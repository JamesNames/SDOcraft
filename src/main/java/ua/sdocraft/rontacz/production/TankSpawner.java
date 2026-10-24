package ua.sdocraft.rontacz.production;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.entity.TankCrewUnit;
import ua.sdocraft.rontacz.faction.MilitaryFaction;

/** Spawn native Superb Warfare vehicles and mount a selectable Reign of Nether driver. */
public final class TankSpawner {
    private TankSpawner() { }

    public static void spawnTank(ServerLevel world, TankCrewUnit commander) {
        spawnVehicle(world, commander, "m1a2");
    }

    public static boolean spawnVehicle(ServerLevel world, TankCrewUnit commander, String kind) {
        if (!MilitaryFaction.canRecruit(world, commander.getOwnerName(), kind)) {
            RonTacZ.LOGGER.warn("Faction {} may not recruit {}", commander.getOwnerName(), kind);
            return false;
        }
        EntityType<?> type = switch (kind) {
            case "m1a2" -> ModEntities.M_1A_2.get();
            case "t90a" -> ModEntities.T_90A.get();
            case "mi28" -> ModEntities.MI_28.get();
            case "sodayo_tow" -> ModEntities.SODAYO_PICK_UP_TOW.get();
            default -> null;
        };
        if (type == null) {
            RonTacZ.LOGGER.error("Unknown SBW RTS vehicle {}", kind);
            return false;
        }
        Entity entity = type.create(world);
        if (!(entity instanceof VehicleEntity vehicle)) {
            RonTacZ.LOGGER.error("Vehicle kind {} could not spawn", kind);
            return false;
        }

        BlockPos pos = findPosition(world, commander.blockPosition(), "mi28".equals(kind));
        if (pos == null) {
            RonTacZ.LOGGER.error("No clear terrain for {} vehicle spawn near {}", kind,commander.blockPosition());
            return false;
        }
        vehicle.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
            commander.getYRot(), 0);
        vehicle.setEnergy(vehicle.getMaxEnergy());
        vehicle.setEngineStart(true);
        vehicle.setEngineStartOver(true);
        if ("mi28".equals(kind)) {
            vehicle.setHoverMode(false);
            // Do not launch before an RTS move order; the previous 0.16
            // rotor power triggered uncontrolled climbing immediately at spawn.
            vehicle.setPower(0f);
            vehicle.setEngineStart(false);
            vehicle.setEngineStartOver(false);
        }

        if (!world.addFreshEntity(vehicle)) {
            RonTacZ.LOGGER.error("Failed adding {} to the world", kind);
            return false;
        }
        MilitaryFaction.applyToVehicle(world, vehicle, commander.getOwnerName(), kind);
        if (!commander.startRiding(vehicle, true)) {
            RonTacZ.LOGGER.error("RTS commander {} could not mount {}", commander.getId(), kind);
            vehicle.discard();
            return false;
        }
        RonTacZ.LOGGER.info("RTS {} spawned at {} with commander={} owner={} seat={}",
            kind, vehicle.blockPosition(), commander.getId(),
            commander.getOwnerName(), vehicle.getSeatIndex(commander));
        return true;
    }

    private static BlockPos findPosition(ServerLevel world, BlockPos near, boolean helicopter) {
        int xClearance = helicopter ? 5 : 3;
        int zClearance = helicopter ? 5 : 4;
        int height = helicopter ? 7 : 4;
        for (int radius = 14; radius <= 38; radius += 4) {
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6;
                BlockPos p = near.offset(
                    (int) Math.round(radius * Math.cos(angle)), 0,
                    (int) Math.round(radius * Math.sin(angle)));
                p=world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p);
                if (isClear(world, p, xClearance, zClearance, height))
                    return p;
            }
        }
        RonTacZ.LOGGER.warn("No unobstructed RTS vehicle spawn site near {}", near);
        return null;
    }

    private static boolean isClear(ServerLevel world, BlockPos center,
                                   int radiusX, int radiusZ, int height) {
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                BlockPos floor = center.offset(x, -1, z);
                if (!world.getBlockState(floor).isSolidRender(world, floor))
                    return false;
                for (int y = 0; y < height; y++) {
                    if (!world.getBlockState(center.offset(x, y, z)).isAir())
                        return false;
                }
            }
        }
        return true;
    }
}
