package com.regionstory.client;

import com.regionstory.RegionStoryMod;
import com.regionstory.data.DialogueDefinition;
import com.regionstory.data.DialogueManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public final class RegionStoryClient implements ClientModInitializer {
    private static final int HINT_CLICK_DURATION = 5;
    private static final int HINT_KEY_WIDTH = 20;
    private static final int HINT_KEY_GAP = 6;
    private static final int HINT_BOX_HEIGHT = 18;
    private static final float HINT_TEXT_SCALE = 0.84F;
    public static KeyBinding TALK_KEY;
    public static String currentRegion = "";
    public static String currentPrompt = "";
    public static String currentIcon = "";
    private static int hintClickTicks;
    private static boolean hintRequestSent;
    private static final DialogueManager CLIENT_DIALOGUES = new DialogueManager();

    @Override
    public void onInitializeClient() {
        RegionStoryConfig.load();
        TALK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.regionstory.talk", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F,
                net.minecraft.client.option.KeyBinding.Category.create(RegionStoryMod.REGION_HINT)));

        ClientPlayNetworking.registerGlobalReceiver(RegionStoryMod.RegionHintPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    currentRegion = payload.regionId();
                    currentPrompt = payload.prompt();
                    currentIcon = payload.icon();
                    if (currentRegion.isEmpty()) {
                        hintClickTicks = 0;
                        hintRequestSent = false;
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(RegionStoryMod.OpenDialoguePayload.ID, (payload, context) ->
                context.client().execute(() -> receiveDialogue(payload)));

        ClientPlayNetworking.registerGlobalReceiver(RegionStoryMod.CloseDialoguePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().currentScreen instanceof DialogueScreen screen) {
                        screen.closeFromServer();
                    }
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            currentRegion = "";
            currentPrompt = "";
            currentIcon = "";
            hintClickTicks = 0;
            hintRequestSent = false;
            CLIENT_DIALOGUES.clear();
            CameraTransitionController.reset(client);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CameraTransitionController.tick(client);
            if (hintClickTicks > 0) {
                hintClickTicks++;
                if (hintClickTicks >= HINT_CLICK_DURATION) {
                    if (!hintRequestSent && !currentRegion.isEmpty() && client.currentScreen == null) {
                        ClientPlayNetworking.send(new RegionStoryMod.StartDialoguePayload(currentRegion));
                        hintRequestSent = true;
                    }
                    hintClickTicks = 0;
                    hintRequestSent = false;
                }
            }
            if (TALK_KEY.wasPressed() && !currentRegion.isEmpty() && client.currentScreen == null) {
                ClientPlayNetworking.send(new RegionStoryMod.StartDialoguePayload(currentRegion));
            }
        });

        HudRenderCallback.EVENT.register(RegionStoryClient::renderHint);
    }

    private static void receiveDialogue(RegionStoryMod.OpenDialoguePayload payload) {
        DialogueDefinition dialogue = CLIENT_DIALOGUES.loadSerialized(payload.json());
        if (dialogue == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof DialogueScreen screen) {
            screen.applyEntry(dialogue, payload.entryId());
        } else {
            client.setScreen(new DialogueScreen(dialogue, payload.entryId()));
        }
    }

    private static void renderHint(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || currentRegion.isEmpty()) return;

        int[] bounds = hintBounds(client);
        int x = bounds[0], y = bounds[1], boxWidth = bounds[2], boxHeight = bounds[3];
        int mainWidth = boxWidth - HINT_KEY_WIDTH - HINT_KEY_GAP;
        int panelX = x + HINT_KEY_WIDTH + HINT_KEY_GAP;
        float pulse = RegionStoryUi.clickPulse(hintClickTicks, HINT_CLICK_DURATION);
        if (pulse > 0.0F) {
            RegionStoryUi.fillCapsule(drawContext, panelX - 2, y - 2, mainWidth + 4, boxHeight + 4,
                    RegionStoryUi.blend(0x00000000, 0xB8FFE777, pulse));
        }
        RegionStoryUi.drawDialoguePanel(drawContext, panelX, y, mainWidth, boxHeight);
        if (pulse > 0.0F) {
            RegionStoryUi.fillCapsule(drawContext, panelX, y, mainWidth, boxHeight,
                    RegionStoryUi.blend(0x00000000, 0x52FFF0A0, pulse));
        }

        int keyX = x;
        int keyHeight = 18;
        int keyY = y + (boxHeight - keyHeight) / 2;
        RegionStoryUi.drawCapsule(drawContext, keyX, keyY, HINT_KEY_WIDTH, keyHeight,
                RegionStoryUi.blend(0xFFF4F5F7, 0xFFFFF8BA, pulse),
                RegionStoryUi.blend(0xFFFFFFFF, 0xFFFFF3A8, pulse));
        int keyTextWidth = RegionStoryUi.width(client.textRenderer, "F");
        RegionStoryUi.drawTextScaled(drawContext, client.textRenderer, "F", 0.9F,
                keyX + (HINT_KEY_WIDTH - keyTextWidth * 0.9F) / 2.0F, keyY + 2.0F,
                RegionStoryUi.blend(0xFF202833, 0xFF6B4A16, pulse));

        if (currentIcon.isBlank()) {
            RegionStoryUi.drawReferenceChatIcon(drawContext, panelX + 10, y + 2, 16);
        } else {
            RegionStoryUi.drawIcon(drawContext, client, currentIcon,
                    panelX + 8, y + 2, 15,
                    RegionStoryUi.blend(0xFFF6F8FB, 0xFFFFF3AE, pulse));
        }
        String label = hintLabel();
        int labelHeight = Math.max(1, Math.round(client.textRenderer.fontHeight * HINT_TEXT_SCALE));
        int labelY = y + Math.max(1, (boxHeight - labelHeight) / 2);
        RegionStoryUi.drawTextScaled(drawContext, client.textRenderer, label, HINT_TEXT_SCALE,
                panelX + 36, labelY,
                RegionStoryUi.blend(0xFFF6F2E7, 0xFFFFF7C5, pulse));
    }

    public static boolean isHintClicked(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || currentRegion.isEmpty()) return false;
        mouseX = mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        mouseY = mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        int[] bounds = hintBounds(client);
        return mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3];
    }

    /** 开始提示条的点击高亮动画，动画结束后由客户端 tick 发送请求。 */
    public static boolean beginHintClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || currentRegion.isEmpty() || hintClickTicks > 0) {
            return false;
        }
        hintClickTicks = 1;
        hintRequestSent = false;
        return true;
    }

    private static int[] hintBounds(MinecraftClient client) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int textWidth = Math.round(RegionStoryUi.width(client.textRenderer, hintLabel()) * HINT_TEXT_SCALE);
        int mainWidth = Math.max(156, textWidth + 54);
        int boxWidth = mainWidth + HINT_KEY_WIDTH + HINT_KEY_GAP;
        int boxHeight = HINT_BOX_HEIGHT;
        int panelX = MathHelper.clamp((int) (width * 0.6f),
                16 + HINT_KEY_WIDTH + HINT_KEY_GAP,
                Math.max(16 + HINT_KEY_WIDTH + HINT_KEY_GAP, width - mainWidth - 16));
        int x = panelX - HINT_KEY_WIDTH - HINT_KEY_GAP;
        int y = MathHelper.clamp((int) (height * 0.56f), 16, Math.max(16, height - boxHeight - 16));
        return new int[]{x, y, boxWidth, boxHeight};
    }

    private static String hintLabel() {
        String label = currentPrompt == null ? "" : currentPrompt.trim();
        return label.replaceFirst("^按\\s*[Ff]\\s*", "").trim();
    }
}
