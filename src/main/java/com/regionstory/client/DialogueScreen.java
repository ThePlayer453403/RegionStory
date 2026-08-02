package com.regionstory.client;

import com.regionstory.RegionStoryMod;
import com.regionstory.data.DialogueDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** 服务端权威的原神式对话界面。 */
public final class DialogueScreen extends Screen {
    private static final int OPTION_CLICK_DURATION = 5;
    private static final int HOVER_PULSE_PERIOD = 20;
    private static final int OPTION_ICON_SIZE = 14;
    private static final int OPTION_LINE_HEIGHT = 8;
    private static final float OPTION_TEXT_SCALE = 0.84F;
    private DialogueDefinition dialogue;
    private String entryId;
    private int transitionTicks;
    private boolean transitionStarted;
    private boolean serverClosing;
    private int selectedOption = -1;
    private int selectedOptionTicks;
    private int hoverPulseTicks;
    private final List<int[]> optionRects = new ArrayList<>();

    public DialogueScreen(DialogueDefinition dialogue, String entryId) {
        super(Text.literal("RegionStory"));
        this.dialogue = dialogue;
        this.entryId = entryId;
    }

    @Override
    protected void init() {
        if (!transitionStarted) {
            CameraTransitionController.beginEnter(client);
            transitionStarted = true;
        }
        transitionTicks = 0;
    }

    @Override
    public boolean shouldPause() { return false; }

    /** 对话界面不绘制原版 Screen 背景，避免其他界面增强模组给世界加模糊。 */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void tick() {
        super.tick();
        transitionTicks = Math.min(20, transitionTicks + 1);
        hoverPulseTicks = (hoverPulseTicks + 1) % HOVER_PULSE_PERIOD;
        if (selectedOption >= 0) {
            selectedOptionTicks++;
            if (selectedOptionTicks >= OPTION_CLICK_DURATION) {
                int option = selectedOption;
                selectedOption = -1;
                selectedOptionTicks = 0;
                ClientPlayNetworkingBridge.choose(dialogue.id, entryId, option);
            }
        }
    }

    public void applyEntry(DialogueDefinition dialogue, String entryId) {
        if (dialogue != null && dialogue.entry(entryId) != null) {
            this.dialogue = dialogue;
            this.entryId = entryId;
        }
    }

    public void closeFromServer() {
        serverClosing = true;
        CameraTransitionController.beginExit(client);
        client.setScreen(null);
    }

    @Override
    public void removed() {
        super.removed();
        if (!serverClosing) {
            CameraTransitionController.beginExit(client);
            ClientPlayNetworkingBridge.close(dialogue.id);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        DialogueDefinition.Entry entry = dialogue.entry(entryId);
        float ease = MathHelper.clamp(transitionTicks / 20f, 0f, 1f);
        int fade = (int) ((1f - ease) * 120f);
        ctx.fill(0, 0, width, height, (fade << 24));

        // 通栏不是一个独立的圆角面板，而是贴近屏幕底部的纵向渐变遮罩。
        // 高度随台词行数略微增加，保证长文本仍然能在遮罩内部保持呼吸感。
        int dialogueLineCount = entry == null ? 1 : Math.max(1, wrap(entry.text(), width - 120).size());
        int bandHeight = MathHelper.clamp(160 + Math.max(0, dialogueLineCount - 1) * 14, 160, 220);
        int bandTop = height - bandHeight;
        drawDialogueBand(ctx, bandTop, bandHeight);

        List<String> dialogueLines = entry == null ? List.of() : wrap(entry.text(), width - 120);

        optionRects.clear();
        if (entry != null) {
            int centerX = width / 2;
            int speakerY = bandTop + 44;
            drawCentered(ctx, entry.speaker(), centerX, speakerY, 0xFFFFD34F);
            int lineY = speakerY + 10;
            if (entry.speakerTitle() != null && !entry.speakerTitle().isBlank()) {
                drawTitleRule(ctx, centerX, lineY + 8, entry.speakerTitle());
                drawCentered(ctx, entry.speakerTitle(), centerX, lineY, 0xFFFFD34F);
                // 身份标签上移后，正文仍保持原来的视觉高度。
                lineY += 31;
            }
            int continuationY = bandTop + bandHeight - 18;
            int bodyLineHeight = 13;
            int bodyHeight = dialogueLines.size() * bodyLineHeight;
            // 单行台词保持参考图的固定层级；多行台词才向上收紧，避免碰到继续指示器。
            int bodyY = Math.min(lineY + 3, continuationY - bodyHeight - 28);
            for (String line : dialogueLines) {
                drawCentered(ctx, line, centerX, bodyY, 0xFFF7F7F2);
                bodyY += bodyLineHeight;
            }
            if (entry.options().isEmpty()) {
                // 菱形始终停靠在通栏下缘，提示玩家点击或按键继续。
                RegionStoryUi.drawHoverDiamond(ctx, centerX, continuationY, 8, 0xFFFFC52E);
            }

            if (!entry.options().isEmpty()) {
                int optionX = MathHelper.clamp((int) (width * 0.6f), 190, width - 190);
                int optionW = Math.max(190, Math.min(width - optionX - 28,
                        Math.max(190, (int) (width * 0.29f))));
                List<Integer> optionHeights = new ArrayList<>();
                int optionTotalH = 0;
                for (int i = 0; i < entry.options().size(); i++) {
                    int textWidth = Math.max(80,
                            Math.round((optionW - 46) / OPTION_TEXT_SCALE));
                    int lineCount = wrap(entry.options().get(i).text(), textWidth).size();
                    int optionH = Math.max(18, lineCount * OPTION_LINE_HEIGHT + 3);
                    optionHeights.add(optionH);
                    optionTotalH += optionH;
                }
                optionTotalH += Math.max(0, (entry.options().size() - 1) * 8);
                int optionY = Math.max(12, bandTop - optionTotalH - 12);
                for (int i = 0; i < entry.options().size(); i++) {
                    int optionH = optionHeights.get(i);
                    optionRects.add(new int[]{optionX, optionY, optionW, optionH});
                    boolean hover = mouseX >= optionX && mouseX <= optionX + optionW
                            && mouseY >= optionY && mouseY <= optionY + optionH;
                    float hoverPulse = hover
                            ? 0.5F + 0.5F * (float) Math.sin((hoverPulseTicks / (float) HOVER_PULSE_PERIOD) * Math.PI * 2.0)
                            : 0.0F;
                    float pulse = i == selectedOption
                            ? RegionStoryUi.clickPulse(selectedOptionTicks, OPTION_CLICK_DURATION) : 0.0F;
                    int baseFill = hover ? 0xE34C5968 : 0xD82A3544;
                    if (hover) {
                        int glow = RegionStoryUi.blend(0x182B3948, 0xB8F2F6FA, hoverPulse);
                        RegionStoryUi.fillCapsule(ctx, optionX - 2, optionY - 2,
                                optionW + 4, optionH + 4, glow);
                        RegionStoryUi.drawHoverArrow(ctx, optionX - 9,
                                optionY + optionH / 2, 8,
                                RegionStoryUi.blend(0xFFB9C2CB, 0xFFFFF4C7, hoverPulse));
                    }
                    if (pulse > 0.0F) {
                        RegionStoryUi.fillCapsule(ctx, optionX - 2, optionY - 2,
                                optionW + 4, optionH + 4,
                                RegionStoryUi.blend(0x00000000, 0xB8FFE777, pulse));
                    }
                    if (pulse > 0.0F) {
                        // 点击峰值短暂使用纯色高亮，结束后回到高分辨率纹理底图。
                        RegionStoryUi.drawDialoguePanel(ctx, optionX, optionY, optionW, optionH);
                        RegionStoryUi.fillCapsule(ctx, optionX, optionY, optionW, optionH,
                                RegionStoryUi.blend(0x00000000, 0x58FFF0A0, pulse));
                    } else {
                        // 普通状态使用高分辨率纹理，避免扫描线圆角产生低像素锯齿。
                        RegionStoryUi.drawDialoguePanel(ctx, optionX, optionY, optionW, optionH);
                    }
                    RegionStoryUi.drawIcon(ctx, client, entry.options().get(i).icon(),
                            optionX + 7, optionY + (optionH - OPTION_ICON_SIZE) / 2, OPTION_ICON_SIZE,
                            RegionStoryUi.blend(0xFFF8FAFC, 0xFFFFF5B8, pulse));
                    List<String> optionLines = wrap(entry.options().get(i).text(),
                            Math.max(80, Math.round((optionW - 46) / OPTION_TEXT_SCALE)));
                    int textBlockHeight = optionLines.size() * OPTION_LINE_HEIGHT;
                    int textY = optionY + (optionH - textBlockHeight) / 2 + 1;
                    for (String line : optionLines) {
                        RegionStoryUi.drawTextScaled(ctx, textRenderer, line, OPTION_TEXT_SCALE,
                                optionX + 34, textY,
                                RegionStoryUi.blend(hover ? 0xFFFFFFFF : 0xFFF8F8F8, 0xFFFFF8C8, pulse));
                        textY += OPTION_LINE_HEIGHT;
                    }
                    if (hover) {
                        RegionStoryUi.drawHoverDiamond(ctx, mouseX + 10, mouseY + 10, 10,
                                RegionStoryUi.blend(0xFFE7B83D, 0xFFFFF5B8, hoverPulse));
                    }
                    optionY += optionH + 8;
                }
            }
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** 绘制全屏通栏的上下渐隐背景，不遮挡场景也不使用模糊。 */
    private void drawDialogueBand(DrawContext ctx, int top, int height) {
        int center = top + height / 2;
        float half = Math.max(1, height / 2.0F);
        for (int row = 0; row < height; row++) {
            float distance = Math.abs((top + row) - center) / half;
            float strength = MathHelper.clamp(1.0F - distance, 0.0F, 1.0F);
            // 中央最深、上下边缘渐隐，避免出现硬切的矩形边界。
            strength *= strength * (0.88F + 0.12F * strength);
            int alpha = Math.round(204.0F * strength);
            ctx.fill(0, top + row, width, top + row + 1,
                    (alpha << 24) | 0x07111E);
        }
    }

    private void drawCentered(DrawContext ctx, String value, int centerX, int y, int color) {
        int textWidth = RegionStoryUi.width(textRenderer, value);
        RegionStoryUi.drawText(ctx, textRenderer, value, centerX - textWidth / 2, y, color);
    }

    /** 身份标签两侧的金色装饰线和小点。 */
    private void drawTitleRule(DrawContext ctx, int centerX, int y, String title) {
        int titleWidth = RegionStoryUi.width(textRenderer, title);
        int gap = titleWidth / 2 + 13;
        int halfRule = Math.max(48, Math.min(150, gap + 44));
        int color = 0xD9D8A54A;
        int softColor = 0x9BD8A54A;
        ctx.fill(centerX - halfRule, y, centerX - gap, y + 1, color);
        ctx.fill(centerX + gap, y, centerX + halfRule, y + 1, color);
        // 细线外再加一层很淡的点状装饰，接近参考图的金色身份分隔线。
        ctx.fill(centerX - halfRule, y + 2, centerX - gap - 6, y + 3, softColor);
        ctx.fill(centerX + gap + 6, y + 2, centerX + halfRule, y + 3, softColor);
        RegionStoryUi.drawHoverDiamond(ctx, centerX - halfRule - 5, y, 5, color);
        RegionStoryUi.drawHoverDiamond(ctx, centerX + halfRule + 5, y, 5, color);
    }

    private void drawPanel(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 3, y, x + w - 3, y + h, color);
        ctx.fill(x, y + 3, x + w, y + h - 3, color);
        int gold = 0xD6D5A24A;
        ctx.fill(x + 4, y, x + w - 4, y + 1, gold);
        ctx.fill(x + 4, y + h - 1, x + w - 4, y + h, gold);
        ctx.fill(x, y + 4, x + 1, y + h - 4, gold);
        ctx.fill(x + w - 1, y + 4, x + w, y + h - 4, gold);
    }

    /** 绘制灰色胶囊按钮，并使用亮白色外框。 */
    private void drawCapsule(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        // 外围只保留一圈低透明度白光，避免边缘显得厚重。
        fillCapsule(ctx, x - 1, y - 1, w + 2, h + 2, 0x45F5F6F7);
        fillCapsule(ctx, x, y, w, h, border);
        int inset = 1;
        if (w > inset * 2 && h > inset * 2) {
            fillCapsule(ctx, x + inset, y + inset, w - inset * 2, h - inset * 2, fill);
        }
    }

    /** 按扫描线绘制左右各一个半圆的胶囊，避免矩形叠加覆盖边框。 */
    private void fillCapsule(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int radius = Math.min(h / 2, w / 2);
        if (radius <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }

        double center = (h - 1) / 2.0;
        double radiusSquared = (double) radius * radius;
        for (int row = 0; row < h; row++) {
            double dy = Math.abs(row - center);
            int inset = dy >= radius
                    ? radius
                    : (int) Math.ceil(radius - Math.sqrt(Math.max(0.0, radiusSquared - dy * dy)));
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            String candidate = line.toString() + character;
            if ((character == '\n' || RegionStoryUi.width(textRenderer, candidate) > maxWidth) && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(character == '\n' ? "" : String.valueOf(character));
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (selectedOption >= 0) return true;
        double mouseX = click.x();
        double mouseY = click.y();
        for (int i = 0; i < optionRects.size(); i++) {
            int[] rect = optionRects.get(i);
            if (mouseX >= rect[0] && mouseX <= rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY <= rect[1] + rect[3]) {
                selectedOption = i;
                selectedOptionTicks = 1;
                return true;
            }
        }
        DialogueDefinition.Entry entry = dialogue.entry(entryId);
        if (entry != null && entry.options().isEmpty()) ClientPlayNetworkingBridge.advance(dialogue.id, entryId);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        if (keyCode == 256) {
            ClientPlayNetworkingBridge.close(dialogue.id);
            return true;
        }
        DialogueDefinition.Entry entry = dialogue.entry(entryId);
        if (entry != null && entry.options().isEmpty()) ClientPlayNetworkingBridge.advance(dialogue.id, entryId);
        return true;
    }

    private static final class ClientPlayNetworkingBridge {
        private static void advance(String dialogueId, String entryId) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new RegionStoryMod.AdvanceDialoguePayload(dialogueId, entryId));
        }

        private static void choose(String dialogueId, String entryId, int optionIndex) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new RegionStoryMod.SelectOptionPayload(dialogueId, entryId, optionIndex));
        }

        private static void close(String dialogueId) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new RegionStoryMod.CloseDialoguePayload(dialogueId));
        }
    }
}
