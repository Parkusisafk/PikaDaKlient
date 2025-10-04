package com.pikadaklient;

import net.fabricmc.api.ClientModInitializer;
import java.io.IOException;

public class ClientInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("PikaDaKlient Client Initialized!");

        // Start the AHK script on client init
        //startAHKScript();
    }

    private void startAHKScript() {
        // Get the AHK script path from environment variable or fallback
        String scriptPath = System.getenv("AHK_SCRIPT_PATH");
        if (scriptPath == null || scriptPath.isEmpty()) {
            scriptPath = "C:\\Users\\imadu\\OneDrive\\Desktop\\pika.exe"; // fallback
        }

        try {
            // Start the script in a new process (non-blocking)
            new ProcessBuilder("C:\\Program Files\\AutoHotkey\\AutoHotkey.exe", scriptPath).start();
            System.out.println("[AHKUtils] AHK script started.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[AHKUtils] Failed to start AHK script.");
        }
    }
}
