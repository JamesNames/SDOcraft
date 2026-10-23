package ua.sdocraft.rontacz.client;
import com.solegendary.reignofnether.unit.modelling.renderers.PillagerUnitRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.entity.ModEntities;

@Mod.EventBusSubscriber(modid = RonTacZ.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModClientEvents {
    @SuppressWarnings({"unchecked","rawtypes"})
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RIFLEMAN.get(), ctx -> (EntityRenderer) new PillagerUnitRenderer(ctx));
        event.registerEntityRenderer(ModEntities.SNIPER.get(), ctx -> (EntityRenderer) new PillagerUnitRenderer(ctx));
        event.registerEntityRenderer(ModEntities.MACHINE_GUNNER.get(), ctx -> (EntityRenderer) new PillagerUnitRenderer(ctx));
        event.registerEntityRenderer(ModEntities.TANK_CREW.get(), ctx -> (EntityRenderer) new PillagerUnitRenderer(ctx));
    }
}
