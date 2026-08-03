package com.regionstory.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side region definitions and transient player state. */
public final class RegionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("RegionStory/Region");
    private final Map<String, RegionDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeRegions = new ConcurrentHashMap<>();

    public void reload(ResourceManager manager) {
        Map<String, RegionDefinition> next = new HashMap<>();
        for (Map.Entry<Identifier, Resource> resource : manager
                .findResources("regions", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var reader = new InputStreamReader(resource.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String id = string(json, "id", fallbackId(resource.getKey(), "regions"));
                if (id.isBlank()) throw new IllegalArgumentException("region id is empty");

                Identifier dimension = Identifier.of(string(json, "dimension", "minecraft:overworld"));
                RegionDefinition.Type type = RegionDefinition.Type.valueOf(
                        string(json, "type", "sphere").toUpperCase(Locale.ROOT));
                double[] center = requiredArray(json, "center", type == RegionDefinition.Type.SPHERE);
                double radius = json.has("radius") ? json.get("radius").getAsDouble() : 0.0D;
                double[] min = requiredArray(json, "min", type == RegionDefinition.Type.BOX);
                double[] max = requiredArray(json, "max", type == RegionDefinition.Type.BOX);
                if (type == RegionDefinition.Type.SPHERE
                        && (!Double.isFinite(radius) || radius <= 0.0D)) {
                    throw new IllegalArgumentException("sphere radius must be greater than zero");
                }
                if (type == RegionDefinition.Type.BOX
                        && (min[0] > max[0] || min[1] > max[1] || min[2] > max[2])) {
                    throw new IllegalArgumentException("box min must not exceed max");
                }

                RegionDefinition parsed = new RegionDefinition(
                        id, dimension, type, center, radius, min, max,
                        string(json, "prompt", "按 F 对话"),
                        string(json, "icon", ""),
                        string(json, "dialogue", id),
                        json.has("priority") ? json.get("priority").getAsInt() : 0);
                if (next.putIfAbsent(id, parsed) != null) {
                    throw new IllegalArgumentException("duplicate region id: " + id);
                }
            } catch (Exception exception) {
                LOGGER.warn("Unable to load region {}: {}", resource.getKey(), exception.getMessage());
            }
        }
        definitions.clear();
        definitions.putAll(next);
        activeRegions.clear();
        LOGGER.info("Loaded {} story regions", definitions.size());
    }

    /** 移除没有对应对话定义的区域，避免玩家进入区域后只看到一个无效提示。 */
    public void validateDialogueReferences(DialogueManager dialogues) {
        definitions.entrySet().removeIf(entry -> {
            RegionDefinition region = entry.getValue();
            if (dialogues.get(region.dialogue) != null) return false;
            LOGGER.warn("Ignoring region {} because dialogue {} does not exist",
                    region.id, region.dialogue);
            return true;
        });
        activeRegions.entrySet().removeIf(entry -> !definitions.containsKey(entry.getValue()));
    }

    private static double[] requiredArray(JsonObject json, String key, boolean required) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            if (required) throw new IllegalArgumentException(key + " must be an array of three numbers");
            return new double[3];
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != 3) throw new IllegalArgumentException(key + " must contain three numbers");
        double[] result = new double[3];
        for (int i = 0; i < result.length; i++) {
            result[i] = array.get(i).getAsDouble();
            if (!Double.isFinite(result[i])) {
                throw new IllegalArgumentException(key + " contains a non-finite coordinate");
            }
        }
        return result;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static String fallbackId(Identifier identifier, String directory) {
        String path = identifier.getPath();
        String prefix = directory + "/";
        return path.startsWith(prefix)
                ? path.substring(prefix.length(), path.length() - ".json".length())
                : path.replace(".json", "");
    }

    public Collection<RegionDefinition> all() {
        return definitions.values();
    }

    public RegionDefinition get(String id) {
        return definitions.get(id);
    }

    public String active(UUID player) {
        return activeRegions.get(player);
    }

    public void setActive(UUID player, String id) {
        if (id == null) activeRegions.remove(player);
        else activeRegions.put(player, id);
    }

    public void clearTransientState() {
        activeRegions.clear();
    }

    public void removePlayer(UUID player) {
        activeRegions.remove(player);
    }
}
