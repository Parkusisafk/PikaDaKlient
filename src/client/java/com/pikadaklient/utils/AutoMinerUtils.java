package com.pikadaklient.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.text.Text;

public class AutoMinerUtils {

    private static boolean running = false;
    private static BlockPos targetCornerPos;
    private static int minY = -999;

    // Constants
    private static final int RESET_Y_THRESHOLD = 90;
    private static final int TARGET_SETUP_Y = 88;
    private static final float AIM_PITCH = 30.0f;
    private static final int DESCENT_BLOCKS = 3;
    private static final int TURN_TICKS = 6;

    // State Variables
    private enum State {
        SURFACE_CHECK,
        SETUP_SCANNING,
        SETUP_FLYING,
        SETUP_DESCEND,
        SETUP_TURN,
        LOOP_MOVE,
        LOOP_TURN,
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
        System.out.println("[AutoMiner] Started.");
    }

    public static void stop() {
        running = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.attackKey != null) mc.options.attackKey.setPressed(false);
        if (mc.options.leftKey != null) mc.options.leftKey.setPressed(false);
        if (mc.options.sneakKey != null) mc.options.sneakKey.setPressed(false);
        if (mc.player != null) mc.player.getAbilities().flying = false;
        System.out.println("[AutoMiner] Stopped.");
    }

    public static void tick(MinecraftClient mc) {
        if (!running || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // --- CAPTCHA HANDLER ---
        if (handleCaptcha(mc)) {
            mc.options.attackKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            return;
        }

        // --- ATTACK ---
        if(currentState != State.SURFACE_CHECK &&
        currentState != State.SETUP_DESCEND &&
        currentState != State.SETUP_FLYING &&
        currentState != State.SETUP_SCANNING &&
        currentState != State.AFK_RESET) {
            //mc.options.attackKey.setPressed(true);
            //performAttack(mc);
        }
        // --- STATE MACHINE ---
        switch (currentState) {

            case SURFACE_CHECK:
                if (mc.player.getY() >= RESET_Y_THRESHOLD) {
                    mc.player.getAbilities().flying = true;
                    mc.player.getAbilities().setFlySpeed(0.05f);
                    currentState = State.SETUP_SCANNING;
                    minY = detectMinY(mc);
                    System.out.println("[AutoMiner] Surface detected. MinY=" + minY);
                } else if (mc.player.getY() <= 19) { //hardcoded
                    currentState = State.AFK_RESET;
                    System.out.println("[AutoMiner] AFK reset triggered.");
                } else {
                    currentState = State.LOOP_MOVE;
                }
                break;

            case SETUP_SCANNING:
                if (findCorner(mc)) {
                    System.out.println("[AutoMiner] Corner found at " + targetCornerPos);
                    currentState = State.SETUP_FLYING;
                }
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(false);
                break;

            case SETUP_FLYING:
                if (!mc.player.getAbilities().flying) {
                    // Simulate a jump (spacebar press)
                    mc.options.jumpKey.setPressed(true);

                    // Give Minecraft a tick to register the jump
                    // (release immediately so it doesn't spam jump)
                    mc.options.jumpKey.setPressed(false);

                    // Enable flying explicitly
                    mc.player.getAbilities().flying = true;
                    mc.player.getAbilities().setFlySpeed(0.05f);

                    System.out.println("[AutoMiner] Activated flying mode.");
                }
                BlockPos pos = targetCornerPos;
                Vec3d targetCenter = new Vec3d(
                        pos.getX() + 0.5, // center X
                        mc.player.getY(), // current Y
                        pos.getZ() + 0.5  // center Z
                );                moveTowards(mc, targetCenter, true, false);
                System.out.println("[AutoMiner] Flying to corner: currentPos=" + mc.player.getPos());

                if (mc.player.getPos().distanceTo(targetCenter) < 0.3) {
                    mc.player.setVelocity(Vec3d.ZERO);
                    currentState = State.SETUP_DESCEND;
                    System.out.println("[AutoMiner] Reached corner, descending...");
                }
                break;

            case SETUP_DESCEND:
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(true);
                mc.player.setPitch(AIM_PITCH);

                if (mc.player.getY() <= TARGET_SETUP_Y - 0.5) {
                    mc.options.sneakKey.setPressed(false);
                    mc.player.setVelocity(Vec3d.ZERO);
                    turnsCompleted = 0;
                    turnTickCounter = 0;
                    currentState = State.SETUP_TURN;
                    System.out.println("[AutoMiner] Setup descend complete. Start mining loop.");
                }
                break;

            case SETUP_TURN:
                // Ensure no stray keys are pressed
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(false);

                BlockPos playerPos = mc.player.getBlockPos();
                Direction chosenDir = null;

                // Find the open direction: the one after two consecutive bedrock blocks clockwise
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    Direction clockwise1 = dir;
                    Direction clockwise2 = dir.rotateYClockwise();

                    boolean b1 = isBedrock(mc.world.getBlockState(playerPos.offset(clockwise1)));
                    boolean b2 = isBedrock(mc.world.getBlockState(playerPos.offset(clockwise2)));

                    if (b1 && b2) {
                        // The next clockwise direction is the tunnel direction
                        chosenDir = clockwise2.rotateYClockwise();
                        break;
                    }
                }

                if (chosenDir != null) {
                    currentDir = chosenDir;

                    // Look into the mine (perpendicular to wall, inward)
                    Direction intoMine = currentDir.rotateYCounterclockwise();
                    float baseYaw = RotationUtils.dirToYaw(intoMine);

                    // Apply small left bias
                    float bias = 15.0f; // adjust as needed
                    float targetYaw = (baseYaw + 85) % 360.0f; //HARDCODED GOES HARD AF


                    // Smoothly turn toward target yaw
                    RotationUtils.smoothTurnYaw(mc, targetYaw, 10.0f);

                    // Look downward at ~30°
                    mc.player.setPitch(30.0f);

                    // When close enough to target yaw, move to LOOP_MOVE
                    if (Math.abs(RotationUtils.wrapDegrees(targetYaw - mc.player.getYaw())) < 2.0f) {
                        System.out.println("[AutoMiner] Setup turn complete. Facing " + currentDir);
                        currentState = State.LOOP_MOVE;
                    }
                } else {
                    System.out.println("[AutoMiner] Could not determine initial tunnel direction!");
                }
                break;

            case LOOP_MOVE:
                mc.options.leftKey.setPressed(true);
                mc.player.setPitch(AIM_PITCH);
                //mc.player.setYaw();
                // Check left wall
                BlockPos playerPos1 = mc.player.getBlockPos();
                Direction moveDir = currentDir; // direction we are flying along
                Direction lookDir2 = moveDir.rotateYClockwise(); // 90° clockwise from movement = facing inward


// Block behind (wall you’re hugging)
                BlockPos backPos = playerPos1.offset(lookDir2);

// Block to the left (corner detection)
                BlockPos leftPos = playerPos1.offset(moveDir.rotateYCounterclockwise());

// Debugging
                BlockState backState = mc.world.getBlockState(backPos);
                BlockState leftState = mc.world.getBlockState(leftPos);

                System.out.println("[AutoMiner] Back block: " + backPos + " Type: " + backState.getBlock().getTranslationKey());
                System.out.println("[AutoMiner] Left block: " + leftPos + " Type: " + leftState.getBlock().getTranslationKey());

// Turning condition: bedrock to the left
                if (isBedrock(leftState)) {
                    System.out.println("[AutoMiner] Bedrock detected on left at " + leftPos + ", turning...");
                    mc.options.leftKey.setPressed(false);
                    turnTargetYaw = mc.player.getYaw() + 90.0f;
                    turnTargetYaw %= 360;
                    turnTickCounter = 0;
                    currentState = State.LOOP_TURN;
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
                    System.out.println("[AutoMiner] Turn completed. Turns done: " + turnsCompleted);
                    if (turnsCompleted >= 4) {
                        currentState = State.DESCEND;
                        descentYStart = mc.player.getY();
                        System.out.println("[AutoMiner] Completed loop, starting descent.");
                    } else {
                        currentState = State.LOOP_MOVE;
                    }
                }
                mc.options.leftKey.setPressed(false);
                break;

            case DESCEND:
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(true);

                if (descentYStart == 0) descentYStart = mc.player.getY();

                if (mc.player.getY() <= descentYStart - 3) {
                    mc.options.sneakKey.setPressed(false);
                    System.out.println("[AutoMiner] Descend complete. CurrentY=" + mc.player.getY());
                    if (mc.player.getY() <= 19) {
                        currentState = State.AFK_RESET;
                        System.out.println("[AutoMiner] Reached bottom. AFK reset.");
                    } else {
                        turnsCompleted = 0;
                        currentState = State.LOOP_MOVE;
                        descentYStart = 0;
                        System.out.println("[AutoMiner] Starting next mining loop.");
                    }
                }
                break;

            case AFK_RESET:
                //mc.options.attackKey.setPressed(true);
                mc.options.leftKey.setPressed(false);
                mc.options.sneakKey.setPressed(false);
                mc.player.getAbilities().flying = false;
                if (mc.player.getY() >= RESET_Y_THRESHOLD) {
                    currentState = State.SURFACE_CHECK;
                }
                break;
        }
    }

    // ---------------- Helper Methods ----------------


    private static void performAttack(MinecraftClient mc) {
//        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
//
//        HitResult hit = mc.crosshairTarget;
//        // Keep the attack key pressed. This is required for the client to think it's still mining.
//        mc.options.attackKey.setPressed(true);
//
//        if (hit instanceof BlockHitResult blockHit) {
//            BlockPos pos = blockHit.getBlockPos();
//            Direction side = blockHit.getSide();
//
//            // --- 1. ESTABLISH MINING STATE ---
//            // Call the standard method once per block to send the initial START_DESTROY_BLOCK
//            // and tell the client's interaction manager which block is being broken.
//            // It does not hurt to call this every tick if the block hasn't changed.
//            mc.interactionManager.attackBlock(pos, side);
//
//            // --- 2. INSTABREAK SPAM (The core of InstantRebreak) ---
//            // Aggressively send the STOP_DESTROY_BLOCK packet every tick.
//            // This forces the server to check for block break completion, which is instant
//            // if the player has high Haste/Efficiency.
//            mc.interactionManager.sendSequencedPacket(mc.world, s ->
//                    new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, side, s)
//            );
//
//            // --- 3. VISUAL/SWING ---
//            // You only need to call mc.player.swingHand(Hand.MAIN_HAND) once here.
//            // No need for a separate HandSwingC2SPacket unless you wanted to prevent the visual swing.
//            mc.player.swingHand(Hand.MAIN_HAND);
//
//        } else if (mc.targetedEntity != null) {
//            // Attack entities if they are the target.
//            mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
//            mc.player.swingHand(Hand.MAIN_HAND);
//        }
    }


    private static Direction findInitialDir(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Check all 4 cardinal directions around player
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockState state = mc.world.getBlockState(pos.offset(dir));
            System.out.println("[AutoMiner] Check " + dir + ": " + state.getBlock().getTranslationKey());
        }

        // Loop through clockwise order
        for (Direction dir : Direction.Type.HORIZONTAL) {
            Direction cw1 = dir.rotateYClockwise();
            Direction cw2 = cw1.rotateYClockwise();

            boolean firstBedrock = isBedrock(mc.world.getBlockState(pos.offset(dir)));
            boolean secondBedrock = isBedrock(mc.world.getBlockState(pos.offset(cw1)));
            boolean thirdOpen = !isBedrock(mc.world.getBlockState(pos.offset(cw2)));

            if (firstBedrock && secondBedrock && thirdOpen) {
                System.out.println("[AutoMiner] Initial move dir found: " + cw2);
                return cw2; // this is the correct currentDir
            }
        }

        System.out.println("[AutoMiner] Failed to find initial move dir, defaulting to NORTH");
        return Direction.NORTH; // fallback
    }

    private static boolean findCorner(MinecraftClient mc) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int x = playerPos.getX() - 50; x < playerPos.getX() + 50; x++) {
            for (int z = playerPos.getZ() - 50; z < playerPos.getZ() + 50; z++) {
                BlockPos pos = new BlockPos(x, TARGET_SETUP_Y, z);
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir()) {
                    int airCount = 0, bedrockCount = 0;
                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        BlockState adj = mc.world.getBlockState(pos.offset(dir));
                        if (adj.isAir()) airCount++;
                        else if (isBedrock(adj)) bedrockCount++;
                    }
                    if (airCount == 2 && bedrockCount == 2) {
                        targetCornerPos = pos;
                        System.out.println("[AutoMiner] Corner candidate: " + pos);
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
        if (!(mc.currentScreen instanceof HandledScreen<?> screen) || (screen instanceof InventoryScreen)) return false;
        Slot targetSlot = null;
        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                String name = stack.getName().getString();
                if ((name.contains("Click me") || name.contains("Continue")) && !stack.getItem().toString().contains("stained_glass_pane")) {
                    targetSlot = slot;
                    break;
                }
            }
        }
        if (targetSlot != null) {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId,
                    targetSlot.id, 0, SlotActionType.PICKUP, mc.player);
        }
        return targetSlot != null;
    }

    private static boolean isBedrock(BlockState state) {
        return state.getBlock() == Blocks.BEDROCK;
    }
}
