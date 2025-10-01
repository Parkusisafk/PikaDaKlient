package com.pikadaklient.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;

import java.util.List;

public class KeyOpenerUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean running = false;
    private static int totalOpened = 0;

    public static void start() {
        running = true;
        loop();
    }

    private static void loop() {
        new Thread(() -> {
            while (running) {
                try {
                    if (!isChestInFront()) {
                        sendChat("No chest detected.");
                        running = false;
                        break;
                    }

                    // Step 1: open chest
                    rightClickChest();

                    // Step 2: wait for GUI
                    waitForGui(GenericContainerScreen.class);
                    Thread.sleep(100);
                    if (!(mc.currentScreen instanceof GenericContainerScreen chest)) {
                        sendChat("No chest GUI opened.");
                        running = false;
                        break;
                    }

                    // Step 3: check slot 14 (index 13 zero-based)
                    ItemStack hookStack = chest.getScreenHandler().getSlot(13).getStack();
                    if (hookStack.getItem() != Items.TRIPWIRE_HOOK) {
                        sendChat("No keys found.");

                        tryCloseGui();
                        Thread.sleep(500);
                        continue;
                    }

                    int keysLeft = parseKeys(hookStack);
                    sendChat("Keys remaining: " + keysLeft);

                    if (keysLeft > 6) {
                        // right click on slot 14
                        clickSlot(chest, 13, true);


                        // wait for "Open Multiple" gui
                        waitForTitle("Multiple");

                        // now click slot 13 (6 keys stack)
                        if (mc.currentScreen instanceof GenericContainerScreen openMultiple) {
                            // now click slot 13 (index 12 zero-based) in this new screen
                            clickSlot(openMultiple, 12, false);
                        }

                        // wait 2s
                        Thread.sleep(2000);

                        // close GUI
                        tryCloseGui();

                        totalOpened += 6;
                    } else {
                        sendChat("Done! Total keys opened: " + totalOpened);
                        running = false;
                        break;
                    }

                    // loop again
                    Thread.sleep(100);

                } catch (Exception e) {
                    e.printStackTrace();
                    running = false;
                }
            }
        }).start();
    }

    private static boolean isChestInFront() {
        if (mc.player == null) return false;
        BlockHitResult hit = (BlockHitResult) mc.player.raycast(4.5, 0, false);
        return mc.world.getBlockState(hit.getBlockPos()).getBlock().getName().getString().toLowerCase().contains("chest");
    }

    private static void rightClickChest() {
        if (mc.player == null || mc.world == null) return;

        // Raycast up to 5 blocks ahead
        BlockHitResult hit = (BlockHitResult) mc.player.raycast(5.0, 0.0f, false);

        // Only interact if the hit is actually a block
        if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    hit
            );
            mc.player.swingHand(Hand.MAIN_HAND);

        }
    }

    private static void waitForGui(Class<?> clazz) throws InterruptedException {
        int timeout = 40;
        while (timeout-- > 0) {
            if (clazz.isInstance(mc.currentScreen)) return;
            Thread.sleep(100);
        }
    }

    private static void waitForTitle(String startsWith) throws InterruptedException {
        int timeout = 40;
        while (timeout-- > 0) {
            if (mc.currentScreen != null &&
                    mc.currentScreen.getTitle().getString().toLowerCase().contains(startsWith.toLowerCase())) return;
            Thread.sleep(100);
        }
    }

    private static int parseKeys(ItemStack stack) {
        if (stack.isEmpty() || mc.player == null || mc.world == null) return 0;

        // Build tooltip context from the client world
        Item.TooltipContext context = Item.TooltipContext.create(mc.world);

        // BASIC = normal tooltip, ADVANCED = with debug info (like F3+H)
        List<Text> tooltip = stack.getTooltip(
                context,
                mc.player,
                net.minecraft.item.tooltip.TooltipType.BASIC
        );

        for (Text line : tooltip) {
            String s = line.getString();
            if (s.contains("Currently stored:")) {
                String num = s.replaceAll("[^0-9]", "");
                try {
                    return Integer.parseInt(num);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static void clickSlot(GenericContainerScreen screen, int slot, boolean right) {
        mc.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slot,
                right ? 1 : 0,
                SlotActionType.PICKUP,
                mc.player
        );
    }

    private static void tryCloseGui() throws InterruptedException {
        int tries = 10;
        while (tries-- > 0) {
            if (mc.currentScreen == null) return;
            mc.player.closeHandledScreen();
            Thread.sleep(200);
        }
    }

    private static void sendChat(String msg) {
        if (mc.player != null)
            mc.player.sendMessage(Text.of(msg), false);
    }
}
