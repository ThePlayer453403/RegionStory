package com.regionstory.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 客户端镜头参数，使用轻量 JSON 配置以保持仅依赖 Fabric API。 */
public final class RegionStoryConfig {
    public static int enterDuration = 20;
    public static int exitDuration = 20;
    public static double thirdPersonDistance = 4.5D;
    public static double heightOffset = 0.6D;
    /** 相对玩家朝向的镜头偏移；-15 度为偏右的后视角。 */
    public static float yawOffset = -15.0F;
    public static float pitchOffset = 8.0F;

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("regionstory.json");
        try {
            if (Files.exists(path)) {
                JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                enterDuration = Math.max(1, json.has("enterDuration") ? json.get("enterDuration").getAsInt() : enterDuration);
                exitDuration = Math.max(1, json.has("exitDuration") ? json.get("exitDuration").getAsInt() : exitDuration);
                thirdPersonDistance = Math.max(0.5D, json.has("thirdPersonDistance") ? json.get("thirdPersonDistance").getAsDouble() : thirdPersonDistance);
                heightOffset = json.has("heightOffset") ? json.get("heightOffset").getAsDouble() : heightOffset;
                yawOffset = json.has("yawOffset") ? json.get("yawOffset").getAsFloat() : yawOffset;
                // 兼容旧版默认值：180 度是前视角，迁移为新的右后方默认角度。
                if (yawOffset == 180.0F) yawOffset = -15.0F;
                pitchOffset = json.has("pitchOffset") ? json.get("pitchOffset").getAsFloat() : pitchOffset;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // 配置损坏时使用默认值，避免阻塞客户端启动。
        }
    }

    private static JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("enterDuration", enterDuration); result.addProperty("exitDuration", exitDuration);
        result.addProperty("thirdPersonDistance", thirdPersonDistance); result.addProperty("heightOffset", heightOffset);
        result.addProperty("yawOffset", yawOffset); result.addProperty("pitchOffset", pitchOffset);
        return result;
    }

    private RegionStoryConfig() {}
}
