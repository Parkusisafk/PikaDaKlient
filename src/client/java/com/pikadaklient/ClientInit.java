package com.pikadaklient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.text.Text;

import java.io.IOException;

public class ClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        System.out.println("PikaDaKlient Client Initialized!");
        System.setProperty("java.awt.headless", "false");

        // Start the AHK script on client init
        //startAHKScript();

        // Listen for scoreboard objective packets
        //registerScoreboardListener();
    }

//    private void registerScoreboardListener() {
//        ClientPlayNetworking.registerGlobalReceiver(ScoreboardObjectiveUpdateS2CPacket.class, (client, handler, packet, sender) -> {
//            // Packet can be received on a non-main thread, schedule on client thread
//            client.execute(() -> {
//                Text displayName = packet.getDisplayName();
//                String objectiveName = packet.getName();
//                int mode = packet.get // 0 = create, 1 = remove, 2 = update
//
//                switch (mode) {
//                    case 0: // Create new objective
//                        System.out.println("[Sidebar] New Objective: " + objectiveName + " -> " + displayName.getString());
//                        break;
//                    case 1: // Remove objective
//                        System.out.println("[Sidebar] Remove Objective: " + objectiveName);
//                        break;
//                    case 2: // Update objective
//                        System.out.println("[Sidebar] Update Objective: " + objectiveName + " -> " + displayName.getString());
//                        break;
//                }
//            });
//        });
//    }

    private void startAHKScript() {
        // Get the AHK script path from environment variable or fallback
        String scriptPath = System.getenv("AHK_SCRIPT_PATH");
        if (scriptPath == null || scriptPath.isEmpty()) {
            scriptPath = "C:\\Users\\imadu\\OneDrive\\Desktop\\pika.exe"; // fallback
        }

        try {
            new ProcessBuilder("C:\\Program Files\\AutoHotkey\\AutoHotkey.exe", scriptPath).start();
            System.out.println("[AHKUtils] AHK script started.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[AHKUtils] Failed to start AHK script.");
        }
    }
}
