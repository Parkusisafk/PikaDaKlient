package com.pikadaklient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public class ServerCheck {
    public static void printServer() {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Singleplayer world check
        if (mc.isIntegratedServerRunning()) {
            System.out.println("Connected to: Singleplayer");
            return;
        }

        // Multiplayer server check
        ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (serverInfo != null) {
            System.out.println("Connected to server: " + serverInfo.address);
            System.out.println("Server name: " + serverInfo.name);
        } else {
            System.out.println("Not connected to any server.");
        }
    }
}
