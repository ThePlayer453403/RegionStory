package com.regionstory.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.regionstory.client.RegionStoryClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
    @Inject(method = "forAllKeyBinds",
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", shift = At.Shift.AFTER),
            cancellable = true)
    private static void regionstory$blockOtherKey(InputUtil.Key key, Consumer<KeyBinding> keyConsumer, CallbackInfo ci, @Local List<KeyBinding> list) {
        if (list.contains(RegionStoryClient.TALK_KEY) && !RegionStoryClient.currentRegion.isEmpty()) {
            keyConsumer.accept(RegionStoryClient.TALK_KEY);
            ci.cancel();
        }
    }
}
