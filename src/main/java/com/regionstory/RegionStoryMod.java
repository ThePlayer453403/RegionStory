package com.regionstory;

import com.regionstory.data.DialogueManager;
import com.regionstory.data.RegionManager;
import com.regionstory.data.RegionDefinition;
import com.regionstory.data.DialogueDefinition;
import com.regionstory.data.DialogueSession;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.util.Identifier;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public final class RegionStoryMod implements ModInitializer {
    public static final String MOD_ID = "regionstory";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int MAX_ENTRY_JSON_BYTES = 24 * 1024;
    public static final Identifier OPEN_DIALOGUE = Identifier.of(MOD_ID, "open_dialogue");
    public static final Identifier REGION_HINT = Identifier.of(MOD_ID, "region_hint");
    public static final Identifier START_DIALOGUE = Identifier.of(MOD_ID, "start_dialogue");
    public static final Identifier ADVANCE_DIALOGUE = Identifier.of(MOD_ID, "advance_dialogue");
    public static final Identifier SELECT_OPTION = Identifier.of(MOD_ID, "select_option");
    public static final Identifier CLOSE_DIALOGUE = Identifier.of(MOD_ID, "close_dialogue");
    public static final RegionManager REGIONS = new RegionManager();
    public static final DialogueManager DIALOGUES = new DialogueManager();
    public static final Map<UUID, DialogueSession> SESSIONS = new ConcurrentHashMap<>();
    private static int regionScanTicker;
    private static MinecraftServer runningServer;

    public record RegionHintPayload(String regionId, String prompt, String icon) implements CustomPayload {
        public static final Id<RegionHintPayload> ID = new Id<>(REGION_HINT);
        public static final PacketCodec<RegistryByteBuf, RegionHintPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, RegionHintPayload::regionId,
                PacketCodecs.STRING, RegionHintPayload::prompt,
                PacketCodecs.STRING, RegionHintPayload::icon,
                RegionHintPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record OpenDialoguePayload(String dialogueId, String json, String entryId) implements CustomPayload {
        public static final Id<OpenDialoguePayload> ID = new Id<>(OPEN_DIALOGUE);
        public static final PacketCodec<RegistryByteBuf, OpenDialoguePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, OpenDialoguePayload::dialogueId,
                PacketCodecs.STRING, OpenDialoguePayload::json,
                PacketCodecs.STRING, OpenDialoguePayload::entryId,
                OpenDialoguePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record StartDialoguePayload(String regionId) implements CustomPayload {
        public static final Id<StartDialoguePayload> ID = new Id<>(START_DIALOGUE);
        public static final PacketCodec<RegistryByteBuf, StartDialoguePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, StartDialoguePayload::regionId, StartDialoguePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AdvanceDialoguePayload(String dialogueId, String entryId) implements CustomPayload {
        public static final Id<AdvanceDialoguePayload> ID = new Id<>(ADVANCE_DIALOGUE);
        public static final PacketCodec<RegistryByteBuf, AdvanceDialoguePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, AdvanceDialoguePayload::dialogueId,
                PacketCodecs.STRING, AdvanceDialoguePayload::entryId,
                AdvanceDialoguePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SelectOptionPayload(String dialogueId, String entryId, int optionIndex) implements CustomPayload {
        public static final Id<SelectOptionPayload> ID = new Id<>(SELECT_OPTION);
        public static final PacketCodec<RegistryByteBuf, SelectOptionPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, SelectOptionPayload::dialogueId,
                PacketCodecs.STRING, SelectOptionPayload::entryId,
                PacketCodecs.INTEGER, SelectOptionPayload::optionIndex,
                SelectOptionPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CloseDialoguePayload(String dialogueId) implements CustomPayload {
        public static final Id<CloseDialoguePayload> ID = new Id<>(CLOSE_DIALOGUE);
        public static final PacketCodec<RegistryByteBuf, CloseDialoguePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, CloseDialoguePayload::dialogueId, CloseDialoguePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(RegionHintPayload.ID, RegionHintPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenDialoguePayload.ID, OpenDialoguePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseDialoguePayload.ID, CloseDialoguePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StartDialoguePayload.ID, StartDialoguePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdvanceDialoguePayload.ID, AdvanceDialoguePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectOptionPayload.ID, SelectOptionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CloseDialoguePayload.ID, CloseDialoguePayload.CODEC);
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new DataReloadListener());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++regionScanTicker % 5 != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                updateRegionState(player);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("regionstory").requires(source -> source.getPermissions() instanceof LeveledPermissionPredicate leveled
                        && leveled.getLevel().isAtLeast(PermissionLevel.fromLevel(2)))
                        .then(literal("reload").executes(context -> {
                            context.getSource().getServer().reloadResources(context.getSource().getServer().getDataPackManager().getEnabledIds());
                            return 1;
                        }))));
        ServerPlayNetworking.registerGlobalReceiver(StartDialoguePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (SESSIONS.containsKey(player.getUuid())) return;
                var region = REGIONS.get(payload.regionId());
                if (region != null && region.contains(player.getEntityWorld().getRegistryKey().getValue(), player.getX(), player.getY(), player.getZ())) {
                    var dialogue = DIALOGUES.get(region.dialogue);
                    if (dialogue == null || dialogue.entry(dialogue.start) == null) return;
                    DialogueSession session = new DialogueSession(region.id, dialogue.id, dialogue.start);
                    SESSIONS.put(player.getUuid(), session);
                    enterEntry(player, dialogue, session);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(AdvanceDialoguePayload.ID, (payload, context) ->
                context.server().execute(() -> advanceDialogue(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SelectOptionPayload.ID, (payload, context) ->
                context.server().execute(() -> chooseOption(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(CloseDialoguePayload.ID, (payload, context) ->
                context.server().execute(() -> closeDialogue(context.player(), payload.dialogueId())));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            runningServer = server;
            REGIONS.clearTransientState();
            SESSIONS.clear();
            regionScanTicker = 0;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            runningServer = null;
            SESSIONS.clear();
            REGIONS.clearTransientState();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.player.getUuid();
            SESSIONS.remove(playerId);
            REGIONS.removePlayer(playerId);
        });
    }

    @Override public void onInitialize() { init(); }

    private static void updateRegionState(ServerPlayerEntity player) {
        String previous = REGIONS.active(player.getUuid());
        String current = null;
        for (var region : REGIONS.all().stream().sorted((a, b) -> Integer.compare(b.priority, a.priority)).toList()) {
            if (region.contains(player.getEntityWorld().getRegistryKey().getValue(), player.getX(), player.getY(), player.getZ())) {
                current = region.id;
                break;
            }
        }
        if (Objects.equals(previous, current)) return;
        if (SESSIONS.containsKey(player.getUuid())) {
            closeDialogue(player, SESSIONS.get(player.getUuid()).dialogueId);
        }
        REGIONS.setActive(player.getUuid(), current);
        if (current == null) {
            ServerPlayNetworking.send(player, new RegionHintPayload("", "", ""));
        } else {
            var region = REGIONS.get(current);
            ServerPlayNetworking.send(player, new RegionHintPayload(region.id, region.prompt, region.icon));
        }
    }

    private static void advanceDialogue(ServerPlayerEntity player, AdvanceDialoguePayload payload) {
        DialogueSession session = SESSIONS.get(player.getUuid());
        if (session == null || !session.dialogueId.equals(payload.dialogueId()) || !session.entryId.equals(payload.entryId())) return;
        if (!sessionStillInRegion(player, session)) return;
        DialogueDefinition dialogue = DIALOGUES.get(session.dialogueId);
        DialogueDefinition.Entry entry = dialogue == null ? null : dialogue.entry(session.entryId);
        if (entry == null || !entry.options().isEmpty()) return;
        if (entry.endDialog() || entry.next().isBlank()) closeDialogue(player, session.dialogueId);
        else { session.entryId = entry.next(); enterEntry(player, dialogue, session); }
    }

    private static void chooseOption(ServerPlayerEntity player, SelectOptionPayload payload) {
        DialogueSession session = SESSIONS.get(player.getUuid());
        if (session == null || !session.dialogueId.equals(payload.dialogueId()) || !session.entryId.equals(payload.entryId())) return;
        if (!sessionStillInRegion(player, session)) return;
        DialogueDefinition dialogue = DIALOGUES.get(session.dialogueId);
        DialogueDefinition.Entry entry = dialogue == null ? null : dialogue.entry(session.entryId);
        if (entry == null || payload.optionIndex() < 0 || payload.optionIndex() >= entry.options().size()) return;
        DialogueDefinition.Option option = entry.options().get(payload.optionIndex());
        executeCommands(player, option.commands());
        if (option.endDialog() || option.next().isBlank()) closeDialogue(player, session.dialogueId);
        else { session.entryId = option.next(); enterEntry(player, dialogue, session); }
    }

    private static void enterEntry(ServerPlayerEntity player, DialogueDefinition dialogue, DialogueSession session) {
        DialogueDefinition.Entry entry = dialogue.entry(session.entryId);
        if (entry == null) { closeDialogue(player, session.dialogueId); return; }
        executeCommands(player, entry.commands());
        String json = DIALOGUES.serializeEntry(session.dialogueId, session.entryId);
        if (json == null) {
            closeDialogue(player, session.dialogueId);
            return;
        }
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_ENTRY_JSON_BYTES) {
            LOGGER.warn("对话 {} 的条目 {} 超过网络包大小限制，已终止本次对话", session.dialogueId, session.entryId);
            closeDialogue(player, session.dialogueId);
            return;
        }
        ServerPlayNetworking.send(player, new OpenDialoguePayload(session.dialogueId, json, session.entryId));
    }

    private static void executeCommands(ServerPlayerEntity player, java.util.List<String> commands) {
        for (String command : commands) {
            if (command == null || command.isBlank()) continue;
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            try {
                player.getCommandSource().getServer().getCommandManager().parseAndExecute(player.getCommandSource().withSilent(), normalized);
            } catch (Exception exception) {
                LOGGER.warn("玩家 {} 执行剧情命令失败：{}", player.getName().getString(), normalized, exception);
            }
        }
    }

    private static void closeDialogue(ServerPlayerEntity player, String dialogueId) {
        DialogueSession session = SESSIONS.get(player.getUuid());
        if (session == null || !session.dialogueId.equals(dialogueId)) return;
        SESSIONS.remove(player.getUuid());
        ServerPlayNetworking.send(player, new CloseDialoguePayload(dialogueId));
    }

    private static boolean sessionStillInRegion(ServerPlayerEntity player, DialogueSession session) {
        RegionDefinition region = REGIONS.get(session.regionId);
        if (region != null && region.contains(player.getEntityWorld().getRegistryKey().getValue(),
                player.getX(), player.getY(), player.getZ())) {
            return true;
        }
        closeDialogue(player, session.dialogueId);
        return false;
    }

    public RegionStoryMod() {}

    /** 数据包重载会使旧条目失效，先通知客户端关闭界面，再清理服务端会话。 */
    public static void resetSessionsAfterReload() {
        if (runningServer != null) {
            for (ServerPlayerEntity player : runningServer.getPlayerManager().getPlayerList()) {
                DialogueSession session = SESSIONS.get(player.getUuid());
                if (session != null) ServerPlayNetworking.send(player, new CloseDialoguePayload(session.dialogueId));
            }
        }
        SESSIONS.clear();
    }
}
