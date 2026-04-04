package dev.muhofy.furnaceboard.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers all FurnaceBoard keybindings.
 * Call register() once during client init.
 *
 * Default keybind: F (GLFW.GLFW_KEY_F) — opens the FurnaceBoard dashboard.
 *
 * KeyBinding.Category.create(Identifier) verified in Yarn 1.21.11+build.4.
 */
public final class FurnaceBoardKeybinds {

    public static KeyBinding openDashboard;

    // Category identifier — shows as translation key "key.category.furnaceboard" in en_us.json
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("furnaceboard", "general"));

    private FurnaceBoardKeybinds() {}

    public static void register() {
        openDashboard = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.furnaceboard.open_dashboard",  // translation key (in en_us.json)
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,                    // default: F
                CATEGORY
        ));

        FurnaceBoardLogger.info("Keybinds registered.");
    }
}