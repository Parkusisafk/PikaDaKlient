package com.pikadaklient.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

// NOTE: Ensure your utility file is named AutoWallMinerUtils.java and your Mixins reference this class name.

public class backup {

    private static boolean running = false;
    private static BlockPos targetCornerPos;
    private static int minY = -999;

    // Constants
    private static final int RESET_Y_THRESHOLD = 90;
    private static final int TARGET_SETUP_Y = 88;
    private static final float AIM_PITCH = -30.0f;
    private static final int DESCENT_BLOCKS = 3;
    private static final float TURN_SPEED = 15.0f;
    private static final int TURN_TICKS = 6;

    // State Variables
    private enum State {
        SURFACE_CHECK,
        SETUP_SCANNING,
        SETUP_FLYING,
        SETUP_DESCEND,
        LOOP_MOVE,
        LOOP_TURN,
        LOOP_END,
        DESCEND,
        AFK_RESET
    }
    private static State currentState = State.SURFACE_CHECK;
    private static Direction currentDir = Direction.NORTH;
    private static float turnTargetYaw;
    private static int turnTickCounter;
    private static int turnsCompleted = 0;
    private static double descentYStart = 0;

    public static void start() {
        if (MinecraftClient.getInstance().player == null) return;
        running = true;
        currentState = State.SURFACE_CHECK;
    }

    public static void stop() {
        running = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.attackKey != null) mc.options.attackKey.setPressed(false);
        if (mc.options.leftKey != null) mc.options.leftKey.setPressed(false);
        if (mc.options.sneakKey != null) mc.options.sneakKey.setPressed(false);
        if (mc.player != null) mc.player.getAbilities().flying = false;
    }

    public static void tick(MinecraftClient mc) {
        if (!running || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // --- 0. CAPTCHA HANDLER ---
        if (handleCaptcha(mc)) {
            mc.options.attackKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            return;
        }

        // --- 1. CORE FUNCTIONALITY: ATTACK ---
        mc.options.attackKey.setPressed(true);
        mc.player.swingHand(Hand.MAIN_HAND);

        // --- 2. STATE MACHINE EXECUTION ---
        switch (currentState) {

            case SURFACE_CHECK:
                if (mc.player.getY() >= RESET_Y_THRESHOLD) {
                    mc.player.getAbilities().flying = true;
                    mc.player.getAbilities().setFlySpeed(0.05f);
                    currentState = State.SETUP_SCANNING;
                    minY = detectMinY(mc);
                } else if (mc.player.getY() <= minY + DESCENT_BLOCKS + 1) {
                    currentState = State.AFK_RESET;
                } else {
                    currentState = State.LOOP_MOVE;
                }
                break;

            case SETUP_SCANNING:
                if (findCorner(mc)) {
                    currentState = State.SETUP_FLYING;
                }
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(false);
                break;

            case SETUP_FLYING:
                Vec3d targetCenter = Vec3d.ofCenter(targetCornerPos).withAxis(Direction.Axis.Y, mc.player.getY());
                moveTowards(mc, targetCenter, true, false);

                if (mc.player.getPos().distanceTo(targetCenter) < 2.0) {
                    mc.player.setVelocity(Vec3d.ZERO);
                    currentState = State.SETUP_DESCEND;
                }
                break;

            case SETUP_DESCEND:
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(true);

                // FIX: Use the stable method getHorizontalDegreesOrThrow()
                mc.player.setYaw(mc.player.getYaw() + 5.0f);
                mc.player.setPitch(AIM_PITCH);

                if (mc.player.getY() <= TARGET_SETUP_Y + 0.5) {
                    mc.options.sneakKey.setPressed(false);
                    mc.player.setVelocity(Vec3d.ZERO);
                    turnsCompleted = 0;
                    turnTickCounter = 0;
                    currentState = State.LOOP_MOVE;
                }
                break;

            case LOOP_MOVE:
                mc.options.leftKey.setPressed(true);

                // Corner Detection: Check if horizontal velocity is near zero
                if (mc.player.getVelocity().horizontalLength() < 0.01) {

                    // FIX: Use Direction.fromYaw(float) (this mapping is often stable for converting Yaw to Direction)
                    // If this fails later, we revert to the MathHelper.floor index logic.
// Get player yaw in radians
                    float yawRad = (float) Math.toRadians(mc.player.getYaw());

// Compute left offset (perpendicular)
                    int offsetX = MathHelper.floor(Math.cos(yawRad + Math.PI / 2)); // left X
                    int offsetZ = MathHelper.floor(Math.sin(yawRad + Math.PI / 2)); // left Z

                    BlockPos leftPos = mc.player.getBlockPos().add(offsetX, 0, offsetZ);
                    if(isBedrock(mc.world.getBlockState(leftPos))) {
                        mc.options.leftKey.setPressed(false);
                        turnTargetYaw = mc.player.getYaw() + 90.0f; // Turn right (clockwise)
                        turnTargetYaw %= 360;
                        turnTickCounter = 0;
                        currentState = State.LOOP_TURN;
                    }
                }
                break;

            case LOOP_TURN:
                float currentYaw = mc.player.getYaw();
                float yawDiff = turnTargetYaw - currentYaw;

                while (yawDiff < -180) yawDiff += 360;
                while (yawDiff > 180) yawDiff -= 360;

                float turnAmount = yawDiff / (TURN_TICKS - turnTickCounter);
                mc.player.setYaw(currentYaw + turnAmount);

                turnTickCounter++;

                if (turnTickCounter >= TURN_TICKS) {
                    turnsCompleted++;
                    currentDir = currentDir.rotateYClockwise();
                    if (turnsCompleted >= 4) {
                        currentState = State.DESCEND;
                    } else {
                        currentState = State.LOOP_MOVE;
                    }
                }
                mc.options.leftKey.setPressed(false);
                break;

            case DESCEND:
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(true);
                descentYStart = mc.player.getY();

                if (mc.player.getY() <= descentYStart - DESCENT_BLOCKS) {
                    mc.options.sneakKey.setPressed(false);
                    if (mc.player.getY() <= minY + 1) {
                        currentState = State.AFK_RESET;
                    } else {
                        turnsCompleted = 0;
                        currentState = State.LOOP_MOVE;
                    }
                }
                break;

            case AFK_RESET:
                mc.options.attackKey.setPressed(true);
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(false);
                mc.player.getAbilities().flying = false;

                if (mc.player.getY() >= RESET_Y_THRESHOLD) {
                    currentState = State.SURFACE_CHECK;
                }
                break;
        }
    }

    //---------------------------------------------------------
    //                   HELPER METHODS
    //---------------------------------------------------------

    private static boolean findCorner(MinecraftClient mc) {
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = playerPos.getX() - 50; x < playerPos.getX() + 50; x++) {
            for (int z = playerPos.getZ() - 50; z < playerPos.getZ() + 50; z++) {
                BlockPos pos = new BlockPos(x, TARGET_SETUP_Y, z);
                BlockState state = mc.world.getBlockState(pos);

                if (state.isAir()) {
                    int airCount = 0;
                    int bedrockCount = 0;

                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        BlockState adj = mc.world.getBlockState(pos.offset(dir));
                        if (adj.isAir()) airCount++;
                        else if (isBedrock(adj)) bedrockCount++;
                    }

                    if (airCount == 2 && bedrockCount == 2) {
                        targetCornerPos = pos;

                        for (Direction dir : Direction.Type.HORIZONTAL) {
                            if (isBedrock(mc.world.getBlockState(pos.offset(dir)))) {
                                currentDir = dir.getOpposite();
                                break;
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void moveTowards(MinecraftClient mc, Vec3d target, boolean flyXZ, boolean flyY) {
        Vec3d dir = target.subtract(mc.player.getPos());
        double distance = dir.length();

        if (distance < 0.1) {
            mc.player.setVelocity(Vec3d.ZERO);
            return;
        }

        double speed = mc.player.getAbilities().getFlySpeed() * 5.0;

        Vec3d movementVec = new Vec3d(flyXZ ? dir.getX() : 0,
                flyY ? dir.getY() : 0,
                flyXZ ? dir.getZ() : 0).normalize().multiply(speed);

        mc.player.setVelocity(movementVec);

        double yawAngle = Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        mc.player.setYaw((float) yawAngle);
        mc.player.setPitch(0.0f);
    }

    private static int detectMinY(MinecraftClient mc) {
        int y = mc.player.getBlockPos().getY();
        while (y > mc.world.getBottomY()) {
            if (isBedrock(mc.world.getBlockState(new BlockPos((int) mc.player.getX(), y - 1, (int) mc.player.getZ())))) {
                return y;
            }
            y--;
        }
        return mc.world.getBottomY();
    }

    private static boolean handleCaptcha(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen) || (screen instanceof InventoryScreen)) {
            return false;
        }

        Slot targetSlot = null;
        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                String name = stack.getName().getString();

                if (name.contains("Click me") || name.contains("Continue")) {
                    if (!stack.getItem().toString().contains("stained_glass_pane")) {
                        targetSlot = slot;
                        break;
                    }
                }
            }
        }

        if (targetSlot != null) {
            mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    targetSlot.id,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
            );
        }
        return true;
    }

    private static boolean isBedrock(BlockState state) {
        return state.getBlock() == Blocks.BEDROCK;
    }
}