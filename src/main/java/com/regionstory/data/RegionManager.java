package com.regionstory.data;

import com.google.gson.*;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("RegionStory/Region");
    private final Map<String, RegionDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeRegions = new ConcurrentHashMap<>();

    public void reload(ResourceManager manager) {
        Map<String, RegionDefinition> next = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("regions", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String id = json.has("id") ? json.get("id").getAsString() : fallbackId(entry.getKey(), "regions");
                if (id.isBlank()) throw new IllegalArgumentException("id 不能为空");
                Identifier dimension = Identifier.of(json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld");
                RegionDefinition.Type type = RegionDefinition.Type.valueOf(
                        json.has("type") ? json.get("type").getAsString().toUpperCase(Locale.ROOT) : "SPHERE");
                double[] center = requiredArray(json, "center", type == RegionDefinition.Type.SPHERE);
                double radius = json.has("radius") ? json.get("radius").getAsDouble() : 0;
                double[] min = requiredArray(json, "min", type == RegionDefinition.Type.BOX);
                double[] max = requiredArray(json, "max", type == RegionDefinition.Type.BOX);
                if (type == RegionDefinition.Type.SPHERE && radius <= 0) throw new IllegalArgumentException("sphere 的 radius 必须大于 0");
                String prompt = json.has("prompt") ? json.get("prompt").getAsString() : "按 F 对话";
                String icon = json.has("icon") ? json.get("icon").getAsString() : "";
                String dialogue = json.has("dialogue") ? json.get("dialogue").getAsString() : id;
                int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
                next.put(id, new RegionDefinition(id, dimension, type, center, radius, min, max, prompt, icon, dialogue, priority));
            } catch (Exception exception) {
                LOGGER.warn("无法加载剧情区域 {}：{}", entry.getKey(), exception.getMessage());
            }
        }
        definitions.clear();
        definitions.putAll(next);
        activeRegions.clear();
        LOGGER.info("已加载 {} 个剧情区域", definitions.size());
    }

    private static double[] requiredArray(JsonObject json, String key, boolean required) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            if (required) throw new IllegalArgumentException(key + " 必须是包含 3 个数字的数组");
            return new double[3];
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != 3) throw new IllegalArgumentException(key + " 必须包含 3 个数字");
        double[] result = new double[3];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsDouble();
        return result;
    }

    private static String fallbackId(Identifier identifier, String directory) {
        String path = identifier.getPath();
        String prefix = directory + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length(), path.length() - ".json".length()) : path.replace(".json", "");
    }

    public Collection<RegionDefinition> all() { return definitions.values(); }
    public RegionDefinition get(String id) { return definitions.get(id); }
    public String active(UUID player) { return activeRegions.get(player); }
    public void setActive(UUID player, String id) { if (id == null) activeRegions.remove(player); else activeRegions.put(player, id); }
    public void clearTransientState() { activeRegions.clear(); }
    public void removePlayer(UUID player) { activeRegions.remove(player); }
}
