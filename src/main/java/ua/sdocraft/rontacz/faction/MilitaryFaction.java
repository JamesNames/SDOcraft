package ua.sdocraft.rontacz.faction;

import net.minecraft.ChatFormatting;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import ua.sdocraft.rontacz.entity.RiflemanUnit;
import ua.sdocraft.rontacz.entity.MachineGunnerUnit;
import ua.sdocraft.rontacz.entity.SniperUnit;
import ua.sdocraft.rontacz.entity.Mi28CrewUnit;
import ua.sdocraft.rontacz.entity.TankCrewUnit;

import javax.annotation.Nullable;

/**
 * Two independent military sides hosted inside Reign of Nether's Villagers roster.
 * Stored as persistent vanilla scoreboard teams, so names/colors persist after restart.
 * RoN itself continues to decide combat hostility through the unit ownerName and alliances.
 */
public final class MilitaryFaction {
    private MilitaryFaction() {}

    public enum Side {
        VNR("ВНР", "rontacz_vnr", 0xCD7F32, ChatFormatting.GOLD),
        VFSM("ВФСМ", "rontacz_vfsm", 0x57C7FF, ChatFormatting.AQUA);

        public final String name;
        public final String team;
        public final int rgb;
        public final ChatFormatting formatting;
        Side(String name, String team, int rgb, ChatFormatting formatting) {
            this.name = name; this.team = team; this.rgb = rgb; this.formatting = formatting;
        }
    }

    public static PlayerTeam getOrMakeTeam(ServerLevel world, Side side) {
        Scoreboard scoreboard = world.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(side.team);
        if (team == null) team = scoreboard.addPlayerTeam(side.team);
        team.setColor(side.formatting);
        team.setAllowFriendlyFire(false);
        return team;
    }

    @Nullable
    public static Side sideOf(net.minecraft.world.level.Level world, String owner) {
        if (owner == null || owner.isBlank()) return null;
        PlayerTeam team = world.getScoreboard().getPlayersTeam(owner);
        if (team == null) return null;
        for (Side side : Side.values()) if (side.team.equals(team.getName())) return side;
        return null;
    }

    public static void setPlayerSide(ServerLevel world, String playerName, Side side) {
        Scoreboard scoreboard = world.getScoreboard();
        scoreboard.addPlayerToTeam(playerName, getOrMakeTeam(world, side));
    }

    public static boolean canRecruit(net.minecraft.world.level.Level level, String owner, String vehicleId) {
        Side side = sideOf(level, owner);
        if (side == null) return true; // backwards compatible with v0.3 saves and sandbox
        return switch (vehicleId) {
            case "m1a2" -> side == Side.VFSM;
            case "t90a" -> side == Side.VNR;
            default -> true; // Mi-28 remains available to both
        };
    }

    private static int defaultGrade(Entity e) {
        if (e instanceof Mi28CrewUnit) return 5;
        if (e instanceof TankCrewUnit) return 4;
        if (e instanceof SniperUnit) return 3;
        if (e instanceof MachineGunnerUnit) return 2;
        return 0;
    }

    public static String rank(Side side, int grade) {
        String[] regular = {
            "Рядовой", "Ефрейтор", "Младший сержант", "Сержант",
            "Старший сержант", "Лейтенант", "Капитан", "Майор"
        };
        String[] rebels = {
            "Новобранец", "Повстанец", "Боец отряда", "Опытный боец",
            "Командир группы", "Полевой командир", "Командир отряда", "Лидер сопротивления"
        };
        String[] ranks = side == Side.VFSM ? regular : rebels;
        return ranks[Math.max(0, Math.min(grade, ranks.length-1))];
    }

    public static String role(Entity e) {
        if (e instanceof Mi28CrewUnit) return "Пилот Ми-28";
        if (e instanceof TankCrewUnit) return "Танкист";
        if (e instanceof SniperUnit) return "Снайпер";
        if (e instanceof MachineGunnerUnit) return "Пулемётчик";
        return "Стрелок";
    }

    public static void applyToUnit(ServerLevel world, RiflemanUnit unit) {
        Side side = sideOf(world, unit.getOwnerName());
        // Even when no side is chosen, military infantry receive real Superb
        // Warfare armor. Default to neutral-grey pants with US-pattern gear.
        if (side == null) {
            if (!(unit instanceof TankCrewUnit))
                equipInfantryArmor(unit, Side.VFSM, 0x76818B);
            return;
        }

        Scoreboard sb = world.getScoreboard();
        sb.addPlayerToTeam(unit.getScoreboardName(), getOrMakeTeam(world, side));
        final String marker = "rontacz_military_grade";
        if (!unit.getPersistentData().contains(marker))
            unit.getPersistentData().putInt(marker, defaultGrade(unit));
        int grade = unit.getPersistentData().getInt(marker);
        unit.setCustomName(Component.literal(side.name + " | " + rank(side, grade) + " | " + role(unit))
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(side.rgb))));
        unit.setCustomNameVisible(true);

        // Tank/air crews keep their existing color-coded lightweight uniform.
        // Infantry get real SBW 3D ballistic helmets and chest plates.
        if (unit instanceof TankCrewUnit) {
            applyLeather(unit, EquipmentSlot.HEAD, Items.LEATHER_HELMET, side.rgb);
            applyLeather(unit, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, side.rgb);
            applyLeather(unit, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, side.rgb);
            applyLeather(unit, EquipmentSlot.FEET, Items.LEATHER_BOOTS, side.rgb);
        } else {
            equipInfantryArmor(unit, side, side.rgb);
        }
    }

    private static void equipInfantryArmor(RiflemanUnit unit, Side side, int color) {
        // Version-pinned item registrations from Superb Warfare 0.8.9.2.
        Item helmet = side == Side.VNR
            ? ModItems.RU_HELMET_6B47.get() : ModItems.US_HELMET_PASGT.get();
        Item chest = side == Side.VNR
            ? ModItems.RU_CHEST_6B43.get() : ModItems.US_CHEST_IOTV.get();

        // Preserve armor damage when the user is promoted or the same team is
        // reselected; only swap equipment on a genuinely different uniform.
        if (!unit.getItemBySlot(EquipmentSlot.HEAD).is(helmet))
            unit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        if (!unit.getItemBySlot(EquipmentSlot.CHEST).is(chest))
            unit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));

        // SBW helmet/chest textures are fixed, so use dyed trousers/boots for
        // visible bronze/cyan faction identity without custom model overrides.
        applyLeather(unit, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, color);
        applyLeather(unit, EquipmentSlot.FEET, Items.LEATHER_BOOTS, color);
    }

    private static void applyLeather(RiflemanUnit unit, EquipmentSlot slot, Item armorItem, int rgb) {
        ItemStack stack = unit.getItemBySlot(slot);
        if (!stack.is(armorItem)) stack = new ItemStack(armorItem);
        if (stack.getItem() instanceof DyeableLeatherItem dye)
            dye.setColor(stack, rgb);
        unit.setItemSlot(slot, stack);
    }

    public static void applyToVehicle(ServerLevel world, Entity vehicle, String owner, String vehicleId) {
        Side side = sideOf(world, owner);
        if (side == null) return;
        world.getScoreboard().addPlayerToTeam(vehicle.getScoreboardName(), getOrMakeTeam(world, side));
        vehicle.setCustomName(Component.literal(side.name + " | " + vehicleId.toUpperCase())
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(side.rgb))));
        vehicle.setCustomNameVisible(true);
    }

    public static boolean promote(ServerLevel world, RiflemanUnit unit) {
        Side side = sideOf(world, unit.getOwnerName());
        if (side == null) return false;
        String tag = "rontacz_military_grade";
        int grade = unit.getPersistentData().contains(tag)
            ? unit.getPersistentData().getInt(tag) : defaultGrade(unit);
        if (grade >= 7) return false;
        unit.getPersistentData().putInt(tag, grade + 1);
        applyToUnit(world, unit);
        return true;
    }
}
