package com.pikadaklient.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoClickerUtils {

    private static boolean running = false;

    // Call this to start the autoclicker
    public static void start() {
        running = true;
    }

    // Call this to stop the autoclicker
    public static void stop() {
        running = false;
    }

    // Call this every client tick
    public static void tick(MinecraftClient mc) {
        if (!running || mc.player == null || mc.world == null) return;

        // Always "hold" the attack key
        mc.options.attackKey.setPressed(true);

        // Also perform the actual attack action (blocks/entities)
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit) {
            mc.interactionManager.attackBlock(blockHit.getBlockPos(), blockHit.getSide());
        } else {
            // Attack entity if target is an entity
            mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
        }

        // Swing the hand visually
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public static boolean isRunning() {
        return running;
    }
}
