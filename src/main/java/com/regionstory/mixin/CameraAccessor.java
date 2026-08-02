package com.regionstory.mixin;

import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.render.Camera;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPos")
    void regionstory$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void regionstory$setRotation(float yaw, float pitch);
}
