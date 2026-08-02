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

public final class DialogueManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("RegionStory/Dialogue");
    private final Map<String, DialogueDefinition> definitions = new ConcurrentHashMap<>();

    public void reload(ResourceManager manager) {
        Map<String, DialogueDefinition> next = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("dialogues", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                DialogueDefinition parsed = parse(JsonParser.parseReader(reader).getAsJsonObject(), fallbackId(entry.getKey(), "dialogues"), true);
                next.put(parsed.id, parsed);
            } catch (Exception exception) {
                LOGGER.warn("无法加载对话定义 {}：{}", entry.getKey(), exception.getMessage());
            }
        }
        definitions.clear();
        definitions.putAll(next);
        LOGGER.info("已加载 {} 个区域对话定义", definitions.size());
    }

    /** 只同步当前条目，避免把整份大型对话 JSON 塞入单个网络包。 */
    public String serializeEntry(String dialogueId, String entryId) {
        DialogueDefinition dialogue = definitions.get(dialogueId);
        DialogueDefinition.Entry entry = dialogue == null ? null : dialogue.entry(entryId);
        return entry == null ? null : new Gson().toJson(new DialogueDefinition(dialogue.id, entryId, List.of(entry)));
    }

    private static DialogueDefinition parse(JsonObject root, String fallbackId, boolean strictReferences) {
        String id = root.has("id") ? root.get("id").getAsString() : fallbackId;
        String start = root.has("start") ? root.get("start").getAsString() : "start";
        if (id.isBlank()) throw new IllegalArgumentException("id 不能为空");
        if (!root.has("entries") || !root.get("entries").isJsonArray()) {
            throw new IllegalArgumentException("entries 必须是数组");
        }

        List<DialogueDefinition.Entry> entries = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("entries")) {
            JsonObject o = e.getAsJsonObject();
            List<DialogueDefinition.Option> options = new ArrayList<>();
            if (o.has("options")) {
                for (JsonElement oe : o.getAsJsonArray("options")) {
                    JsonObject oo = oe.getAsJsonObject();
                    options.add(new DialogueDefinition.Option(
                            str(oo, "text", ""),
                            str(oo, "next", ""),
                            strings(oo, "commands"),
                            str(oo, "icon", "dialogue")));
                }
            }
            entries.add(new DialogueDefinition.Entry(str(o, "id", ""), str(o, "speaker", ""),
                    str(o, "speakerTitle", ""), str(o, "text", ""), options,
                    str(o, "next", ""), strings(o, "commands"), o.has("endDialog") && o.get("endDialog").getAsBoolean()));
        }
        validate(id, start, entries, strictReferences);
        return new DialogueDefinition(id, start, List.copyOf(entries));
    }

    public DialogueDefinition loadSerialized(String json) {
        try {
            DialogueDefinition parsed = parse(JsonParser.parseString(json).getAsJsonObject(), "synced", false);
            definitions.put(parsed.id, parsed);
            return parsed;
        } catch (Exception exception) {
            LOGGER.warn("无法读取服务器同步的对话数据：{}", exception.getMessage());
            return null;
        }
    }

    public String serialize(String id) {
        DialogueDefinition definition = definitions.get(id);
        return definition == null ? null : new Gson().toJson(definition);
    }

    private static void validate(String dialogueId, String start, List<DialogueDefinition.Entry> entries, boolean strictReferences) {
        Set<String> ids = new HashSet<>();
        for (DialogueDefinition.Entry entry : entries) {
            if (entry.id().isBlank()) throw new IllegalArgumentException("对话 " + dialogueId + " 存在空条目 id");
            if (!ids.add(entry.id())) throw new IllegalArgumentException("对话 " + dialogueId + " 存在重复条目 id: " + entry.id());
        }
        if (!ids.contains(start)) throw new IllegalArgumentException("对话 " + dialogueId + " 的 start 不存在: " + start);
        if (strictReferences) {
            for (DialogueDefinition.Entry entry : entries) {
                validateNext(dialogueId, entry.id(), entry.next(), ids);
                for (DialogueDefinition.Option option : entry.options()) validateNext(dialogueId, entry.id(), option.next(), ids);
            }
        }
    }

    private static void validateNext(String dialogueId, String entryId, String next, Set<String> ids) {
        if (!next.isBlank() && !ids.contains(next)) {
            throw new IllegalArgumentException("对话 " + dialogueId + " 的 " + entryId + " 指向不存在的条目: " + next);
        }
    }

    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }

    private static String fallbackId(Identifier identifier, String directory) {
        String path = identifier.getPath();
        String prefix = directory + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length(), path.length() - ".json".length()) : path.replace(".json", "");
    }

    private static List<String> strings(JsonObject o, String k) {
        List<String> result = new ArrayList<>();
        if (o.has(k)) for (JsonElement e : o.getAsJsonArray(k)) result.add(e.getAsString());
        return result;
    }

    public DialogueDefinition get(String id) { return definitions.get(id); }
    public Collection<DialogueDefinition> all() { return definitions.values(); }
    public void clear() { definitions.clear(); }
}
