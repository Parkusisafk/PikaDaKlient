package com.pikadaklient.window;

import com.pikadaklient.SidebarParser;
import com.pikadaklient.utils.AbyssRunner;
import com.pikadaklient.utils.AutoMinerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutoManager - 1.21.8 friendly version.
 * - public fields: prestige, ascensions, coins, currentAction
 * - updateX(...) methods that call ProgressTracker setters
 * - ascension flow: send /ascension, poll for chest GUI, check last slot:
 *     - REDSTONE -> parse tooltip for required prestige
 *     - EMERALD  -> click the slot
 */
public class AutoManager {
    // ----- Singleton instance -----
    private static AutoManager INSTANCE;

    // ----- Public state -----
    private volatile int lastHandledSyncId = -1;

    public volatile int prestige = 0;
    public volatile int ascensions = 0;
    public volatile double coins = 0.0;
    public volatile String currentAction = "None";

    private final String initString;

    public AutoManager(String s) {
        this.initString = s;
        INSTANCE = this; // assign singleton on creation
    }

    /** Get the singleton instance */
    public static AutoManager getInstance() {
        return INSTANCE;
    }

    // ----- Internal / static state -----
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile int requiredPrestige = -1; // parsed from tooltip (-1 = not found)

    // pattern to capture numbers like "1,234" from strings containing "Prestige"
    private static final Pattern PRESTIGE_PATTERN = Pattern.compile("Prestige\\D*([\\d,]+)", Pattern.CASE_INSENSITIVE);



    /** Initialize and run the sequence (safe to call once). */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) return;
        ProgressTracker tracker = ProgressTracker.getInstance();
        tracker.initWindow();

        // if called with "sp" set ProgressTracker state and local state if desired
        if ("sp".equalsIgnoreCase(initString)) {
            this.currentAction = "prestigegrind";
            tracker.setCurrentState("prestigegrind");
        } else if("sa".equalsIgnoreCase(initString) || "as".equalsIgnoreCase(initString)){
            this.currentAction = "abyssgrind";
            tracker.setCurrentState("abyssgrind");
        }

        try {
            // if player is within the target coords, run transfer sequence then ascension flow
            if (AutoMinerUtils.isPlayerWithin(-1719, 80, -22)) {
                AutoMinerUtils.stop();

                CompletableFuture<Boolean> fut = AutoMinerUtils.runTransferSequenceAsync();
                fut.thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        runAscensionSequence();

                        TransferCompleted(initString);


                    } else {
                        System.out.println("[AutoManager] transfer sequence returned false.");
                    }
                });
            } else {
                runAscensionSequence();

                TransferCompleted(initString);


            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public String getInitString() {
        return initString;
    }

    /** Returns parsed required prestige or -1 if not found yet. */
    public static int getRequiredPrestige() {
        return requiredPrestige;
    }

    /** Setter so TooltipUtils or the parser can set it if needed. */
    public static void setRequiredPrestige(int v) {
        requiredPrestige = v;
        ProgressTracker.getInstance().setRequiredPrestige(v);
    }
    private Thread sidebarThread;

    public void startSidebarThread() {
        // Stop previous thread if running
        if (sidebarThread != null && sidebarThread.isAlive()) {
            sidebarThread.interrupt();
        }

        sidebarThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) { // check interruption
                try {
                    Thread.sleep(5000);

                    SidebarParser.SidebarData data = SidebarParser.parse();
                    if (data != null) {
                        if (data.prestige > 0) updatePrestige(data.prestige);
                        if (data.ascension > 0) updateAscension(data.ascension);
                        if (data.coins > 0) updateCoins(data.coins);
                        if (data.rebirth > 0) System.out.println("Rebirth: " + data.rebirth);
                    }

                } catch (InterruptedException e) {
                    System.out.println("[AutoManager] Sidebar thread interrupted.");
                    return; // exit thread
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "Sidebar-Update-Thread");

        sidebarThread.start();
    }
    public void TransferCompleted(String com){

        AutoMinerUtils.stop();
        AbyssRunner.stop();
        startSidebarThread();

        //main init code goes here


        if ("sp".equalsIgnoreCase(com)) {
            this.currentAction = "prestigegrind";
            ProgressTracker.getInstance().setCurrentState("prestigegrind");
            AutoMinerUtils.start();
        } else if("sa".equalsIgnoreCase(initString) || "as".equalsIgnoreCase(initString)){
            this.currentAction = "abyssgrind";
            ProgressTracker.getInstance().setCurrentState("abyssgrind");
            AbyssRunner.start(MinecraftClient.getInstance());
        }
    }
    // ---------------------------
    // Public update methods (also update ProgressTracker)
    // ---------------------------
    public void updatePrestige(int value) {
        this.prestige = value;
        ProgressTracker.getInstance().setPrestige(value);
        if(value >= requiredPrestige){
            runAscensionSequence();

            new Thread(() -> {
                try{
                    Thread.sleep(7500);
                    runAscensionSequence();
                } catch(InterruptedException e){
                    System.out.println(e);
                }
            }).start();
        }
    }

    public void updateAscension(int value) {
        this.ascensions = value;
        ProgressTracker.getInstance().setAscension(value);
    }

    public void updateCoins(int value) {
        this.coins = value;
        ProgressTracker.getInstance().setCoins(value);
    }

    public void updateCurrentAction(String action) {
        if (action == null) action = "None";
        this.currentAction = action;
        ProgressTracker.getInstance().setCurrentAction(action);
    }

    // ---------------------------
    // Ascension sequence: send /ascension, wait for chest GUI, inspect last slot
    // ---------------------------
    private void runAscensionSequence() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            System.out.println("[AutoManager] MinecraftClient or player null.");
            return;
        }

        // 1) send the /ascension command (multiple fallbacks)
        sendChatCommand("/ascension");

        // 2) poll for chest GUI in background thread
        new Thread(() -> {
            final int maxWaitMs = 12_000;
            final int pollIntervalMs = 200;
            int waited = 0;

            while (waited < maxWaitMs) {
                try { Thread.sleep(pollIntervalMs); } catch (InterruptedException ignored) {}
                waited += pollIntervalMs;

                if (!(mc.currentScreen instanceof HandledScreen<?>)) {
                    // not a container GUI — skip
                    continue;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}


                HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                ScreenHandler handler = screen.getScreenHandler();
                if (handler == null) continue;

                int slotCount = handler.slots.size();
                if (slotCount <= 0) continue;

// player inventory size (usually 36). Subtract to get the top/container slot count.
                int playerInvSize = 0;
                try {
                    playerInvSize = MinecraftClient.getInstance().player.getInventory().size();
                } catch (Throwable t) {
                    // fallback to 36 if anything odd happens
                    playerInvSize = 36;
                }

                int topSlotCount = slotCount - playerInvSize;
                if (topSlotCount <= 0) {
                    System.out.println("[AutoManager] no top/container slots detected (slotCount=" + slotCount + ", playerInvSize=" + playerInvSize + ")");
                    continue;
                }

// last index *within the chest/container portion*
                int lastChestIndex = topSlotCount - 1;

// Find the last useful slot in the chest area (skip empty and filler panes)
                int slotIndex = 51;
                if (slotIndex >= handler.slots.size()) {
                    System.out.println("[AutoManager] Slot 51 does not exist in this container.");
                    return;
                }

                Slot targetSlot = handler.getSlot(slotIndex);





                if (targetSlot == null) {
                    System.out.println("[AutoManager] no non-empty non-filler slot found in chest area.");
                    return;
                }

                ItemStack stack = targetSlot.getStack();
                if (stack == null || stack.isEmpty()) {
                    System.out.println("[AutoManager] target slot empty after all.");
                    continue;
                }


                System.out.println(String.format("[AutoManager] Chest slot %d => %s | Name: '%s'",
                        slotIndex, stack.getItem(), stack.getName().getString()));

// If REDSTONE -> parse tooltip for required prestige and close the chest after parsing
                if (stack.isOf(Items.REDSTONE)) {
                    System.out.println("[AutoManager] Found REDSTONE at chest slot " + slotIndex + " -> parsing tooltip...");
                    // parsePrestigeFromTooltipAsync already runs on client thread; it now closes the screen afterwards
                    parsePrestigeFromTooltipAsync(stack, mc);
                    return;
                }

// If EMERALD -> click the slot (on client thread) then close the chest
                if (stack.isOf(Items.EMERALD)) {
                    System.out.println("[AutoManager] Found EMERALD at chest slot " + slotIndex + " -> clicking...");
                    // schedule on client thread so handler/click are executed safely
                    mc.execute(() -> {
                        try {
                            clickSlot(handler, slotIndex, mc);
                            updatePrestige(0);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
                            // close the screen and allow future handling
                            mc.setScreen(null);
                            lastHandledSyncId = -1;
                        }
                    });
                    return;
                }



// Otherwise log what we found
                System.out.println("[AutoManager] Chest last useful slot contains: " + stack.getItem() + " | Name: '" + stack.getName().getString() + "'");
                return;

            }

            System.out.println("[AutoManager] timed out waiting for chest GUI.");
        }, "AutoManager-AscensionPoll").start();
    }

    // ---------------------------
    // Click helper
    // ---------------------------
    private void clickSlot(ScreenHandler handler, int slotIndex, MinecraftClient mc) {
        try {
            ClientPlayerInteractionManager im = mc.interactionManager;
            if (im != null) {
                int syncId = handler.syncId;
                im.clickSlot(syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // fallback via handler.onSlotClick
        try {
            handler.onSlotClick(slotIndex, 0, SlotActionType.PICKUP, (PlayerEntity) mc.player);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[AutoManager] failed to click slot - adapt to mappings if necessary.");
        }
    }

    // ---------------------------
    // Tooltip parsing (sync & async variants)
    // ---------------------------

    /**
     * Async-safe: schedules parsing on client thread (mc.execute) and sets requiredPrestige when found.
     * Call this from background threads.
     */
    private void parsePrestigeFromTooltipAsync(ItemStack stack, MinecraftClient mc) {
        if (stack == null || stack.isEmpty() || mc == null) return;

        // run on client thread
        mc.execute(() -> {
            try {
                int parsed = parsePrestigeFromTooltipSync(stack, mc);
                if (parsed > 0) {
                    setRequiredPrestige(parsed);
                    System.out.println("[AutoManager] parsed requiredPrestige = " + parsed);
                    if (mc.player != null) mc.player.sendMessage(Text.literal("§cRequired Prestige: " + parsed), false);
                } else {
                    System.out.println("[AutoManager] no prestige found in tooltip.");
                    // optional: dump tooltip for debugging
                    try {
                        TooltipContext ctx = TooltipContext.create(mc.world);
                        List<Text> tt = stack.getTooltip(ctx, mc.player, TooltipType.BASIC);
                        for (Text line : tt) System.out.println("[AutoManager] TOOLTIP: " + line.getString());
                        //if (stack.) System.out.println("[AutoManager] NBT: " + stack.getNbt());
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                // Close the chest GUI now that parsing finished, and allow re-processing later
                try {
                    mc.setScreen(null);
                } catch (Throwable ignored) {}
                lastHandledSyncId = -1;
            }
        });
    }


    /**
     * Synchronous tooltip parser. MUST be called on client thread.
     * Returns parsed prestige number, or -1 if not found.
     */
    private int parsePrestigeFromTooltipSync(ItemStack stack, MinecraftClient mc) {
        if (stack == null || stack.isEmpty() || mc == null || mc.player == null || mc.world == null) return -1;

        // Use a robust pattern to capture the number, allowing any characters (non-greedily)
        // between "Prestige" and the digits/commas. This handles:
        // "Prestige 2,070" and "You must be Prestige 2,070 to ascend!"
        // NOTE: If you only want the *required* prestige, a more targeted pattern is below.
        final Pattern PRESTIGE_PATTERN = Pattern.compile("Prestige.*?([\\d,]+)", Pattern.CASE_INSENSITIVE);

        // Pattern specific to the 'You must be Prestige X to ascend!' line for clarity
        final Pattern REQUIRED_PRESTIGE_PATTERN = Pattern.compile("You must be Prestige\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);

        try {
            TooltipContext context = TooltipContext.create(mc.world);
            // Using TooltipType.BASIC as in the original, but can be changed if needed
            List<Text> tooltip = stack.getTooltip(context, mc.player, TooltipType.BASIC);

            for (Text line : tooltip) {
                String s = line.getString();
                if (s == null || s.isEmpty()) continue;

                // 1. Check for the specific "You must be" line (most reliable for required prestige)
                Matcher requiredMatcher = REQUIRED_PRESTIGE_PATTERN.matcher(s);
                if (requiredMatcher.find()) {
                    String num = requiredMatcher.group(1);
                    if (num != null) {
                        // This is the number we need, return it immediately
                        String cleaned = num.replaceAll(",", "");
                        try {
                            return Integer.parseInt(cleaned);
                        } catch (NumberFormatException ignored) { }
                    }
                }

                // 2. Fallback to the general PRESTIGE_PATTERN
                // This is less reliable as it could potentially find the "Prestige ➟ 0" line,
                // but is kept as a safeguard if the first line is phrased differently.
                Matcher generalMatcher = PRESTIGE_PATTERN.matcher(s);
                if (generalMatcher.find()) {
                    String num = generalMatcher.group(1);
                    if (num != null) {
                        // Check if this number is 0 (i.e., the current prestige). If so, ignore it.
                        String cleaned = num.replaceAll(",", "");
                        if (!cleaned.equals("0")) {
                            try {
                                // Assume any non-zero prestige number found in the tooltip is the required prestige.
                                return Integer.parseInt(cleaned);
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                }
            }

            // Add fallback: check raw NBT lore if present (as hinted in original code)
            // ... (implementation for NBT parsing would go here) ...

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return -1;
    }

    /** Very small helper to extract "text" from simple JSON text components if present. */
    private static String stripJsonTextComponent(String raw) {
        if (raw == null) return "";
        try {
            java.util.regex.Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(raw);
            if (m.find()) return m.group(1);
        } catch (Throwable ignored) {}
        return raw.replaceAll("[{}\"]", "").trim();
    }

    // ---------------------------
    // Chat / command sending helper (multiple fallbacks)
    // ---------------------------
    public static void sendChatCommand(String message) {
        if (message.startsWith("/"))
            MinecraftClient.getInstance().player.networkHandler.sendChatCommand(message.substring(1));
        else
            MinecraftClient.getInstance().player.networkHandler.sendChatMessage(message);
    }
}
