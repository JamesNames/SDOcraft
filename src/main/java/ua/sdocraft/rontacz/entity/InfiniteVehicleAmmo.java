package ua.sdocraft.rontacz.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.world.item.ItemStack;
import ua.sdocraft.rontacz.RonTacZ;

/**
 * An actual Superb Warfare Creative Ammo Box in vehicle inventory makes native
 * backpack-ammo weapons infinite, while all magazine-fed guns are refilled.
 * Keeps original projectile types, reload/heat gating and weapon mechanics.
 */
public final class InfiniteVehicleAmmo {
    private InfiniteVehicleAmmo() { }

    public static void tick(VehicleEntity vehicle, long tick) {
        if (vehicle.isWreck()) return;
        if (tick % 20 != Math.floorMod(vehicle.getId(), 20)) return;

        var inv=vehicle.getInventory();
        boolean present=false;
        int empty=-1;
        for (int i=0;i<inv.getSlots();i++) {
            ItemStack stack=inv.getStackInSlot(i);
            if (stack.is(ModItems.CREATIVE_AMMO_BOX.get())) {
                present=true;
                break;
            }
            if (stack.isEmpty() && empty < 0) empty=i;
        }
        if (!present) {
            if (empty>=0) {
                inv.setStackInSlot(empty,new ItemStack(ModItems.CREATIVE_AMMO_BOX.get()));
                RonTacZ.LOGGER.info("RTS infinite ammo box installed: vehicle={} slot={}",vehicle.getId(),empty);
            } else {
                RonTacZ.LOGGER.warn("RTS infinite ammo: vehicle={} has no free inventory slot",vehicle.getId());
            }
        }

        refill(vehicle,"Cannon",1);
        refill(vehicle,"MachineGun",100);
        refill(vehicle,"PassengerMachineGun",100);
        refill(vehicle,"Rocket",42);
        refill(vehicle,"DriverMissile",4);
        refill(vehicle,"DriverAAMissile",4);
        refill(vehicle,"PassengerMissile",8);
        refill(vehicle,"SeekMissile",2);
    }

    private static void refill(VehicleEntity vehicle, String weapon, int maxAmmo) {
        if (vehicle.getGunData(weapon) == null) return;
        vehicle.modifyGunData(weapon,data->{
            if (!data.useBackpackAmmo() && data.ammo.get()<maxAmmo)
                data.ammo.set(maxAmmo);
        });
    }
}
