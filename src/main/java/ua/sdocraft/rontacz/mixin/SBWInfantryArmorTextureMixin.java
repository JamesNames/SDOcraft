package ua.sdocraft.rontacz.mixin;

import com.solegendary.reignofnether.unit.modelling.layers.VillagerUnitArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * RoN's custom armor layer requests a vanilla material texture even for
 * Superb Warfare Bedrock/Geo armor models. Give it the real SBW armor texture.
 */
@Mixin(value=VillagerUnitArmorLayer.class, remap=false)
public abstract class SBWInfantryArmorTextureMixin {
    private static final Map<String, String> TEXTURES = Map.of(
        "us_helmet_pasgt", "us_helmet_pasgt",
        "us_chest_iotv", "us_chest_iotv",
        "ru_helmet_6b47", "ru_helmet_6b47",
        "ru_chest_6b43", "ru_chest_6b43"
    );

    @Inject(method="getArmorResource", at=@At("HEAD"), cancellable=true, remap=false)
    private void rontacz$armorTexture(Entity entity, ItemStack stack, EquipmentSlot slot,
                                       @Nullable String type, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("superbwarfare")) return;
        String file=TEXTURES.get(id.getPath());
        if (file != null) cir.setReturnValue(new ResourceLocation("superbwarfare",
            "textures/bedrock/armor/" + file + ".png"));
    }
}
