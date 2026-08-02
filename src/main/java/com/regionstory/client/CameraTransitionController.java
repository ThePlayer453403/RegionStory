package com.regionstory.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import com.regionstory.mixin.CameraAccessor;

/**
 * 轻量电影镜头控制器：负责转场进度和目标镜头位置，Camera Mixin 负责最终覆盖渲染镜头。
 */
public final class CameraTransitionController {
    private enum Mode { IDLE, ENTERING, DIALOGUE, EXITING }
    private static Mode mode = Mode.IDLE;
    private static int ticks;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;
    private static int previousBluriness;
    private static boolean blurCaptured;

    public static void beginEnter(MinecraftClient client) {
        if (mode == Mode.IDLE) {
            previousPerspective = client.options.getPerspective();
            previousBluriness = client.options.getMenuBackgroundBlurrinessValue();
            blurCaptured = true;
            // 对话期间保持清晰世界背景；结束时恢复玩家原来的设置。
            client.options.getMenuBackgroundBlurriness().setValue(0);
        }
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        ticks = 0;
        mode = Mode.ENTERING;
    }

    public static void beginExit(MinecraftClient client) {
        ticks = 0;
        mode = Mode.EXITING;
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    public static void tick(MinecraftClient client) {
        if (mode == Mode.IDLE) return;
        ticks++;
        if (mode == Mode.ENTERING && ticks >= RegionStoryConfig.enterDuration) mode = Mode.DIALOGUE;
        if (mode == Mode.EXITING && ticks >= RegionStoryConfig.exitDuration) {
            client.options.setPerspective(previousPerspective);
            restoreBlur(client);
            mode = Mode.IDLE;
        }
    }

    public static boolean active() { return mode != Mode.IDLE; }

    public static void reset(MinecraftClient client) {
        client.options.setPerspective(previousPerspective);
        restoreBlur(client);
        mode = Mode.IDLE;
        ticks = 0;
    }

    private static void restoreBlur(MinecraftClient client) {
        if (blurCaptured) {
            client.options.getMenuBackgroundBlurriness().setValue(previousBluriness);
            blurCaptured = false;
        }
    }

    public static void apply(Camera camera, Entity focusedEntity) {
        if (!active() || focusedEntity == null) return;
        double progress = mode == Mode.EXITING
                ? 1.0D - MathHelper.clamp(ticks / (double) RegionStoryConfig.exitDuration, 0.0D, 1.0D)
                : MathHelper.clamp(ticks / (double) RegionStoryConfig.enterDuration, 0.0D, 1.0D);
        double eased = progress * progress * (3.0D - 2.0D * progress);
        double distance = RegionStoryConfig.thirdPersonDistance * eased;
        // yawOffset 以“玩家朝向”为 0；负值表示从玩家右后方观察。
        double angle = Math.toRadians(focusedEntity.getYaw() + RegionStoryConfig.yawOffset);
        double x = focusedEntity.getX() + Math.sin(angle) * distance;
        double z = focusedEntity.getZ() - Math.cos(angle) * distance;
        double y = focusedEntity.getY() + focusedEntity.getStandingEyeHeight() + RegionStoryConfig.heightOffset * eased;
        CameraAccessor accessor = (CameraAccessor) (Object) camera;
        accessor.regionstory$setPos(x, y, z);
        accessor.regionstory$setRotation(focusedEntity.getYaw() + RegionStoryConfig.yawOffset,
                RegionStoryConfig.pitchOffset * (float) eased);
    }

    private CameraTransitionController() {}
}
