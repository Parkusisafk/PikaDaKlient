package com.pikadaklient.utils;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.MinecraftClient;

public final class RotationUtils {
    private RotationUtils() {}

    // Convert a cardinal Direction to the usual Minecraft yaw (degrees)
    // SOUTH = 0, WEST = 90, NORTH = 180, EAST = 270
    public static float dirToYaw(Direction d) {
        switch (d) {
            case SOUTH: return 0.0f;
            case WEST:  return 90.0f;
            case NORTH: return 180.0f;
            case EAST:  return 270.0f;
            default:    return 0.0f;
        }
    }

    // Convert a yaw (degrees) to the nearest cardinal Direction.
    // Uses the classic floor((yaw * 4 / 360) + 0.5) & 3 mapping.
    public static Direction yawToDirection(float yaw) {
        int i = MathHelper.floor((yaw * 4.0F / 360.0F) + 0.5F) & 3;
        switch (i) {
            case 0: return Direction.SOUTH;
            case 1: return Direction.WEST;
            case 2: return Direction.NORTH;
            default: return Direction.EAST;
        }
    }

    // Wraps difference into -180..180
    public static float wrapDegrees(float deg) {
        return MathHelper.wrapDegrees(deg);
    }

    // Smoothly rotate player yaw toward targetYaw by up to maxDelta degrees this tick
    // (positive maxDelta rotates fastest; small value = slower/smoother)
    public static void smoothTurnYaw(MinecraftClient mc, float targetYaw, float maxDelta) {
        if (mc.player == null) return;
        float current = mc.player.getYaw();
        float diff = wrapDegrees(targetYaw - current);

        if (Math.abs(diff) <= maxDelta) {
            mc.player.setYaw(targetYaw);
        } else {
            mc.player.setYaw(current + Math.signum(diff) * maxDelta);
        }
    }
}
