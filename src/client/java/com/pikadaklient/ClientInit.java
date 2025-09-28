package com.pikadaklient;

import net.fabricmc.api.ClientModInitializer;

public class ClientInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("PikaDaKlient Client Initialized!");
        // Any other client-side setup can go here
    }
}
