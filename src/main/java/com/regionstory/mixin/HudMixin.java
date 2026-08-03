package com.regionstory.mixin;

import com.regionstory.client.CameraTransitionController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide the vanilla in-game HUD while the dialogue screen owns the scene. */
@Mixin(InGameHud.class)
public abstract class HudMixin {
    @Inject(method = "renderMainHud", at = @At("HEAD"), cancellable = true)
    private void regionstory$hideMainHud(DrawContext context, RenderTickCounter tickCounter,
                                         CallbackInfo callbackInfo) {
        if (CameraTransitionController.dialogueActive()) callbackInfo.cancel();
    }
}
