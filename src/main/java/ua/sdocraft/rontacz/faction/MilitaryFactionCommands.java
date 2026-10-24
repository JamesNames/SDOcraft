package ua.sdocraft.rontacz.faction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.sdocraft.rontacz.RonTacZ;
import ua.sdocraft.rontacz.entity.RiflemanUnit;
import ua.sdocraft.rontacz.faction.MilitaryFaction.Side;

/** /rontacz faction vnr | vfsm | status, /rontacz promote <entityId> */
@Mod.EventBusSubscriber(modid = RonTacZ.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MilitaryFactionCommands {
    private MilitaryFactionCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rontacz")
            .then(Commands.literal("faction")
                .then(Commands.literal("vnr").executes(c -> pick(c.getSource(), Side.VNR)))
                .then(Commands.literal("vfsm").executes(c -> pick(c.getSource(), Side.VFSM)))
                .then(Commands.literal("status").executes(c -> status(c.getSource()))))
            .then(Commands.literal("promote")
                .then(Commands.argument("entityId", IntegerArgumentType.integer(1))
                    .executes(c -> promote(c.getSource(), IntegerArgumentType.getInteger(c, "entityId"))))));
    }

    private static int pick(CommandSourceStack source, Side side) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = player.serverLevel();
        MilitaryFaction.setPlayerSide(world, player.getGameProfile().getName(), side);

        int count = 0;
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (entity instanceof RiflemanUnit soldier
                && soldier.level() == world
                && soldier.getOwnerName().equals(player.getGameProfile().getName())) {
                MilitaryFaction.applyToUnit(world, soldier);
                if (soldier.getVehicle() != null) {
                    String type = soldier.getVehicle().getType().toString();
                    MilitaryFaction.applyToVehicle(world, soldier.getVehicle(), soldier.getOwnerName(), type);
                }
                count++;
            }
        }
        source.sendSuccess(() -> Component.literal(
            "Сторона: " + side.name + ". Перекрашено юнитов: " + count +
            ". ВФСМ: Abrams, ВНР: T-90A. Ми-28 доступен обеим сторонам."), false);
        return 1;
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Side side = MilitaryFaction.sideOf(player.level(), player.getGameProfile().getName());
        source.sendSuccess(() -> Component.literal("Твоя сторона: " +
            (side == null ? "не выбрана. /rontacz faction vnr или vfsm" : side.name)), false);
        return 1;
    }

    private static int promote(CommandSourceStack source, int entityId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        for (ServerLevel world : player.server.getAllLevels()) {
            Entity entity = world.getEntity(entityId);
            if (entity instanceof RiflemanUnit soldier
                && (soldier.getOwnerName().equals(player.getGameProfile().getName()) || player.hasPermissions(2))) {
                boolean didPromote = MilitaryFaction.promote(world, soldier);
                source.sendSuccess(() -> Component.literal(didPromote
                    ? "Повышено звание: " + soldier.getName().getString()
                    : "Максимальное звание или сторона не выбрана"), false);
                return didPromote ? 1 : 0;
            }
        }
        source.sendFailure(Component.literal("Юнит " + entityId + " не найден или не принадлежит тебе"));
        return 0;
    }
}
