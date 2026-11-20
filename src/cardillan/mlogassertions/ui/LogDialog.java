package cardillan.mlogassertions.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Log.LogLevel;
import cardillan.mlogassertions.MlogDevTools;
import cardillan.mlogassertions.auxiliary.AuxiliaryStore;
import cardillan.mlogassertions.data.LogMessages;
import cardillan.mlogassertions.data.LogMessages.LogMessage;
import cardillan.mlogassertions.data.LogMessages.LoggingData;
import cardillan.mlogassertions.data.LogMessages.MessageQueue;
import cardillan.mlogassertions.data.SnapshotManager;
import cardillan.mlogassertions.data.VariableValues;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.FileChooser;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static cardillan.mlogassertions.MlogDevTools.s;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

public class LogDialog extends BaseDialog {
    public static final int imageSize = 20;

    // Display settings - persistent for the duration of the game session
    private static boolean descending = true;
    private static boolean connected = false;
    private static final Set<LogLevel> enabledLevels = EnumSet.noneOf(LogLevel.class);
    private static final Set<String> enabledCategories = new HashSet<>();

    private final LogicBuild build;
    private final Table messageList = new Table();

    private final Seq<LoggingData> activeLogs = new Seq<>();
    private final Set<String> allCategories = new TreeSet<>();
    private final MessageQueue messages = new MessageQueue();

    // The dialog is in one of three possible modes:
    // 1. Live (live = true): the game is not paused, and the logs are updated on the fly, according to the active
    //    filters. Changing the filter setting causes full reload.
    // 2. Paused (live = false, can pause the game): the game is paused, the logs are loaded just once. Changing
    //    the filter setting causes full reload.
    // 3. Suspended (file = false, cannot pause the game): the game runs, but the logs aren't updated. Instead, the
    //    logs have been loaded just once (on opening the dialog, or on suspending updates), and filters are applied
    //    to the static snapshot of logs.
    private boolean live = false;

    private long maxFrameId = 0;
    private float frameIdWidth = 65f;
    private float scrollPos;

    // Resize detection
    private int lastWidth;

    public static void show(LogicBuild build) {
        new LogDialog(build).show();
    }

    private LogDialog(LogicBuild build) {
        super(Core.bundle.get("mlogdevtools.log.title"));

        this.build = build;
        this.shouldPause = true;

        addCloseButton();
        shown(this::rebuild);

        onResize(() -> {
            if (lastWidth != Core.graphics.getWidth() / 16) {
                rebuildMessages();
            }
        });

        update(this::updateMessages);

        reload(true);
    }

    private long nextCheck = 0;

    private void updateMessages() {
        if (!live || System.currentTimeMillis() < nextCheck) return;

        int categories = allCategories.size();
        if (loadMessages(true)) {
            nextCheck = System.currentTimeMillis() + 25;
            if (categories == allCategories.size()) {
                rebuildMessages();
            } else {
                rebuild();
            }
        }
    }

    private void reload(boolean force) {
        if (!force && !live && !MlogDevTools.canPauseGame()) {
            // Soft-reload of a suspended log: just rebuild messages
            rebuildMessages();
            return;
        }

        activeLogs.clear();

        activeLogs.add(AuxiliaryStore.create(build, AuxiliaryStore.loggingData));
        if (connected) {
            ObjectSet<LogicBuild> processors = new ObjectSet<>();
            processors.add(build);

            VariableValues content = SnapshotManager.liveView(build);
            content.eachObject(o -> {
                if (o instanceof LogicBuild b && processors.add(b)) {
                    AuxiliaryStore.process(b, AuxiliaryStore.loggingData, activeLogs::add);
                }
            });
        }

        allCategories.clear();
        for (LoggingData data : activeLogs) {
            data.messages.each(m -> allCategories.add(m.category));
        }

        loadMessages(false);
        rebuild();
    }

    private boolean matchesFilter(LogMessage message) {
        return (enabledLevels.isEmpty() || enabledLevels.contains(message.level)) &&
                (enabledCategories.isEmpty() || enabledCategories.contains(message.category));
    }

    private boolean loadMessages(boolean incremental) {
        boolean suspended = !live && !MlogDevTools.canPauseGame();
        int limit = live ? 100 : 5000;
        if (!incremental) {
            messages.clear();
            maxFrameId = 0;
        }

        MessageSource[] sources = new MessageSource[activeLogs.size];
        boolean hasAny = false;
        for (int i = 0; i < sources.length; i++) {
            sources[i] = incremental ? new IncrementalMessageSource(activeLogs.get(i)) : new FullMessageSource(activeLogs.get(i));
            hasAny |= sources[i].hasNext();
        }
        if (!hasAny) return false;

        while (true) {
            long maxId = Long.MIN_VALUE;
            int maxIndex = -1;

            for (int i = 0; i < sources.length; i++) {
                MessageSource source = sources[i];

                while (source.hasNext()) {
                    LogMessage message = source.peek();
                    allCategories.add(message.category);

                    if (suspended || matchesFilter(message)) {
                        if (message.messageId > maxId) {
                            maxId = message.messageId;
                            maxIndex = i;
                        }
                        break;
                    }
                    source.next();
                }
            }

            if (maxIndex == -1) break;

            LogMessage message = sources[maxIndex].next();
            maxFrameId = Math.max(maxFrameId, message.frameId);
            if (incremental) {
                messages.add(message);
                if (messages.size > limit) messages.removeFirst();
            } else {
                messages.addFirst(message);
                if (messages.size >= limit) break;
            }
        }

        return true;
    }

    private void rebuild() {
        enabledCategories.retainAll(allCategories);

        cont.clear();

        Table filters = new Table();
        filters.left();

        // Sorting option
        filters.button(Icon2.sortDesc, Styles.clearTogglei, () -> {
            descending = !descending;
            rebuildMessages();
        }).size(40).padRight(10f).checked(descending).tooltip(Core.bundle.get("mlogdevtools.log.newestfirst"));

        filters.button(Icon.sitemap, Styles.clearTogglei, () -> {
            connected = !connected;
            reload(true);
        }).size(40).padRight(10f).checked(connected).tooltip(Core.bundle.get("mlogdevtools.log.showconnected"));

        filters.button(live ? Icon.pause : Icon.play, Styles.cleari, () -> {
            live = !live;
            if (MlogDevTools.canPauseGame()) {
                state.set(live ? GameState.State.playing : GameState.State.paused);
            }
            reload(true);
        }).size(40).padRight(20f).tooltip(Core.bundle.get(live ? "mlogdevtools.log.pause" : "mlogdevtools.log.resume"));

        // Level filters
        LogLevel[] levels = new LogLevel[]{LogLevel.err, LogLevel.warn, LogLevel.info, LogLevel.debug};
        for (LogLevel level : levels) {
            filters.button(button -> button.image(icon(level).tint(color(level))).size(imageSize),
                    Styles.clearTogglei, () -> {
                        toggle(enabledLevels, level);
                        reload(false);
                    }).size(40).checked(enabledLevels.contains(level)).tooltip(level.name().toLowerCase());
        }

        filters.add().width(10);
        filters.defaults().height(42f);

        // Category filters
        for (String category : allCategories) {
            filters.button(category.isEmpty() ? Core.bundle.get("mlogdevtools.log.empty") : category, Styles.togglet, () -> {
                toggle(enabledCategories, category);
                reload(false);
            }).pad(2).checked(enabledCategories.contains(category));
        }

        cont.add(filters).center().row();

        ScrollPane scroll = new ScrollPane(messageList);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        cont.add(scroll).grow().update(s -> scrollPos = s.getScrollY()).get().setScrollYForce(scrollPos);

        rebuildMessages();

        buttons.clear();
        buttons.button("@back", Icon.left, this::hide).name(s("back"));
        buttons.button("@edit", Icon.edit, this::editCommands).name(s("edit"));
    }

    private static <T> void toggle(Set<T> set, T value) {
        if (!set.remove(value)) {
            set.add(value);
        }
    }

    private void rebuildMessages() {
        String str = String.valueOf(maxFrameId);
        frameIdWidth = Math.max(frameIdWidth, str.length() * 14f);

        title.setText(live ? Core.bundle.get("mlogdevtools.log.titlelive") : Core.bundle.format("mlogdevtools.log.titlepaused", messages.size));
        lastWidth = Core.graphics.getWidth() / 16;
        messageList.clear();
        messageList.top().left();

        // When suspended, filters need to be applied here
        boolean suspended = !live && !MlogDevTools.canPauseGame();

        int count = 0;
        for (int i = 0; i < messages.size; i++) {
            LogMessage message = messages.get(descending ? messages.size - i - 1 :  i);
            if (!suspended || matchesFilter(message)) {
                addMessage(messageList, message);
                count++;
            }
        }

        if (count == 0) {
            String text = LogMessages.maxMessages == 0 ? Core.bundle.get("mlogdevtools.log.disabled") :
                    messages.size == 0 ? Core.bundle.get("mlogdevtools.log.nomessages") :
                            Core.bundle.get("mlogdevtools.log.nomatchingfilter");
            messageList.center();
            messageList.add(text).center();
        }
    }

    private void addMessage(Table table, LogMessage message) {
        table.table(row -> {
            row.left().defaults().pad(3, 7, 3, 7);

            // Processor
            if (connected) {
                row.add(pos(message.build.x, message.build.y))
                        .color(Color.gray)
                        .minWidth(130)
                        .center()
                        .get().setAlignment(Align.center);
            }

            // Level icon
            row.image(icon(message.level).tint(color(message.level))).size(imageSize);

            // Frame ID — right-aligned, width based on content
            row.add(Long.toString(message.frameId))
                    .color(Color.lightGray)
                    .minWidth(frameIdWidth)
                    .right()
                    .get().setAlignment(Align.right);

            // Category — centered, width based on content
            if (!message.category.isEmpty()) {
                row.add(message.category)
                        .color(Color.gray)
                        .minWidth(100)
                        .center()
                        .get().setAlignment(Align.center);
            }

            // Message — takes the remaining width
            Label label = new Label(message.message);
            label.setWrap(true);
            label.setAlignment(Align.left);

            row.add(label).growX().left();
        }).growX().row();

        table.image().color(Color.darkGray)
                .height(1)
                .growX()
                .pad(2)
                .row();
    }

    private static TextureRegionDrawable icon(LogLevel level) {
        return switch (level) {
            case err -> Icon.cancel;
            case warn -> Icon.warning;
            case info -> Icon.infoCircle;
            default -> Icon.settings;
        };
    }

    private static Color color(Log.LogLevel level) {
        return switch (level) {
            case err -> Color.salmon;
            case warn -> Color.gold;
            case info -> Color.forest;
            default -> Color.lightGray;
        };
    }

    private String createText() {
        StringBuilder sb = new StringBuilder(30 * messages.size);
        if (connected) sb.append(s("Processor\t"));
        sb.append(s("Level\tFrame\tCategory\tMessage\n"));
        for (LogMessage message : messages) {
            if (connected) sb.append(pos(message.build.x, message.build.y)).append('\t');
            sb.append(message.level.name()).append('\t');
            sb.append(message.frameId).append('\t');
            sb.append(message.category == null ? "" : message.category).append('\t');
            sb.append(message.message).append('\n');
        }
        return sb.toString();
    }

    private void editCommands() {
        BaseDialog dialog = new BaseDialog("@edit");
        dialog.cont.pane(p -> {
            p.margin(10f);
            p.table(Tex.button, t -> {
                TextButton.TextButtonStyle style = Styles.flatt;
                t.defaults().size(360f, 60f).left();

                t.button(Core.bundle.get("mlogdevtools.log.clearmessages"), Icon.cancel, style, () -> {
                    LogMessages.clear(build);
                    reload(true);
                    dialog.hide();
                }).marginLeft(12f).row();

                t.button("@copy.clipboard", Icon.copy, style, () -> {
                    Core.app.setClipboardText(createText());
                    dialog.hide();
                }).marginLeft(12f).row();

                t.button(Core.bundle.get("mlogdevtools.log.exporttofile"), Icon.upload, style, () -> {
                    FileChooser.save(s("txt")).name("log_messages.txt").submit(file -> {
                        try {
                            file.writeString(createText());
                        } catch (Exception e) {
                            MlogDevTools.err("Error writing file: ", e);
                            ui.showErrorMessage(Core.bundle.format("mlogdevtools.writeerror", file.absolutePath()));
                        }
                    });
                    dialog.hide();
                }).marginLeft(12f).row();

                t.button("@back", Icon.left, style, dialog::hide).padTop(10f).marginLeft(12f).name(s("back"));
            });
        });

        dialog.addCloseListener();
        dialog.show();
    }

    public static String pos(float x, float y) {
        return String.format(s("(%.0f,\u00A0%.0f)"), x / Vars.tilesize, y / Vars.tilesize);
    }

    private interface MessageSource {
        boolean hasNext();
        LogMessage peek();
        LogMessage next();
    }

    // Traverses messages backwards from the newest one
    private static class FullMessageSource implements MessageSource {
        MessageQueue messages;
        int size;
        int position;

        public FullMessageSource(LoggingData data) {
            this.messages = data.messages;
            this.size = data.messages.size;
            this.position = data.messages.size - 1;
            data.additions = 0;
        }

        public boolean hasNext() {
            return size > 0;
        }

        public LogMessage peek() {
            return messages.get(position);
        }

        public LogMessage next() {
            size--;
            return messages.get(position--);
        }
    }

    // Traverses messages forwards from the oldest unseen one
    private static class IncrementalMessageSource implements MessageSource {
        MessageQueue messages;
        int size;
        int position;

        public IncrementalMessageSource(LoggingData data) {
            this.messages = data.messages;
            this.position = Math.max(0, data.messages.size - data.additions);
            this.size = data.messages.size - this.position;
            data.additions = 0;
        }

        public boolean hasNext() {
            return size > 0;
        }

        public LogMessage peek() {
            return messages.get(position);
        }

        public LogMessage next() {
            size--;
            return messages.get(position++);
        }
    }
}
