package com.regionstory.client;

import com.regionstory.RegionStoryMod;
import com.regionstory.client.renderstates.ContinueIconElementRenderState;
import com.regionstory.data.DialogueDefinition;
import com.tp4.genshinlib.client.GILText;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/** Dialogue screen. Shapes are shader-rendered; Minecraft TextRenderer remains the font backend. */
public final class DialogueScreen extends Screen {
    private static final int TYPING_SPEED_MILLISECOND = 30;
    private static final int BODY_LINE_HEIGHT = 12;
    private static final int DIALOGUE_BASE_HEIGHT = 88;
    private static final int DIALOGUE_MAX_HEIGHT = 208;
    private static final int DIALOGUE_SIDE_PADDING = 60;
    private static final int HOVER_DIAMOND_Y = 10;

    private static final int K_W = 87;
    private static final int K_S = 83;
    private static final int K_F = 70;
    private static final int K_SPACE = 32;
    private static final int K_ESC = 256;

    private final boolean hudVisible;

    private DialogueDefinition dialogue;
    private String entryId;

    private int mouseHoveredOption = -1;
    private int keyboardSelectedOption = -1;

    private boolean typingAnimation = true;
    private long typingStartTime;

    public DialogueScreen(DialogueDefinition dialogue, String entryId) {
        super(Text.literal("RegionStory"));
        this.hudVisible = MinecraftClient.getInstance().options.hudHidden;
        MinecraftClient.getInstance().options.hudHidden = true;
        CameraTransitionController.beginEnter(client);
        applyEntry(dialogue, entryId);
    }

    @Override
    public boolean shouldPause() {return false;}

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == K_ESC) {
            ClientPlayNetworking.send(new RegionStoryMod.CloseDialoguePayload(dialogue.id));
        } else if (input.getKeycode() == K_F || input.getKeycode() == K_SPACE) {
            if (typingAnimation) {
                typingAnimation = false;
            } else if (!dialogue.entry(entryId).options().isEmpty()) {
                if (keyboardSelectedOption < 0 || input.getKeycode() != K_F) {return true;}
                ClientPlayNetworking.send(new RegionStoryMod.SelectOptionPayload(dialogue.id, entryId, keyboardSelectedOption));
            } else {
                ClientPlayNetworking.send(new RegionStoryMod.AdvanceDialoguePayload(dialogue.id, entryId));
            }
        } else if (input.getKeycode() == K_W) {
            keyboardSelectionChange(1);
        } else if (input.getKeycode() == K_S) {
            keyboardSelectionChange(-1);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (typingAnimation) {
            typingAnimation = false;
            return true;
        } else if (!dialogue.entry(entryId).options().isEmpty()) {
            if (mouseHoveredOption < 0) {return true;}
            ClientPlayNetworking.send(new RegionStoryMod.SelectOptionPayload(dialogue.id, entryId, mouseHoveredOption));
        } else {
            ClientPlayNetworking.send(new RegionStoryMod.AdvanceDialoguePayload(dialogue.id, entryId));
        }
        return true;
    }

    @Override
    public void removed() {
        MinecraftClient.getInstance().options.hudHidden = hudVisible;
        CameraTransitionController.beginExit(client);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        DialogueDefinition.Entry entry = dialogue.entry(entryId);
        List<String> dialogueLines = wrap(entry.text());
        float renderHeight = height - scale(Math.min(DIALOGUE_BASE_HEIGHT + dialogueLines.size() * BODY_LINE_HEIGHT, DIALOGUE_MAX_HEIGHT));

        renderBackground(context, renderHeight);
        renderHeight = renderSpeakerName(context, renderHeight, entry);
        renderDialogueText(context, renderHeight, dialogueLines);
        renderContinueIcon(context, entry);
        renderOptions(context, entry, dialogueLines, mouseX, mouseY);
    }

    private void renderBackground(DrawContext context, float renderHeight) {
        context.drawTexturedQuad(Identifier.of(RegionStoryMod.MOD_ID, "textures/gui/dialogue_background.png"), 0, (int) renderHeight, width, height, 0, 1, 0, 1);
    }

    private float renderSpeakerName(DrawContext context, float renderHeight, DialogueDefinition.Entry entry) {
        renderHeight += scale(20);
        GILText.textRender(context, entry.speaker(), width / 2f, renderHeight).color(0xffffd34f).center().scale(scale(1.3f)).render();
        if (entry.speakerTitle() != null && !entry.speakerTitle().isBlank()) {
            renderHeight += scale(16);
            GILText.textRender(context, entry.speakerTitle(), width / 2f, renderHeight).color(0xffe9b94f).center().scale(scale(1f)).render();
            // TODO: 名称称号两侧的装饰线
        } else {
            // TODO: 名称与正文间的分割线
        }
        return renderHeight;
    }

    private void renderDialogueText(DrawContext context, float renderHeight, List<String> dialogueLines) {
        // 由当前时间减去打字机动画开始时间得出当前应该显示的字数
        int typingCount = (int) (System.currentTimeMillis() - typingStartTime) / TYPING_SPEED_MILLISECOND;
        renderHeight += scale(16);

        for (String line : dialogueLines) {
            if (typingCount >= line.length() || !typingAnimation) {  // 如果typingCount>=文本长度，说明本行文本已经完全显示
                GILText.textRender(context, line, width / 2f, renderHeight).color(0xfff7f7f2).scale(scale(1.3f)).center().render();
            } else if (typingCount > 0) {  // 如果文本长度>typingCount>0，说明打字机动画进行到本行，渲染部分文本
                GILText.textRender(context, line.substring(0, typingCount), width / 2f, renderHeight).color(0xfff7f7f2).scale(scale(1.3f)).center(line).render();
            }  // 否则说明本行文本还不应该渲染
            typingCount -= line.length();  // 获取剩余字数
            renderHeight += scale(BODY_LINE_HEIGHT);
        }

        if (typingCount >= 0) {typingAnimation = false;}  // 如果在完全渲染完后typingCount>=0，即渲染字数大于等于总字数，说明打字机动画结束
    }

    private void renderContinueIcon(DrawContext context, DialogueDefinition.Entry entry) {
        if (typingAnimation || !entry.options().isEmpty()) return;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(width / 2f, height - HOVER_DIAMOND_Y);
        context.getMatrices().scale(scale(0.95f));
        context.state.addSimpleElement(new ContinueIconElementRenderState(new Matrix3x2f(context.getMatrices()), 0xffb02d, context.scissorStack.peekLast()));
        context.getMatrices().popMatrix();
    }

    private void renderOptions(DrawContext context, DialogueDefinition.Entry entry, List<String> dialogueLines, int mouseX, int mouseY) {
        if (typingAnimation) return;
        float y = height - scale(Math.min(DIALOGUE_BASE_HEIGHT + dialogueLines.size() * BODY_LINE_HEIGHT, DIALOGUE_MAX_HEIGHT) + 20);
        int index = 0;
        mouseHoveredOption = -1;
        for (DialogueDefinition.Option option : entry.options()) {
            float x = (1 - scale(1 - DialogueRegionHint.OPTION_ANCHOR_X)) * width;
            boolean mouseHover = x <= mouseX && mouseX <= x + scale(0.95 - DialogueRegionHint.OPTION_ANCHOR_X) * width && y <= mouseY && mouseY <= y + scale(DialogueRegionHint.OPTION_HEIGHT);
            DialogueRegionHint.renderOption(context, (int) x, (int) y, option.text(), mouseHover, index == keyboardSelectedOption);
            y -= scale(DialogueRegionHint.OPTION_HEIGHT + DialogueRegionHint.OPTION_GAP);
            if (mouseHover) {mouseHoveredOption = index;}
            index++;
        }
    }

    private float scale(double number) {return (float) (RegionStoryClient.config.scale * number);}

    private List<String> wrap(String value) {
        String text = value == null ? "" : value;
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int limit = width - (int) scale(DIALOGUE_SIDE_PADDING * 2);
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            String candidate = line + String.valueOf(character);
            if (!line.isEmpty() && GILText.width(candidate) * DialogueRegionHint.OPTION_TEXT_SCALE * RegionStoryClient.config.scale > limit) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(character);
        }
        if (!line.isEmpty() || lines.isEmpty()) lines.add(line.toString());
        return lines;
    }

    public void applyEntry(DialogueDefinition dialogue, String entryId) {
        typingAnimation = true;
        typingStartTime = System.currentTimeMillis();
        if (dialogue != null && dialogue.entry(entryId) != null) {
            this.dialogue = dialogue;
            this.entryId = entryId;
            this.typingStartTime = System.currentTimeMillis();
            this.keyboardSelectedOption = dialogue.entry(entryId).options().size() - 1;
        }
    }

    public void keyboardSelectionChange(double vertical) {
        if (vertical > 0) {
            keyboardSelectedOption ++;
            if (keyboardSelectedOption >= dialogue.entry(entryId).options().size()) {
                keyboardSelectedOption = 0;
            }
        } else if (vertical < 0) {
            keyboardSelectedOption --;
            if (keyboardSelectedOption < 0) {
                keyboardSelectedOption = dialogue.entry(entryId).options().size() - 1;
            }
        }
    }
}
