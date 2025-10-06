package com.pikadaklient.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AbyssRunner {
    // --- Configurable constants ---
    private static final BlockPos COORD_A = new BlockPos(451, 9, 518);         // first reference coordinate
    private static final int COORD_A_RANGE = 150;                              // within 150 blocks
    private static final BlockPos COORD_B = new BlockPos(1028, 35, 1026);      // second reference coordinate
    private static final int COORD_B_RANGE = 5;                                // within 5 blocks for restart condition
    private static final int TIMER_THRESHOLD = 200;                             // timer > 200 triggers restart logic
    private static final long START_WAIT_MS = 15_000L;                         // start() waits 15 seconds
    private static final long GOTO_TIMEOUT_MS = 15_000L;                        // #goto timeout 15 seconds
    private static final double REACH_DISTANCE = 1;                             // considered "arrived" to waypoint
    private static final double HOVER_REACH_THRESHOLD = 0.5;                    // how close to look target before advancing
    private static final double HOVER_LOOK_SPEED = 8.0;                         // degrees per tick interpolation speed (tweak)
    private static final int SCAN_RADIUS = 6;                                   // radius to scan for glazed terracotta
    private static final double VISUAL_MAX_DISTANCE = 4.5;                      // maximum raycast visual distance for hitvecs

    // --- Hardcoded waypoints (replace with your own list) ---
    private static final List<BlockPos> WAYPOINTS = Arrays.asList(
            new BlockPos(444, 9, 514),
            new BlockPos(438, 9, 530),
            new BlockPos(432, 12, 526),
            //new BlockPos(429, 14, 518),
            new BlockPos(433, 11, 512),
            new BlockPos(432, 8, 497),
            new BlockPos(427, 8, 477),
            new BlockPos(430, 6, 479),
            new BlockPos(444, 3, 482),
            new BlockPos(455, 3, 475),
            new BlockPos(459, 3, 493),
            new BlockPos(446, 3, 499)
    );

    // --- Internal state ---
    private static volatile boolean running = false;
    private static int timer = 0;

    private enum NavState { IDLE, WAITING_START, RUNNING, NAVIGATING, HOVERING }
    private static NavState state = NavState.IDLE;

    private static int waypointIndex = 0;
    private static long gotoStartTime = 0L;
    private static BlockPos currentGotoTarget = null;

    // Hovering (chain) state - now uses Vec3d hit vectors
    private static List<Vec3d> hoverList = new ArrayList<>();
    private static int hoverIndex = 0;
    private static boolean hoveringActive = false;

    // thread-safe guard so we don't call start() many times concurrently
    private static final AtomicBoolean startingGuard = new AtomicBoolean(false);

    public static boolean leftclicking = false;

    // --- Public API ---

    public static void start(MinecraftClient mc) {
        if (startingGuard.getAndSet(true)) return; // already starting
        try {
            if (mc == null || mc.player == null) {
                startingGuard.set(false);
                return;
            }

            state = NavState.WAITING_START;

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    sendPlayerMsg("/abyss");
                    Thread.sleep(START_WAIT_MS);
                } catch (InterruptedException ignored) {}
                running = true;
                state = NavState.RUNNING;
                startingGuard.set(false);
            }, "AbyssRunner-Starter").start();
        } finally {
            // nothing
        }
    }

    public static void stop() {
        running = false;
        state = NavState.IDLE;
        timer = 0;
        waypointIndex = 0;
        hoverList.clear();
        hoverIndex = 0;
        hoveringActive = false;
    }

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            if (leftclicking) {
                leftclicking = false;
                AHKUtils.stopAHK();
            }
            return;
        }

        if (!running && state == NavState.WAITING_START) {
            if (leftclicking) {
                leftclicking = false;
                AHKUtils.stopAHK();
            }
            return;
        }

        if (running && isWithinRange(mc.player.getPos(), COORD_A, COORD_A_RANGE)) {
            timer++;
        }

        if (running && !isWithinRange(mc.player.getPos(), COORD_A, COORD_A_RANGE)
                && isWithinRange(mc.player.getPos(), COORD_B, COORD_B_RANGE)
                && timer > TIMER_THRESHOLD) {
            if (leftclicking) {
                leftclicking = false;
                AHKUtils.stopAHK();
            }
            timer = 0;
            running = false;
            state = NavState.WAITING_START;
            start(mc);
            return;
        }

        if (!running) {
            if (leftclicking) {
                leftclicking = false;
                AHKUtils.stopAHK();
            }
            return;
        }

        if (isWithinRange(mc.player.getPos(), COORD_A, COORD_A_RANGE)) {
            if (state == NavState.HOVERING && hoveringActive) {
                if (!leftclicking) { leftclicking = true; AHKUtils.startAHK(); }
                processHovering(mc);
                return;
            }

            if (state == NavState.NAVIGATING && currentGotoTarget != null) {
                if(leftclicking){
                    leftclicking = false;
                    AHKUtils.stopAHK();
                }
                double dist = mc.player.getPos().distanceTo(new Vec3d(currentGotoTarget.getX()+0.5, currentGotoTarget.getY()+0.5, currentGotoTarget.getZ()+0.5));
                long now = System.currentTimeMillis();

                if (dist <= REACH_DISTANCE) {
                    state = NavState.RUNNING;
                    currentGotoTarget = null;
                    gotoStartTime = 0;
                    List<Vec3d> hitVecs = scanVisibleYGTHitVecs(mc, SCAN_RADIUS);
                    if (!hitVecs.isEmpty()) {
                        buildAndStartHoverChainFromHitVecs(mc, hitVecs);
                        System.out.println("hitVecs: " + hitVecs.size());
                    } else {
                        advanceToNextWaypoint();
                    }
                    return;
                }

                if (now - gotoStartTime > GOTO_TIMEOUT_MS) {
                    currentGotoTarget = null;
                    gotoStartTime = 0;
                    state = NavState.RUNNING;

                    if (!isWithinRange(mc.player.getPos(), COORD_A, COORD_A_RANGE)
                            && isWithinRange(mc.player.getPos(), COORD_B, COORD_B_RANGE)
                            && timer > TIMER_THRESHOLD) {
                        timer = 0;
                        running = false;
                        state = NavState.WAITING_START;
                        start(mc);
                        return;
                    } else {
                        advanceToNextWaypoint();
                        return;
                    }
                }

                return;
            }

            if (state == NavState.RUNNING || state == NavState.IDLE) {
                startGotoToWaypoint(mc);
            }
        } else {
            if (isWithinRange(mc.player.getPos(), COORD_B, COORD_B_RANGE) && timer > TIMER_THRESHOLD) {
                timer = 0;
                running = false;
                state = NavState.WAITING_START;
                start(mc);
            }
        }
    }

    private static void startGotoToWaypoint(MinecraftClient mc) {
        if (WAYPOINTS.isEmpty()) {
            state = NavState.RUNNING;
            return;
        }
        if (waypointIndex >= WAYPOINTS.size()) waypointIndex = 0;
        BlockPos target = WAYPOINTS.get(waypointIndex);
        sendPlayerMsg("#goto " + target.getX() + " " + target.getY() + " " + target.getZ());
        currentGotoTarget = target;
        gotoStartTime = System.currentTimeMillis();
        state = NavState.NAVIGATING;
    }

    private static void advanceToNextWaypoint() {
        waypointIndex++;
        if (waypointIndex >= WAYPOINTS.size()) waypointIndex = 0;
        state = NavState.RUNNING;
    }

    /**
     * Improved raytrace-based scan: keep one best hit per (blockPos, face).
     */
    private static List<Vec3d> scanVisibleYGTHitVecs(MinecraftClient mc, int radius) {
        Map<String, Vec3d> bestPerFace = new HashMap<>(); // key: "x,y,z:faceIndex"
        if (mc.player == null || mc.world == null) return new ArrayList<>();

        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d eyePos = mc.player.getCameraPosVec(1.0f);

        double[][] samples = new double[][] {
                {0.2, 0.2}, {0.8, 0.2}, {0.2, 0.8}, {0.8, 0.8}, {0.5, 0.5}, {0.5, 0.2}
        };
        final double INSIDE_EPS = 0.001;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius / 2; dy <= radius / 2; dy++) {
                    BlockPos p = playerPos.add(dx, dy, dz);
                    if (!mc.world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;

                    BlockState state = mc.world.getBlockState(p);
                    if (state.getBlock() != Blocks.YELLOW_GLAZED_TERRACOTTA) continue;

                    for (Direction dir : Direction.values()) {
                        BlockPos neighbor = p.offset(dir);
                        BlockState neighborState = mc.world.getBlockState(neighbor);
                        if (!(neighborState.isAir())) continue;

                        for (double[] uv : samples) {
                            double u = uv[0], v = uv[1];
                            double tx, ty, tz;
                            double bx = p.getX(), by = p.getY(), bz = p.getZ();

                            switch (dir) {
                                case EAST -> { tx = bx + 1.0 - INSIDE_EPS; ty = by + v;          tz = bz + u; }
                                case WEST -> { tx = bx + INSIDE_EPS;      ty = by + v;          tz = bz + u; }
                                case UP ->   { tx = bx + u;               ty = by + 1.0 - INSIDE_EPS; tz = bz + v; }
                                case DOWN -> { tx = bx + u;               ty = by + INSIDE_EPS; tz = bz + v; }
                                case SOUTH ->{ tx = bx + u;               ty = by + v;          tz = bz + 1.0 - INSIDE_EPS; }
                                case NORTH ->{ tx = bx + u;               ty = by + v;          tz = bz + INSIDE_EPS; }
                                default ->   { tx = bx + 0.5; ty = by + 0.5; tz = bz + 0.5; }
                            }

                            Vec3d target = new Vec3d(tx, ty, tz);

                            BlockHitResult result = mc.world.raycast(new RaycastContext(
                                    eyePos,
                                    target,
                                    RaycastContext.ShapeType.COLLIDER,
                                    RaycastContext.FluidHandling.NONE,
                                    mc.player
                            ));

                            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(p)) {
                                Vec3d hitVec = result.getPos();
                                double dist = eyePos.distanceTo(hitVec);
                                if (dist > VISUAL_MAX_DISTANCE) continue;

                                // Key per block pos + face ordinal
                                String key = p.getX() + "," + p.getY() + "," + p.getZ() + ":" + dir.ordinal();

                                // Keep the hit that's closest to eye (more visible)
                                Vec3d prev = bestPerFace.get(key);
                                if (prev == null || eyePos.distanceTo(hitVec) < eyePos.distanceTo(prev)) {
                                    bestPerFace.put(key, hitVec);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Collect and sort by distance
        List<Vec3d> deduped = new ArrayList<>(bestPerFace.values());
        Vec3d eyeRef = mc.player.getCameraPosVec(1.0f);
        deduped.sort(Comparator.comparingDouble(vec -> eyeRef.distanceTo(vec)));

        System.out.println("RaycastHitVec scan (per-face) found " + deduped.size() + " visible hit points within " + radius + ".");
        return deduped;
    }

    private static void buildAndStartHoverChainFromHitVecs(MinecraftClient mc, List<Vec3d> hitVecs) {
        class HitPoint {
            final Vec3d v;
            final BlockPos pos;
            final Direction face;
            HitPoint(Vec3d v, BlockPos pos, Direction face) { this.v = v; this.pos = pos; this.face = face; }
        }

        List<HitPoint> hpList = new ArrayList<>();
        for (Vec3d v : hitVecs) {
            BlockPos p = new BlockPos((int)Math.floor(v.x), (int)Math.floor(v.y), (int)Math.floor(v.z));
            double dx = (v.x - p.getX()), dy = (v.y - p.getY()), dz = (v.z - p.getZ());

            double diffNorth = Math.abs(dz - 0.0);
            double diffSouth = Math.abs(dz - 1.0);
            double diffWest  = Math.abs(dx - 0.0);
            double diffEast  = Math.abs(dx - 1.0);
            double diffDown  = Math.abs(dy - 0.0);
            double diffUp    = Math.abs(dy - 1.0);

            Direction best = Direction.NORTH;
            double bestDiff = diffNorth;
            if (diffSouth < bestDiff) { best = Direction.SOUTH; bestDiff = diffSouth; }
            if (diffWest  < bestDiff) { best = Direction.WEST;  bestDiff = diffWest;  }
            if (diffEast  < bestDiff) { best = Direction.EAST;  bestDiff = diffEast;  }
            if (diffDown  < bestDiff) { best = Direction.DOWN;  bestDiff = diffDown;  }
            if (diffUp    < bestDiff) { best = Direction.UP;    bestDiff = diffUp;    }

            hpList.add(new HitPoint(v, p, best));
        }

        List<HitPoint> remaining = new ArrayList<>(hpList);
        List<Vec3d> chain = new ArrayList<>();
        if (remaining.isEmpty()) {
            hoverList = chain;
            hoveringActive = false;
            state = NavState.RUNNING;
            return;
        }

        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        remaining.sort(Comparator.comparingDouble(h -> eye.distanceTo(h.v)));
        HitPoint current = remaining.remove(0);
        chain.add(current.v);

        while (!remaining.isEmpty()) {
            HitPoint bestHp = null;
            double bestScore = Double.POSITIVE_INFINITY;

            for (HitPoint cand : remaining) {
                double base;
                if (cand.pos.equals(current.pos) && cand.face == current.face) base = 0.0;
                else if (areAdjacentBlocks(cand.pos, current.pos) && cand.face == current.face) base = 10.0;
                else if (cand.pos.equals(current.pos)) base = 20.0;
                else base = 100.0;

                double dist = current.v.distanceTo(cand.v);
                double score = base + dist;
                if (score < bestScore) {
                    bestScore = score;
                    bestHp = cand;
                }
            }

            if (bestHp == null) break;
            chain.add(bestHp.v);
            current = bestHp;
            remaining.remove(bestHp);
        }

        hoverList = chain;
        hoverIndex = 0;
        hoveringActive = true;
        state = NavState.HOVERING;
    }

    private static boolean areAdjacentBlocks(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return (dx + dy + dz) == 1;
    }

    private static void processHovering(MinecraftClient mc) {
        if (!hoveringActive || hoverList == null || hoverList.isEmpty()) {
            hoveringActive = false;
            state = NavState.RUNNING;
            return;
        }
        if (hoverIndex >= hoverList.size()) {
            hoveringActive = false;
            hoverList.clear();
            hoverIndex = 0;
            advanceToNextWaypoint();
            return;
        }

        Vec3d targetVec = hoverList.get(hoverIndex);
        System.out.println("Hovering to hitVec " + targetVec);

        double eyeX = mc.player.getPos().x;
        double eyeY = mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose());
        double eyeZ = mc.player.getPos().z;

        float[] targetYawPitch = calcYawPitch(eyeX, eyeY, eyeZ,
                (float) targetVec.x, (float) targetVec.y, (float) targetVec.z);

        float cyaw = mc.player.getYaw();
        float cpitch = mc.player.getPitch();
        float nyaw = approachAngle(cyaw, targetYawPitch[0], (float) HOVER_LOOK_SPEED);
        float npitch = approachAngle(cpitch, targetYawPitch[1], (float) HOVER_LOOK_SPEED);

        mc.player.setYaw(nyaw);
        mc.player.setPitch(npitch);

        float yawDiff = Math.abs(wrapDegrees(nyaw - targetYawPitch[0]));
        float pitchDiff = Math.abs(npitch - targetYawPitch[1]);
        if (yawDiff < 3.0f && pitchDiff < 3.0f) {
            hoverIndex++;
        }
    }

    private static boolean isWithinRange(Vec3d pos, BlockPos target, int range) {
        double dx = pos.x - (target.getX() + 0.5);
        double dy = pos.y - (target.getY() + 0.5);
        double dz = pos.z - (target.getZ() + 0.5);
        return dx*dx + dy*dy + dz*dz <= (double)range * range;
    }

    private static float[] calcYawPitch(double sx, double sy, double sz, double tx, double ty, double tz) {
        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double dist = Math.sqrt(dx*dx + dz*dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        return new float[]{wrapDegrees(yaw), pitch};
    }

    private static float approachAngle(float current, float target, float maxDelta) {
        float diff = wrapDegrees(target - current);
        if (diff > maxDelta) diff = maxDelta;
        if (diff < -maxDelta) diff = -maxDelta;
        return wrapDegrees(current + diff);
    }

    public static void sendPlayerMsg(String message) {
        if (message.startsWith("/"))
            MinecraftClient.getInstance().player.networkHandler.sendChatCommand(message.substring(1));
        else
            MinecraftClient.getInstance().player.networkHandler.sendChatMessage(message);
    }

    private static float wrapDegrees(float deg) {
        deg %= 360.0f;
        if (deg >= 180.0f) deg -= 360.0f;
        if (deg < -180.0f) deg += 360.0f;
        return deg;
    }
}
