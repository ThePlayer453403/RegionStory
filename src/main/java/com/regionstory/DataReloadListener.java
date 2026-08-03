package com.regionstory;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** 数据包热重载：同时刷新区域与对话定义。 */
public final class DataReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public Identifier getFabricId() { return Identifier.of(RegionStoryMod.MOD_ID, "story_data"); }

    @Override
    public void reload(ResourceManager manager) {
        RegionStoryMod.DIALOGUES.reload(manager);
        RegionStoryMod.REGIONS.reload(manager);
        RegionStoryMod.REGIONS.validateDialogueReferences(RegionStoryMod.DIALOGUES);
        RegionStoryMod.resetSessionsAfterReload();
    }
}
