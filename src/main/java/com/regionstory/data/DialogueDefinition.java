package com.regionstory.data;

import java.util.List;

public final class DialogueDefinition {
    public final String id;
    public final String start;
    public final List<Entry> entries;
    public DialogueDefinition(String id, String start, List<Entry> entries) { this.id = id; this.start = start; this.entries = entries; }
    public Entry entry(String entryId) { return entries.stream().filter(e -> e.id.equals(entryId)).findFirst().orElse(null); }
    /** 对话选项支持图标、命令和显式结束标记。 */
    public record Option(String text, String next, List<String> commands,
                         String icon, boolean endDialog) {}
    public record Entry(String id, String speaker, String speakerTitle, String text,
                         List<Option> options, String next, List<String> commands, boolean endDialog) {}
}
