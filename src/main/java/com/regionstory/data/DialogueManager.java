package com.regionstory.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Server/data-pack dialogue definitions. */
public final class DialogueManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("RegionStory/Dialogue");
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_OPTIONS_PER_ENTRY = 32;
    private static final int MAX_COMMANDS_PER_NODE = 32;
    private static final int MAX_TEXT_CHARS = 32_768;
    private final Map<String, DialogueDefinition> definitions = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public void reload(ResourceManager manager) {
        Map<String, DialogueDefinition> next = new HashMap<>();
        for (Map.Entry<Identifier, Resource> resource : manager
                .findResources("dialogues", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var reader = new InputStreamReader(resource.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                DialogueDefinition parsed = parse(JsonParser.parseReader(reader).getAsJsonObject(),
                        fallbackId(resource.getKey(), "dialogues"), true);
                if (next.putIfAbsent(parsed.id, parsed) != null) {
                    throw new IllegalArgumentException("duplicate dialogue id: " + parsed.id);
                }
            } catch (Exception exception) {
                LOGGER.warn("Unable to load dialogue {}: {}", resource.getKey(), exception.getMessage());
            }
        }
        definitions.clear();
        definitions.putAll(next);
        LOGGER.info("Loaded {} dialogue definitions", definitions.size());
    }

    /** Sends one entry at a time so a large dialogue does not become one network packet. */
    public String serializeEntry(String dialogueId, String entryId) {
        DialogueDefinition dialogue = definitions.get(dialogueId);
        DialogueDefinition.Entry entry = dialogue == null ? null : dialogue.entry(entryId);
        return entry == null ? null : gson.toJson(
                new DialogueDefinition(dialogue.id, entryId, List.of(entry)));
    }

    private static DialogueDefinition parse(JsonObject root, String fallbackId,
                                            boolean strictReferences) {
        String id = string(root, "id", fallbackId);
        String start = string(root, "start", "start");
        if (id.isBlank()) throw new IllegalArgumentException("dialogue id is empty");
        if (!root.has("entries") || !root.get("entries").isJsonArray()) {
            throw new IllegalArgumentException("entries must be an array");
        }

        List<DialogueDefinition.Entry> entries = new ArrayList<>();
        if (root.getAsJsonArray("entries").size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many dialogue entries");
        }
        for (JsonElement element : root.getAsJsonArray("entries")) {
            JsonObject object = element.getAsJsonObject();
            List<DialogueDefinition.Option> options = new ArrayList<>();
            if (object.has("options")) {
                if (!object.get("options").isJsonArray()
                        || object.getAsJsonArray("options").size() > MAX_OPTIONS_PER_ENTRY) {
                    throw new IllegalArgumentException("options must be an array with at most "
                            + MAX_OPTIONS_PER_ENTRY + " items");
                }
                for (JsonElement optionElement : object.getAsJsonArray("options")) {
                    JsonObject option = optionElement.getAsJsonObject();
                    options.add(new DialogueDefinition.Option(
                            string(option, "text", ""),
                            string(option, "next", ""),
                            strings(option, "commands"),
                            string(option, "icon", "dialogue"),
                            option.has("endDialog") && option.get("endDialog").getAsBoolean()));
                }
            }
            entries.add(new DialogueDefinition.Entry(
                    string(object, "id", ""),
                    string(object, "speaker", ""),
                    string(object, "speakerTitle", ""),
                    string(object, "text", ""),
                    List.copyOf(options),
                    string(object, "next", ""),
                    strings(object, "commands"),
                    object.has("endDialog") && object.get("endDialog").getAsBoolean()));
        }
        validate(id, start, entries, strictReferences);
        return new DialogueDefinition(id, start, List.copyOf(entries));
    }

    public DialogueDefinition loadSerialized(String json) {
        try {
            DialogueDefinition parsed = parse(JsonParser.parseString(json).getAsJsonObject(),
                    "synced", false);
            definitions.put(parsed.id, parsed);
            return parsed;
        } catch (Exception exception) {
            LOGGER.warn("Unable to read synchronized dialogue: {}", exception.getMessage());
            return null;
        }
    }

    public String serialize(String id) {
        DialogueDefinition definition = definitions.get(id);
        return definition == null ? null : gson.toJson(definition);
    }

    private static void validate(String dialogueId, String start,
                                 List<DialogueDefinition.Entry> entries,
                                 boolean strictReferences) {
        Set<String> ids = new HashSet<>();
        for (DialogueDefinition.Entry entry : entries) {
            if (entry.id().isBlank()) {
                throw new IllegalArgumentException("dialogue " + dialogueId + " has an empty entry id");
            }
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("duplicate entry id: " + entry.id());
            }
        }
        if (!ids.contains(start)) {
            throw new IllegalArgumentException("start entry does not exist: " + start);
        }
        if (strictReferences) {
            for (DialogueDefinition.Entry entry : entries) {
                validateNext(dialogueId, entry.id(), entry.next(), ids);
                for (DialogueDefinition.Option option : entry.options()) {
                    validateNext(dialogueId, entry.id(), option.next(), ids);
                }
            }
        }
    }

    private static void validateNext(String dialogueId, String entryId,
                                     String next, Set<String> ids) {
        if (!next.isBlank() && !ids.contains(next)) {
            throw new IllegalArgumentException("entry " + entryId
                    + " points to missing entry " + next + " in " + dialogueId);
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        String value = object.has(key) ? object.get(key).getAsString() : fallback;
        if (value.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException(key + " is too long");
        }
        return value;
    }

    private static List<String> strings(JsonObject object, String key) {
        List<String> result = new ArrayList<>();
        if (object.has(key)) {
            if (!object.get(key).isJsonArray()
                    || object.getAsJsonArray(key).size() > MAX_COMMANDS_PER_NODE) {
                throw new IllegalArgumentException(key + " must be an array with at most "
                        + MAX_COMMANDS_PER_NODE + " items");
            }
            for (JsonElement element : object.getAsJsonArray(key)) {
                String value = element.getAsString();
                if (value.length() > MAX_TEXT_CHARS) {
                    throw new IllegalArgumentException(key + " item is too long");
                }
                result.add(value);
            }
        }
        return result;
    }

    private static String fallbackId(Identifier identifier, String directory) {
        String path = identifier.getPath();
        String prefix = directory + "/";
        return path.startsWith(prefix)
                ? path.substring(prefix.length(), path.length() - ".json".length())
                : path.replace(".json", "");
    }

    public DialogueDefinition get(String id) {
        return definitions.get(id);
    }

    public Collection<DialogueDefinition> all() {
        return definitions.values();
    }

    public void clear() {
        definitions.clear();
    }
}
