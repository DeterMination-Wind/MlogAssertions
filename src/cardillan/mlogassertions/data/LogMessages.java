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
                    data -> data.add(build, Core.graphics.getFrameId(), level, category, message));
        }
    }

    public static void addSystemMessage(LogicBuild build, String message) {
        if (maxMessages > 0 && Core.settings.getBool(Constants.generateSystemLogs, true)) {
            AuxiliaryStore.store(build, AuxiliaryStore.loggingData,
                    data -> data.add(build, Core.graphics.getFrameId(), Log.LogLevel.info, String.valueOf((char)59407), message));
        }
    }

    public static class LogMessage {
        // Used to order messages generated in different processors
        private static long counter = 0;

        public final LogicBuild build;
        public final long messageId;
        public final long frameId;
        public final Log.LogLevel level;
        public final String category;
        public final String message;

        LogMessage(LogicBuild build, long frameId, Log.LogLevel level, String category, String message) {
            this.build = build;
            this.messageId = counter++;
            this.frameId = frameId;
            this.level = level;
            this.category = category;
            this.message = message;
        }
    }

    public static class LoggingData implements AuxiliaryData {
        public final MessageQueue messages = new MessageQueue();
        public int additions = 0;

        public LoggingData(Senseable object) {
        }

        @Override
        public AuxiliaryData reset(Senseable object) {
            if (Core.settings.getBool(Constants.clearLogOnRestart, true)) {
                messages.clear();
            }
            return this;
        }

        public void add(LogicBuild build, long frameId, Log.LogLevel level, String category, String message) {
            messages.add(new LogMessage(build, frameId, level, category, message));
            if (messages.size > maxMessages) {
                messages.removeFirst();
            }
            additions++;
        }

        void adjustSize(int maxMessages) {
            if (messages.size > maxMessages) {
                while (messages.size > maxMessages) messages.removeFirst();
                messages.shrink();
            }
        }
    }

    public static class MessageQueue extends Queue<LogMessage> {
        public MessageQueue() {
            super(20, LogMessage.class);
        }

        public MessageQueue(MessageQueue queue) {
            super(0);

            LogMessage[] values = queue.values;
            int head = queue.head();
            int tail = queue.tail();
            LogMessage[] newArray = new LogMessage[queue.size];
            if (head < tail) {
                System.arraycopy(values, head, newArray, 0, tail - head);
            } else if (queue.size > 0) {
                int rest = values.length - head;
                System.arraycopy(values, head, newArray, 0, rest);
                System.arraycopy(values, 0, newArray, rest, tail);
            }

            this.values = newArray;
            this.head = 0;
            this.tail = newArray.length;
            this.size = newArray.length;
        }

        int head() {
            return head;
        }

        int tail() {
            return tail;
        }
    }
}
