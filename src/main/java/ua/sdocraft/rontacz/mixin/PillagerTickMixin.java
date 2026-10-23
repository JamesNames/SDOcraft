package ua.sdocraft.rontacz.mixin;
import com.solegendary.reignofnether.unit.goals.UnitCrossbowAttackGoal;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.sdocraft.rontacz.entity.RiflemanUnit;

@Mixin(PillagerUnit.class)
public abstract class PillagerTickMixin {
    @Redirect(method = "tick",
        at = @At(value = "INVOKE",
            target = "Lcom/solegendary/reignofnether/unit/goals/UnitCrossbowAttackGoal;tickChargeCrossbow()V",
            remap = false))
    private void rontacz$noCrossbowChargeForRifleman(UnitCrossbowAttackGoal<?> goal) {
        if (!((Object)this instanceof RiflemanUnit)) goal.tickChargeCrossbow();
    }
}
