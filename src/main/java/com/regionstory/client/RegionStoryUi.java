package com.regionstory.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 对话与区域提示共用的字体、胶囊和图标绘制工具。 */
public final class RegionStoryUi {
    public static final Identifier FONT = Identifier.of("regionstory", "dialogue");
    // 保留资源 ID 供数据包图标使用；对话框本体使用程序绘制，避免拉伸贴图破坏圆角比例。
    public static final Identifier DIALOGUE_PANEL = Identifier.of("regionstory", "textures/gui/dialogue_icon.png");
    public static final Identifier PANEL_LEFT = Identifier.of("regionstory", "textures/gui/dialogue_left.png");
    public static final Identifier PANEL_MIDDLE = Identifier.of("regionstory", "textures/gui/dialogue_middle.png");
    public static final Identifier PANEL_RIGHT = Identifier.of("regionstory", "textures/gui/dialogue_right.png");

    private static final int PANEL_SOURCE_LEFT_WIDTH = 240;
    private static final int PANEL_SOURCE_MIDDLE_WIDTH = 670;
    private static final int PANEL_SOURCE_RIGHT_WIDTH = 410;
    private static final int PANEL_SOURCE_HEIGHT = 475;

    private RegionStoryUi() {}

    public static Text text(String value) {
        return Text.literal(value == null ? "" : value)
                .setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT)));
    }

    public static int width(TextRenderer renderer, String value) {
        return renderer.getWidth(text(value));
    }

    public static void drawText(DrawContext context, TextRenderer renderer,
                                String value, int x, int y, int color) {
        context.drawTextWithShadow(renderer, text(value), x, y, color);
    }

    /** 按 UI 局部比例绘制文字，避免为了缩小选项和提示而影响底部主对话字体。 */
    public static void drawTextScaled(DrawContext context, TextRenderer renderer,
                                      String value, float scale, float x, float y, int color) {
        if (scale <= 0.0F) return;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawTextWithShadow(renderer, text(value), 0, 0, color);
        context.getMatrices().popMatrix();
    }

    /** 将点击动画的进度映射为 0 -> 1 -> 0。 */
    public static float clickPulse(int ticks, int duration) {
        if (ticks <= 0 || duration <= 0) return 0.0F;
        float progress = Math.min(1.0F, ticks / (float) duration);
        return progress < 0.5F ? progress * 2.0F : (1.0F - progress) * 2.0F;
    }

    /** 按 ARGB 通道插值颜色，用于选中高亮。 */
    public static int blend(int from, int to, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int a = Math.round(((from >>> 24) & 0xFF) * (1.0F - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1.0F - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1.0F - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1.0F - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 绘制带柔光的胶囊按钮，边框仅一像素。 */
    public static void drawCapsule(DrawContext context, int x, int y, int w, int h,
                                   int fill, int border) {
        fillCapsule(context, x - 1, y - 1, w + 2, h + 2, 0x42F5F7FA);
        fillCapsule(context, x, y, w, h, border);
        if (w > 2 && h > 2) fillCapsule(context, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /**
     * 绘制原神式深灰胶囊框。
     * 参考图的边缘是等厚圆角，不是将一张长贴图直接拉伸，因此这里使用像素级胶囊绘制。
     */
    public static void drawDialoguePanel(DrawContext context, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getResourceManager().getResource(PANEL_LEFT).isEmpty()
                || client.getResourceManager().getResource(PANEL_MIDDLE).isEmpty()
                || client.getResourceManager().getResource(PANEL_RIGHT).isEmpty()) {
            drawCapsule(context, x, y, w, h, 0xD91F2834, 0xD94A5665);
            return;
        }
        float scale = h / (float) PANEL_SOURCE_HEIGHT;
        int leftW = Math.max(1, Math.round(PANEL_SOURCE_LEFT_WIDTH * scale));
        int rightW = Math.max(1, Math.round(PANEL_SOURCE_RIGHT_WIDTH * scale));
        if (leftW + rightW > w) {
            float fit = w / (float) (leftW + rightW);
            leftW = Math.max(1, Math.round(leftW * fit));
            rightW = Math.max(1, w - leftW);
        }
        int middleW = Math.max(0, w - leftW - rightW);
        drawTexture(context, PANEL_LEFT, x, y, leftW, h,
                PANEL_SOURCE_LEFT_WIDTH, PANEL_SOURCE_HEIGHT);
        if (middleW > 0) {
            drawTexture(context, PANEL_MIDDLE, x + leftW, y, middleW, h,
                    PANEL_SOURCE_MIDDLE_WIDTH, PANEL_SOURCE_HEIGHT);
        }
        drawTexture(context, PANEL_RIGHT, x + leftW + middleW, y, rightW, h,
                PANEL_SOURCE_RIGHT_WIDTH, PANEL_SOURCE_HEIGHT);
    }

    private static void drawTexture(DrawContext context, Identifier texture,
                                    int x, int y, int width, int height,
                                    int sourceWidth, int sourceHeight) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y,
                0.0F, 0.0F, width, height,
                sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    /** 参考图中的白色对话气泡图标，三个深色圆点保持清晰像素边缘。 */
    public static void drawReferenceChatIcon(DrawContext context, int x, int y, int size) {
        int bubbleW = Math.max(16, size);
        int bubbleH = Math.max(11, Math.round(size * 0.68F));
        fillCapsule(context, x, y, bubbleW, bubbleH, 0xFFF6F7F8);
        int tailX = x + Math.max(3, bubbleW / 5);
        context.fill(tailX, y + bubbleH - 2, tailX + Math.max(4, bubbleW / 4), y + bubbleH + 3, 0xFFF6F7F8);
        int dotColor = 0xFF67717A;
        int dotRadius = Math.max(2, size / 9);
        int dotY = y + bubbleH / 2;
        int dotGap = Math.max(4, size / 5);
        int firstX = x + bubbleW / 2 - dotGap;
        for (int i = 0; i < 3; i++) {
            fillCircle(context, firstX + i * dotGap, dotY, dotRadius, dotColor);
        }
    }

    public static void fillCapsule(DrawContext context, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int radius = Math.min(h / 2, w / 2);
        if (radius <= 0) {
            context.fill(x, y, x + w, y + h, color);
            return;
        }
        double center = (h - 1) / 2.0;
        double radiusSquared = (double) radius * radius;
        for (int row = 0; row < h; row++) {
            double dy = Math.abs(row - center);
            int inset = dy >= radius
                    ? radius
                    : (int) Math.ceil(radius - Math.sqrt(Math.max(0.0, radiusSquared - dy * dy)));
            context.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /** 绘制悬停时出现在选项左侧的浅色三角指示。 */
    public static void drawHoverArrow(DrawContext context, int x, int centerY, int size, int color) {
        int half = Math.max(2, size / 2);
        for (int row = -half; row <= half; row++) {
            int width = Math.max(1, half - Math.abs(row));
            context.fill(x, centerY + row, x + width, centerY + row + 1, color);
        }
    }

    /** 绘制跟随鼠标的黄白色菱形光标。 */
    public static void drawHoverDiamond(DrawContext context, int centerX, int centerY,
                                        int size, int color) {
        int half = Math.max(2, size / 2);
        for (int row = -half; row <= half; row++) {
            int width = Math.max(1, half - Math.abs(row));
            context.fill(centerX - width, centerY + row,
                    centerX + width + 1, centerY + row + 1, color);
        }
    }

    /** 图标优先读取资源路径，否则使用内置的白色符号。 */
    public static void drawIcon(DrawContext context, MinecraftClient client, String icon,
                                int x, int y, int size, int color) {
        String key = icon == null ? "" : icon.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.contains(":" ) && !key.startsWith("regionstory:icon/")) {
            try {
                Identifier texture = Identifier.of(icon);
                Identifier resource = texture.getPath().endsWith(".png")
                        ? texture : texture.withPath(path -> path + ".png");
                if (client.getResourceManager().getResource(resource).isPresent()) {
                    // 绘制时也传入带扩展名的 ID，避免 1.21.11 回退到缺失纹理。
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, resource, x, y,
                            0.0F, 0.0F, size, size, size, size);
                    return;
                }
            } catch (Exception ignored) {
                // 非法资源标识符回退到内置图标。
            }
        }

        String symbol = key.startsWith("regionstory:icon/")
                ? key.substring("regionstory:icon/".length()) : key;
        int slot = Math.max(18, size);
        int cx = x + slot / 2;
        int cy = y + slot / 2;
        fillCircle(context, cx, cy, slot / 2 - 1, 0x66333E4D);
        switch (symbol) {
            case "star", "spark", "quest" -> drawStar(context, cx, cy, slot / 2 - 5, color);
            case "compass", "explore" -> drawCompass(context, cx, cy, slot / 2 - 4, color);
            case "map", "location" -> drawMapPin(context, cx, cy, slot / 2 - 4, color);
            case "exit", "leave" -> drawExit(context, x + 3, y + 3, slot - 6, color);
            default -> drawChat(context, x + 3, y + 3, slot - 6, color);
        }
    }

    private static void fillCircle(DrawContext context, int cx, int cy, int radius, int color) {
        if (radius <= 0) return;
        for (int row = -radius; row <= radius; row++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - row * row));
            context.fill(cx - half, cy + row, cx + half + 1, cy + row + 1, color);
        }
    }

    private static void drawStar(DrawContext context, int cx, int cy, int radius, int color) {
        int arm = Math.max(3, radius);
        for (int i = -arm; i <= arm; i++) {
            int width = Math.max(1, (arm - Math.abs(i)) / 3 + 1);
            context.fill(cx - width, cy + i, cx + width + 1, cy + i + 1, color);
            context.fill(cx + i, cy - width, cx + i + 1, cy + width + 1, color);
        }
        context.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
    }

    private static void drawCompass(DrawContext context, int cx, int cy, int radius, int color) {
        for (int i = -radius; i <= radius; i++) {
            int width = Math.max(1, radius / 4 - Math.abs(i) / 4);
            context.fill(cx - width, cy + i, cx + width + 1, cy + i + 1, color);
            context.fill(cx + i, cy - width, cx + i + 1, cy + width + 1, color);
        }
        context.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF27313E);
    }

    private static void drawMapPin(DrawContext context, int cx, int cy, int radius, int color) {
        fillCircle(context, cx, cy - 2, Math.max(3, radius / 2), color);
        for (int row = 0; row < radius; row++) {
            int half = Math.max(1, radius - row);
            context.fill(cx - half, cy + row, cx + half + 1, cy + row + 1, color);
        }
        fillCircle(context, cx, cy - 2, Math.max(1, radius / 5), 0xFF27313E);
    }

    private static void drawChat(DrawContext context, int x, int y, int size, int color) {
        int h = Math.max(8, size - 5);
        fillCapsule(context, x, y, size, h, color);
        context.fill(x + size / 4, y + h - 1, x + size / 2, y + h + 3, color);
        int dot = Math.max(2, size / 7);
        int gap = Math.max(2, size / 8);
        int start = x + (size - dot * 3 - gap * 2) / 2;
        for (int i = 0; i < 3; i++) {
            int dx = start + i * (dot + gap);
            fillCircle(context, dx + dot / 2, y + h / 2, Math.max(1, dot / 2), 0xFF27313E);
        }
    }

    private static void drawExit(DrawContext context, int x, int y, int size, int color) {
        int mid = y + size / 2;
        int thickness = Math.max(2, size / 6);
        context.fill(x + size / 4, mid - thickness / 2, x + size, mid + thickness / 2 + 1, color);
        for (int i = 0; i < size / 2; i++) {
            context.fill(x + size / 4 + i, mid - i, x + size / 4 + i + thickness, mid - i + thickness, color);
            context.fill(x + size / 4 + i, mid + i, x + size / 4 + i + thickness, mid + i + thickness, color);
        }
    }
}
