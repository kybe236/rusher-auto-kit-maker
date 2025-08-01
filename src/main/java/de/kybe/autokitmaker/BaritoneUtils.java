package de.kybe.autokitmaker;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.rusherhack.client.api.utils.ChatUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.rusherhack.client.api.Globals.mc;

public class BaritoneUtils {
    public static IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    public static void goToExact(BlockPos pos) {
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
    }

    public static void goToClose(BlockPos pos) {
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 3));
    }

    public static void gotoChest(BlockPos chest) {
        if (mc.level == null || mc.player == null) return;

        List<Direction> dirs = List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);
        int playerY = mc.player.blockPosition().getY();
        int chestY = chest.getY();

        List<Integer> offsets = new ArrayList<>();

        int maxUpOffset = 3;
        int maxDownOffset = 6;

        offsets.add(playerY - chestY);

        if (chestY < playerY) {
            for (int i = 1; i <= maxUpOffset; i++) offsets.add(i);
            for (int i = 1; i <= maxDownOffset; i++) offsets.add(-i);
        } else {
            for (int i = 1; i <= maxDownOffset; i++) offsets.add(-i);
            for (int i = 1; i <= maxUpOffset; i++) offsets.add(i);
        }

        offsets = offsets.stream().filter(o -> o >= -6 && o <= 6).toList();

        for (int offset : offsets) {
            for (Direction dir : dirs) {
                BlockPos neighbor = chest.relative(dir);
                BlockPos below = neighbor.offset(0, offset, 0);
                BlockPos above1 = below.above();
                BlockPos above2 = above1.above();

                if (!mc.level.getBlockState(below).isAir()
                        && mc.level.getBlockState(above1).isAir()
                        && mc.level.getBlockState(above2).isAir()) {

                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(above1));
                    return;
                }
            }
        }

        ChatUtils.print("Failed to Navigate to Chest");
    }

    public static void gotoDoubleChest(BlockPos chest1, BlockPos chest2) {
        if (mc.level == null || mc.player == null) return;

        List<Direction> dirs = List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);
        int playerY = mc.player.blockPosition().getY();
        int chestY = Math.min(chest1.getY(), chest2.getY());

        List<Integer> offsets = new ArrayList<>();

        int maxUpOffset = 3;
        int maxDownOffset = 6;

        offsets.add(playerY - chestY);

        if (chestY < playerY) {
            for (int i = 1; i <= maxUpOffset; i++) offsets.add(i);
            for (int i = 1; i <= maxDownOffset; i++) offsets.add(-i);
        } else {
            for (int i = 1; i <= maxDownOffset; i++) offsets.add(-i);
            for (int i = 1; i <= maxUpOffset; i++) offsets.add(i);
        }

        offsets = offsets.stream().filter(o -> o >= -6 && o <= 6).toList();

        Set<BlockPos> neighbors = new LinkedHashSet<>();
        for (Direction dir : dirs) {
            BlockPos n1 = chest1.relative(dir);
            if (!n1.equals(chest2)) neighbors.add(n1);
            BlockPos n2 = chest2.relative(dir);
            if (!n2.equals(chest1)) neighbors.add(n2);
        }

        for (int offset : offsets) {
            for (BlockPos neighbor : neighbors) {
                BlockPos below = neighbor.offset(0, offset, 0);
                BlockPos above1 = below.above();
                BlockPos above2 = above1.above();

                if (!mc.level.getBlockState(below).isAir()
                        && mc.level.getBlockState(above1).isAir()
                        && mc.level.getBlockState(above2).isAir()) {

                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(above1));
                    return;
                }
            }
        }

        ChatUtils.print("Failed to Navigate to Double Chest");
    }


    public static boolean isBaritonePathing() {
        return baritone.getPathingBehavior().isPathing();
    }

    public static void stopBaritone() {
        baritone.getPathingBehavior().cancelEverything();
    }
}
