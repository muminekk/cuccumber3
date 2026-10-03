package com.cucumber;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CucumberMod implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        HudRenderCallback.EVENT.register(HudOverlay::render);
        WorldRenderEvents.AFTER_ENTITIES.register(NameTags::render);
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cucumber.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.cucumber"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Bomb.tick(client);
            while (openKey.wasPressed()) {
                if (client.world != null && client.currentScreen == null) {
                    client.setScreen(new CucumberScreen());
                }
            }
        });
    }
}
