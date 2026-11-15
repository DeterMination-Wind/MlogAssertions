package cardillan.mlogassertions.data;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Queue;
import arc.util.Log;
import arc.util.Nullable;
import cardillan.mlogassertions.Constants;
import mindustry.game.EventType;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

public class LogMessages {
    public static int maxMessages = 100;
    private static final ObjectMap<LogicBuild, LoggingData> loggingData = new ObjectMap<>();

    public static void init() {
        Events.on(EventType.ResetEvent.class, e -> {
            loggingData.clear();
        });

        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            if (e.breaking && (e.tile.build instanceof LogicBuild build)) {
                loggingData.remove(build);
            }
        });
    }

    public static void setLimit(int maxMessages) {
        LogMessages.maxMessages = maxMessages;

        if (maxMessages == 0) {
            loggingData.clear();
        } else {
            loggingData.each((b, q) -> {while (q.messages.size > maxMessages) q.messages.removeFirst();});
        }
    }

    private static Queue<LogMessage> getMessages(LogicBuild build, boolean create) {
        LoggingData data = loggingData.get(build);
        if (data == null || data.instructions != build.executor.instructions && Core.settings.getBool(Constants.clearLogOnRestart, true)) {
            if (!create) {
                loggingData.remove(build);
                return null;
            } else {
                data = new LoggingData(new Queue<>(), build.executor.instructions);
                loggingData.put(build, data);
            }
        }
        return data.messages;
    }

    public static void clear(LogicBuild build) {
        Queue<LogMessage> messages = getMessages(build, false);
        if (messages != null) messages.clear();
    }

    public static void addMessage(LogicBuild build, Log.LogLevel level, @Nullable String category, String message) {
        if (maxMessages > 0) {
            Queue<LogMessage> messages = getMessages(build, true);
            messages.add(new LogMessage(Core.graphics.getFrameId(), level, category, message));
            if (messages.size > maxMessages) messages.removeFirst();
        }
    }

    public static LogMessage[] getMessages(LogicBuild build) {
        Queue<LogMessage> messages = getMessages(build, false);
        return messages == null ? new LogMessage[0] : messages.toArray(LogMessage.class);
    }

    public static class LogMessage {
        public final long frameId;
        public final Log.LogLevel level;
        public final String category;
        public final String message;

        LogMessage(long frameId, Log.LogLevel level, String category, String message) {
            this.frameId = frameId;
            this.level = level;
            this.category = category;
            this.message = message;
        }
    }

    private static class LoggingData {
        final Queue<LogMessage> messages;
        final Object instructions;

        LoggingData(Queue<LogMessage> messages, Object instructions) {
            this.messages = messages;
            this.instructions = instructions;
        }
    }
}
