package cardillan.mlogassertions.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Queue;
import arc.util.Align;
import arc.util.Log;
import arc.util.Log.LogLevel;
import cardillan.mlogassertions.data.*;
import cardillan.mlogassertions.data.LogMessages.LogMessage;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.logic.LCanvas;
import mindustry.ui.FileChooser;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import java.util.*;

import static mindustry.Vars.ui;

public class LogDialog extends BaseDialog {
    public static final int imageSize = 20;
    private static boolean descending = true;

    private final LogicBuild build;
    private final Set<LogLevel> enabledLevels = EnumSet.allOf(LogLevel.class);
    private final Set<String> enabledCategories = new TreeSet<>();
    private final Table messageList = new Table();

    private LogMessage[] messages;
    private int lastWidth;

    public static void show(LogicBuild build) {
        new LogDialog(build, LogMessages.getMessages(build)).show();
    }

    private LogDialog(LogicBuild build, LogMessage[] messages) {
        super("Log: " + messages.length + " messages");

        this.build = build;
        this.messages = messages;
        this.shouldPause = true;

        enabledCategories.clear();
        for (LogMessage m : messages) enabledCategories.add(m.category);

        addCloseButton();
        shown(this::rebuild);

        onResize(() -> {
            if (lastWidth != Core.graphics.getWidth() / 16) {
                rebuildMessages();
            }
        });

        rebuild();
    }

    private void rebuild() {
        cont.clear();

        Table filters = new Table();
        filters.left();

        // Sorting option
        filters.button(Icon2.sortDesc, Styles.clearTogglei, () -> {
            descending = !descending;
            rebuildMessages();
        }).size(40).padRight(10f).checked(descending).tooltip("Newest first");


        // Level filters
        LogLevel[] levels = new LogLevel[] {LogLevel.err, LogLevel.warn, LogLevel.info, LogLevel.debug };
        for (LogLevel level : levels) {
            filters.button(button -> button.image(icon(level).tint(color(level))).size(imageSize),
                            Styles.clearTogglei, () -> {
                toggle(enabledLevels, level);
                rebuildMessages();
            }).size(40).checked(true).tooltip(level.name().toLowerCase());
        }

        filters.add().width(10);
        filters.defaults().height(42f);

        // Category filters
        for (String category : enabledCategories) {
            filters.button(category.isEmpty() ? "<empty>" : category, Styles.togglet, () -> {
                toggle(enabledCategories, category);
                rebuildMessages();
            }).pad(2).checked(true);
        }

        cont.add(filters).center().row();

        ScrollPane scroll = new ScrollPane(messageList);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        cont.add(scroll).grow();

        rebuildMessages();

        buttons.clear();
        buttons.button("@back", Icon.left, this::hide).name("back");
        buttons.button("@edit", Icon.edit, () -> editCommands()).name("edit");
    }

    private static <T> void toggle(Set<T> set, T value) {
        if (!set.remove(value)) {
            set.add(value);
        }
    }

    private void rebuildMessages() {
        title.setText("Log: " + messages.length + " messages");
        lastWidth = Core.graphics.getWidth() / 16;
        messageList.clear();
        messageList.top().left();

        int count = 0;
        for (int i = 0; i < messages.length; i++) {
            LogMessage message = messages[descending ? messages.length - i - 1 : i];
            if (enabledLevels.contains(message.level) && enabledCategories.contains(message.category)) {
                addMessage(messageList, message);
                count++;
            }
        }

        if (count == 0) {
            String text = LogMessages.maxMessages == 0 ? "Logging disabled in settings." :
                    messages.length == 0 ? "No messages logged in the current processor." :
                    "No messages matching the filter.";
            messageList.center();
            messageList.add(text).center();
        }
    }

    private void addMessage(Table table, LogMessage message) {
        table.table(row -> {
            row.left().defaults().pad(3, 7, 3, 7);

            // Level icon
            row.image(icon(message.level).tint(color(message.level))).size(imageSize);

            // Frame ID — right-aligned, width based on content
            row.add(Long.toString(message.frameId))
                    .color(Color.lightGray)
                    .minWidth(65)
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
        StringBuilder sb = new StringBuilder(30 * messages.length);
        sb.append("Level\tFrame\tCategory\tMessage\n");
        for (LogMessage message : messages) {
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

                t.button("Clear log messages", Icon.cancel, style, () -> {
                    LogMessages.clear(build);
                    messages = new LogMessage[0];
                    rebuildMessages();
                    dialog.hide();
                }).marginLeft(12f).row();

                t.button("@copy.clipboard", Icon.copy, style, () -> {
                    Core.app.setClipboardText(createText());
                    dialog.hide();
                }).marginLeft(12f).row();

                t.button("Export to a text file", Icon.upload, style, () -> {
                    FileChooser.save("txt").name("log_messages.txt").submit(file -> {
                        try {
                            file.writeString(createText());
                        } catch (Exception e) {
                            Log.err("[Mlog Dev Tools] Error writing file: ", e);
                            ui.showErrorMessage("Error writing file " + file.absolutePath());
                        }
                    });
                    dialog.hide();
                }).marginLeft(12f).row();

                t.button("@back", Icon.left, style, dialog::hide).padTop(10f).marginLeft(12f).name("back");
            });
        });

        dialog.addCloseListener();
        dialog.show();
    }
}
