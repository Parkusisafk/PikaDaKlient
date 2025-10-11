package com.pikadaklient.mixin;

import com.pikadaklient.ScoreboardPrinter;
import com.pikadaklient.utils.AbyssRunner;
import com.pikadaklient.utils.AutoClickerUtils;
import com.pikadaklient.utils.AutoMinerUtils;
import com.pikadaklient.utils.KeyOpenerUtils;
import com.pikadaklient.window.AutoManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class CommandMixin {

	// Use @Shadow to access the chatField text input widget
	@Shadow protected TextFieldWidget chatField;

	// TARGET: public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {

		// Only proceed if the key pressed is ENTER (257) or NUMPAD ENTER (335)
		if (keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_KP_ENTER) {
			return;
		}

		// Get the text from the chat field
		String chatText = this.chatField.getText().trim();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || chatText.isEmpty()) return;

		// Strip the leading slash if present, for unified command handling
		String message = chatText.startsWith("/") ? chatText.substring(1).trim() : chatText;

		System.out.println("[PikadaClient Debug] KeyPress Mixin running. Processed message: \"" + message + "\"");

		if (message.equalsIgnoreCase("sp")) {
			AutoManager manager = AutoManager.getInstance();
			if (manager == null) {
				manager = new AutoManager("sp");
				manager.initialize();
			} else {
				// optionally update action or re-run initialization if needed
				manager.updateCurrentAction("prestigegrind");
				manager.TransferCompleted("sp");
			}

			mc.setScreen(null);
			cir.setReturnValue(true);
			cir.cancel();
		} else if (message.equalsIgnoreCase("ep")) {
			AutoMinerUtils.stop();
			mc.player.sendMessage(Text.literal("§cAutoClicker stopped."), false);
			mc.setScreen(null);
			cir.setReturnValue(true);
			cir.cancel();
		} else if (message.equalsIgnoreCase("okey")){
			mc.setScreen(null);

			KeyOpenerUtils.start();

			cir.setReturnValue(true);
			cir.cancel();
		} else if (message.equalsIgnoreCase("as") || message.equalsIgnoreCase("sa")){
			AutoManager manager = AutoManager.getInstance();
			if (manager == null) {
				manager = new AutoManager("as");
				manager.initialize();
			} else {
				manager.updateCurrentAction("abyssgrind");
				manager.TransferCompleted("as");
			}

			mc.setScreen(null);
			cir.setReturnValue(true);
			cir.cancel();
		} else if (message.equalsIgnoreCase("ae") || message.equalsIgnoreCase("ea")){
			mc.setScreen(null);

			AbyssRunner.stop();

			cir.setReturnValue(true);
			cir.cancel();
		} else if (message.equalsIgnoreCase("sr") || message.equalsIgnoreCase("rs")){
			mc.setScreen(null);

			AutoMinerUtils.runTransferSequenceAsync().thenAccept(success -> {
				System.out.println("Transfer success: " + success);
			});

			cir.setReturnValue(true);
			cir.cancel();
		} else if (message.equalsIgnoreCase("ps") ){
			mc.setScreen(null);

			ScoreboardPrinter.dumpEverything();

			cir.setReturnValue(true);
			cir.cancel();
		}

	}
}