package com.regionstory.mixin;

import com.regionstory.client.RegionStoryClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void regionstory$clickHint(long window, MouseInput button, int action, CallbackInfo callbackInfo) {
        if (button.button() != 0 || action != 1) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (RegionStoryClient.isHintClicked(client.mouse.getX(), client.mouse.getY())) {
            if (RegionStoryClient.beginHintClick()) callbackInfo.cancel();
        }
    }
}
