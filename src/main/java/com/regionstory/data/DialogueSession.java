package com.regionstory.data;

/** 服务端权威保存的玩家对话状态。 */
public final class DialogueSession {
    public final String regionId;
    public final String dialogueId;
    public String entryId;

    public DialogueSession(String regionId, String dialogueId, String entryId) {
        this.regionId = regionId;
        this.dialogueId = dialogueId;
        this.entryId = entryId;
    }
}
