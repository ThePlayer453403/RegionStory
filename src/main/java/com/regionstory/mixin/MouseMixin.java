package com.regionstory.mixin;

import com.regionstory.client.CameraTransitionController;
import com.regionstory.client.RegionStoryClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps gameplay camera input separate from Screen and hint interactions. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"),
            cancellable = true)
    private void regionstory$lockGameplayMouse(double timeDelta, CallbackInfo callbackInfo) {
        // 只拦截玩家转头调用，保留鼠标坐标更新，否则选项悬停无法工作。
        if (CameraTransitionController.dialogueActive()) callbackInfo.cancel();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void regionstory$clickHint(long window, MouseInput input, int action,
                                       CallbackInfo callbackInfo) {
        if (input.button() != 0 || action != 1) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (RegionStoryClient.isHintClicked(client.mouse.getX(), client.mouse.getY())
                && RegionStoryClient.beginHintClick()) {
            callbackInfo.cancel();
        }
    }
}
