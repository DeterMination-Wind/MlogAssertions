package cardillan.mlogassertions.data;

import arc.Core;
import arc.struct.Queue;
import arc.util.Log;
import arc.util.Nullable;
import cardillan.mlogassertions.Constants;
import cardillan.mlogassertions.auxiliary.AuxiliaryData;
import cardillan.mlogassertions.auxiliary.AuxiliaryStore;
import mindustry.logic.Senseable;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

public class LogMessages {
    public static int maxMessages = 100;
    public static int maxLevel = 0;

    public static void updateLimit() {
        if (maxMessages == 0) {
            AuxiliaryStore.clear(AuxiliaryStore.loggingData);
        } else {
            AuxiliaryStore.each(AuxiliaryStore.loggingData, (o, r) -> r.adjustSize(maxMessages));
        }
    }

    public static void clear(LogicBuild build) {
        AuxiliaryStore.remove(build, AuxiliaryStore.loggingData);
    }

    public static void addMessage(LogicBuild build, Log.LogLevel level, @Nullable String category, String message) {
        if (maxMessages > 0 && level.ordinal() >= maxLevel) {
            AuxiliaryStore.store(build, AuxiliaryStore.loggingData,
                    data -> data.add(Core.graphics.getFrameId(), level, category, message));
        }
    }

    public static void addSystemMessage(LogicBuild build, String message) {
        if (maxMessages > 0 && Core.settings.getBool(Constants.generateSystemLogs, true)) {
            AuxiliaryStore.store(build, AuxiliaryStore.loggingData,
                    data -> data.add(Core.graphics.getFrameId(), Log.LogLevel.info, "" + (char)59407, message));
        }
    }

    public static LogMessage[] getMessages(LogicBuild build) {
        LoggingData data = AuxiliaryStore.get(build, AuxiliaryStore.loggingData);
        return data == null ? new LogMessage[0] : data.messages.toArray(LogMessage.class);
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

    public static class LoggingData implements AuxiliaryData {
        final Queue<LogMessage> messages = new Queue<>();

        public LoggingData(Senseable object) {
        }

        @Override
        public AuxiliaryData reset(Senseable object) {
            if (Core.settings.getBool(Constants.clearLogOnRestart, true)) {
                messages.clear();
            }
            return this;
        }
        public void add(long frameId, Log.LogLevel level, String category, String message) {
            messages.add(new LogMessage(frameId, level, category, message));
            if (messages.size > maxMessages) {
                messages.removeFirst();
            }
        }

        void adjustSize(int maxMessages) {
            if (messages.size > maxMessages) {
                while (messages.size > maxMessages) messages.removeFirst();
                messages.shrink();
            }
        }
    }
}
