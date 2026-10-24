package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.buildings.villagers.Blacksmith;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/** Rename the forge build button to Hangar without changing saved building IDs. */
@Mixin(value=Blacksmith.class,remap=false)
public abstract class VehicleHangarLabelMixin {
    @Inject(method="getBuildButton",at=@At("RETURN"),remap=false)
    private void rontacz$labelHangar(Keybinding hotkey,
                                     CallbackInfoReturnable<BuildingPlaceButton> cir) {
        BuildingPlaceButton button=cir.getReturnValue();
        if(button==null)return;
        button.name=Component.translatable("buildings.rontacz.vehicle_hangar").getString();
        button.iconResource=ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/block/iron_block.png");
        if(button.tooltipLines!=null) {
            ArrayList<FormattedCharSequence> tip=new ArrayList<>();
            tip.add(Component.translatable("buildings.rontacz.vehicle_hangar").getVisualOrderText());
            tip.add(Component.translatable("buildings.rontacz.vehicle_hangar.tooltip").getVisualOrderText());
            tip.addAll(button.tooltipLines);
            button.tooltipLines=tip;
        }
    }
}
