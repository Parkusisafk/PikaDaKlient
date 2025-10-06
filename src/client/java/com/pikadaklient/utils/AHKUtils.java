package com.pikadaklient.utils;

import java.io.File;
import java.io.IOException;

public class AHKUtils {
    // ⚠️ CRITICAL: Change the path to point to your COMPILED EXE file
    private static final String AHK_PROCESS_PATH = "C:\\Users\\imadu\\OneDrive\\Desktop\\pikaclick.exe";

    // The command (e.g., "start" or "stop")
    private static final String COMMAND = "%s";


    public static void startAHK() {
        executeCommand("start");
    }

    public static void stopAHK() {
        executeCommand("stop");
    }

    private static void executeCommand(String cmd) {
        if (!new File(AHK_PROCESS_PATH).exists()) {
            System.err.println("[AHKUtils] Error: AHK Executable not found at path: " + AHK_PROCESS_PATH);
            return;
        }

        try {
            // I HATE MYSELF
            ProcessBuilder builder = new ProcessBuilder(
                    AHK_PROCESS_PATH,
                    cmd
            );

            builder.start();
            System.out.println("[AHKUtils] Command executed: " + cmd);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[AHKUtils] Failed to execute AHK process. Check AHK_PROCESS_PATH.");
        }
    }
}