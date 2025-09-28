package com.pikadaklient.mixin;

import com.pikadaklient.utils.AutoClickerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// We mix into MinecraftClient so we can inject into the client tick
@Mixin(MinecraftClient.class)
public class TickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;

        // Ensure player and world are loaded
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return;

        // Call the AutoClicker tick function
        AutoClickerUtils.tick(mc);
    }
}
