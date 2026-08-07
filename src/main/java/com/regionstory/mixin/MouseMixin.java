package com.regionstory.mixin;

import com.regionstory.client.RegionStoryClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps gameplay camera input separate from Screen and hint interactions. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void regionstory$scrollHint(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (vertical > 0) {
            RegionStoryClient.selectedRegionIndex += 1;
            if (RegionStoryClient.selectedRegionIndex >= RegionStoryClient.currentRegion.size()) {
                RegionStoryClient.selectedRegionIndex = 0;
            }
        } else if (vertical < 0) {
            RegionStoryClient.selectedRegionIndex -= 1;
        }
    }
}
