package com.regionstory.mixin;

import com.regionstory.client.CameraTransitionController;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void regionstory$applyTransition(World area, Entity focusedEntity, boolean thirdPerson,
                                              boolean inverseView, float tickDelta, CallbackInfo callbackInfo) {
        CameraTransitionController.apply((Camera) (Object) this, focusedEntity);
    }
}
