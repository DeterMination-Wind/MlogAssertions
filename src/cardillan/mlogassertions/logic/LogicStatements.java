package cardillan.mlogassertions.logic;

import arc.func.Cons;
import arc.func.Func;
import arc.func.Prov;
import arc.graphics.Color;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import cardillan.mlogassertions.MlogDevTools;
import mindustry.gen.Icon;
import mindustry.gen.LogicIO;
import mindustry.logic.*;
import mindustry.logic.LStatements.JumpStatement;
import mindustry.ui.Styles;
import org.jetbrains.annotations.NonNls;

import static cardillan.mlogassertions.MlogDevTools.s;

public class LogicStatements {
    public static final String NULL = "null";

    private static final LogicStatementWriter writer = new LogicStatementWriter();

    public static void register() {
        register(AssertStatement::new, AssertStatement.opcode, AssertStatement::read);
        register(AssertBoundsStatement::new, AssertBoundsStatement.opcode, AssertBoundsStatement::read);
        register(AssertEqualsStatement::new, AssertEqualsStatement.opcode, AssertEqualsStatement::read);
        register(AssertFlushStatement::new, AssertFlushStatement.opcode, AssertFlushStatement::read);
        register(AssertPrintsStatement::new, AssertPrintsStatement.opcode, AssertPrintsStatement::read);
        register(AssertTypeStatement::new, AssertTypeStatement.opcode, AssertTypeStatement::read);
        register(BreakpointStatement::new, BreakpointStatement.opcode, BreakpointStatement::read);
        register(ErrorStatement::new, ErrorStatement.opcode, ErrorStatement::read);
        register(LogStatement::new, LogStatement.opcode, LogStatement::read);
        register(ProfileStatement::new, ProfileStatement.opcode, ProfileStatement::read);
        register(RestartStatement::new, RestartStatement.opcode, RestartStatement::read);
        register(SnapshotStatement::new, SnapshotStatement.opcode, SnapshotStatement::read);
        register(SystemLogStatement::new, SystemLogStatement.opcode, SystemLogStatement::read);
    }

    private static void register(Prov<LStatement> prov, String opcode, Func<String[], LStatement> parser) {
        if (LAssembler.customParsers.containsKey(opcode)) {
            MlogDevTools.warn("Logic statement opcode already registered: " + opcode);
            return;
        }
        LogicIO.allStatements.add(prov);
        LAssembler.customParsers.put(opcode, parser);
    }

    private static final Log.LogLevel[] levels = {
            Log.LogLevel.err,
            Log.LogLevel.warn,
            Log.LogLevel.info,
            Log.LogLevel.debug,
    };

    public static abstract class AbstractAssertStatement extends LStatement {
        // This is the optional (expandable) message field. Child classes need to use it for optional messages.
        protected String message = NULL;
        boolean expanded = false;

        public static String bundle(@NonNls String key) {
            return LStatement.bundle(key);
        }

        public AbstractAssertStatement() {
        }

        @Override
        public LCategory category() {
            return AssertLogic.assertsCategory;
        }

        protected boolean expanded() {
            return !message.equals(NULL);
        }

        protected abstract void rebuild(Table t);

        @Override
        public final void build(Table table) {
            expanded = expanded();
            rebuild(table);
        }

        // Expandable message field
        protected void message(Table t, @NonNls String label, String defaultMessage) {
            if (!expanded) {
                t.button(b -> {
                    b.add(bundle("add " + label)).color(Color.gray);
                    b.clicked(() -> {
                        expanded = true;
                        message = s('"' + defaultMessage + '"');
                        rebuild(t);
                    });
                }, Styles.logict, () -> {
                }).size(180f, 40f).left().pad(4f).color(t.color);
            } else {
                t.add(bundle(label)).padLeft(10);
                // Convert various representations of empty messages to null
                field(t, message, str -> message = str == null || str.isEmpty() || str.equals("\"\"") || str.equals("\"") ? NULL : str)
                        .width(LCanvas.getTargetWidth() - Scl.scl(20f)).padRight(3);
                if (false) {
                    // A delete button - could be activated inadvertently
                    t.button(b -> {
                        b.image(Icon.trashSmall, t.color);
                        b.clicked(() -> {
                            message = NULL;
                            expanded = false;
                            rebuild(t);
                        });
                    }, Styles.logict, () -> {
                    }).size(40f).padLeft(-1).color(t.color);
                }
            }
        }

        // Select field with optional label
        protected <T extends Enum<?>> void select(Table table, @NonNls String label, T[] values, T current, Cons<T> getter, int cols, float width) {
            Table sub = new Table();
            sub.setColor(table.color);
            table.add(sub);

            if (!label.isEmpty()) sub.add(bundle(label)).padLeft(10);
            table.button(b -> {
                b.add(bundle(current));
                b.clicked(() -> showSelect(b, values, current, o -> { getter.get(o); rebuild(table); }, cols, c -> c.width(width)));
            }, Styles.logict, () -> {
            }).size(width, 40f).left().pad(4f).color(table.color);
        }
    }

    public abstract static class AbstractMessageStatement extends AbstractAssertStatement {
        private final String opcode;
        private final boolean hasLevel;
        private final boolean hasCategory;
        public Log.LogLevel level = Log.LogLevel.info;
        public String category = NULL;
        public String[] params = new String[10];

        public AbstractMessageStatement(@NonNls String opcode, @NonNls String message, boolean hasLevel, boolean hasCategory) {
            super();
            this.opcode = opcode;
            this.hasLevel = hasLevel;
            this.hasCategory = hasCategory;
            params[0] = s("\"" + message + " at #{@counter}.\"");
            for (int i = 1; i < params.length; i++) params[i] = NULL;
        }

        @Override
        protected boolean expanded() {
            for (int i = 1; i < params.length; i++) {
                if (!params[i].isEmpty() && !NULL.equals(params[i])) {
                    return true;
                }
            }
            return false;
        }

        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();

            if (hasLevel) {
                select(t, "level", levels, level, o -> level = o, 1, 80f);
            }

            if (hasCategory) {
                fields(t, s("category"), false, category, str -> category = str);
            }

            field(t, params[0], str -> params[0] = str).width(LCanvas.getTargetWidth() - Scl.scl(20f)).padRight(3);

            if (expanded) {
                for (int i = 1; i < params.length; i++) {
                    final int index = i;
                    fields(t, s("p" + i), false, params[index], v -> params[index] = v);
                }
            } else {
                t.button(b -> {
                    b.add(bundle("add parameters")).color(Color.gray);
                    b.clicked(() -> { expanded = true; rebuild(t); });
                }, Styles.logict, () -> {}).size(180f, 40f).left().pad(4f).color(t.color);
            }
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            if (hasLevel) writer.write(level.name());
            if (hasCategory) writer.write(category);
            for (String param : params) writer.write(param == null || param.isEmpty() ? NULL : param);
            writer.end();
        }

        protected LStatement readTokens(String[] tokens) {
            int i = 1;
            if (hasLevel && tokens.length > i) level = Log.LogLevel.valueOf(tokens[i++]);
            if (hasCategory && tokens.length > i) category = tokens[i++];
            for (int j = 0; j < 10; j++) {
                if (tokens.length > i) params[j] = tokens[i++];
            }
            return this;
        }
    }

    public static class AssertStatement extends AbstractAssertStatement {
        public static final String opcode = "assert";

        public ConditionOp op = ConditionOp.equal;
        public String value = s("x"), compare = s("false");

        public AssertStatement() {
            super();
        }

        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();

            JumpStatement.addOp(this, t, op, o -> {
                op = o;
                rebuild(t);
            }, value, str -> value = str, compare, str -> compare = str);

            message(t, "message", s("Assertion [gold]{1}[] {3} [gold]{2}[] failed."));
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.AssertI(op, builder.var(value), builder.var(compare), builder.var(message));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(op.name());
            writer.write(value);
            writer.write(compare);
            writer.write(message);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            AssertStatement stmt = new AssertStatement();
            int i = 1;
            if (tokens.length > i) stmt.op = ConditionOp.valueOf(tokens[i++]);
            if (tokens.length > i) stmt.value = tokens[i++];
            if (tokens.length > i) stmt.compare = tokens[i++];
            if (tokens.length > i) stmt.message = tokens[i++];
            return stmt;
        }
    }

    public static class AssertBoundsStatement extends AbstractAssertStatement {
        private static final ConditionOp[] ops = {ConditionOp.lessThan, ConditionOp.lessThanEq};
        public static final String opcode = "assertBounds";
        public AssertionType type = AssertionType.integer;
        public String multiple = "2";
        public String min = "0";
        public ConditionOp opMin = ConditionOp.lessThanEq;
        public String value = s("index");
        public ConditionOp opMax = ConditionOp.lessThanEq;
        public String max = "10";

        public AssertBoundsStatement() {
            super();
        }

        @Override
        protected void rebuild(Table table) {
            table.clearChildren();
            table.left();

            field(table, value, str -> value = str);
            select(table, "is", AssertionType.all, type, o -> type = o, 2, 110f);
            if (type == AssertionType.multiple) {
                fields(table, s("of"), false, multiple, str -> multiple = str);
            }
            fields(table, s("where"), false, min, str -> min = str);
            select(table, "", ops, opMin, o -> opMin = o, 2, 64f);
            table.add(bundle("value")).padLeft(10);
            select(table, "", ops, opMax, o -> opMax = o, 2, 64f);
            fields(table, max, str -> max = str);
            message(table, "message", s("Index out of bounds: {1}{4}{2}{5}{3}."));
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.AssertBoundsI(type, builder.var(multiple),
                    builder.var(min), opMin, builder.var(value), opMax, builder.var(max),
                    builder.var(message));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(type.name());
            writer.write(multiple);
            writer.write(min);
            writer.write(opMin.name());
            writer.write(value);
            writer.write(opMax.name());
            writer.write(max);
            writer.write(message);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            AssertBoundsStatement stmt = new AssertBoundsStatement();
            int i = 1;
            if (tokens.length > i) stmt.type = AssertionType.valueOf(tokens[i++]);
            if (tokens.length > i) stmt.multiple = tokens[i++];
            if (tokens.length > i) stmt.min = tokens[i++];
            if (tokens.length > i) stmt.opMin = conditionOp(tokens[i++]);
            if (tokens.length > i) stmt.value = tokens[i++];
            if (tokens.length > i) stmt.opMax = conditionOp(tokens[i++]);
            if (tokens.length > i) stmt.max = tokens[i++];
            if (tokens.length > i) stmt.message = tokens[i++];
            return stmt;
        }

        private static ConditionOp conditionOp(String name) {
            ConditionOp op = ConditionOp.valueOf(name);
            if (op != ConditionOp.lessThan && op != ConditionOp.lessThanEq) {
                throw new IllegalArgumentException(s("Unsupported condition op: " + name));
            }
            return op;
        }
    }

    public static class AssertEqualsStatement extends AbstractAssertStatement {
        public static final String opcode = "assertequals";
        public String expected = "0";
        public String actual = s("value");

        public AssertEqualsStatement() {
            super();
        }


        @Override
        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();
            fields(t, s("expected"), false, expected, v -> expected = v);
            fields(t, s("actual"), false, actual, v -> actual = v);
            message(t, "message", s("Assertion failed: expected [gold]{1}[], got [gold]{2}[]."));
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.AssertEqualsI(builder.var(expected),
                    builder.var(actual), builder.var(message));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(expected);
            writer.write(actual);
            writer.write(message);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            AssertEqualsStatement stmt = new AssertEqualsStatement();
            int i = 1;
            if (tokens.length > i) stmt.expected = tokens[i++];
            if (tokens.length > i) stmt.actual = tokens[i++];
            if (tokens.length > i) stmt.message = tokens[i++];
            return stmt;
        }
    }

    public static class AssertFlushStatement extends AbstractAssertStatement {
        public static final String opcode = "assertflush";
        public String position = s("position");

        public AssertFlushStatement() {
            super();
        }

        @Override
        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();
            fields(t, s("position"), false, position, v -> position = v);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.AssertFlushI(builder.var(position));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(position);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            AssertFlushStatement stmt = new AssertFlushStatement();
            int i = 1;
            if (tokens.length > i) stmt.position = tokens[i++];
            return stmt;
        }
    }

    public static class AssertPrintsStatement extends AbstractAssertStatement {
        public static final String opcode = "assertprints";
        public String position = s("position");
        public String expected = s("\"frog\"");

        public AssertPrintsStatement() {
            super();
        }

        @Override
        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();
            fields(t, s("position"), false, position, v -> position = v);
            fields(t, s("expected"), false, expected, v -> expected = v);
            message(t, "message", s("Assertion failed: expected [gold]{1}[], got [gold]{2}[]."));
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.AssertPrintsI(builder.var(position),
                    builder.var(expected), builder.var(message));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(position);
            writer.write(expected);
            writer.write(message);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            AssertPrintsStatement stmt = new AssertPrintsStatement();
            int i = 1;
            if (tokens.length > i) stmt.position = tokens[i++];
            if (tokens.length > i) stmt.expected = tokens[i++];
            if (tokens.length > i) stmt.message = tokens[i++];
            return stmt;
        }
    }

    public static class AssertTypeStatement extends AbstractAssertStatement {
        public static final String opcode = "asserttype";
        public AssertionDataType expectedType = AssertionDataType.unit;
        public String actualValue = s("@unit");

        public AssertTypeStatement() {
            super();
        }

        @Override
        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();
            fields(t, actualValue, v -> actualValue = v);
            select(t, "is", AssertionDataType.all, expectedType, o -> expectedType = o, 2, 160f);
            message(t, "message", s("Assertion failed: expected [gold]{1}[], got [gold]{2}[]."));
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.AssertTypeI(expectedType, builder.var(actualValue), builder.var(message));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(expectedType.name());
            writer.write(actualValue);
            writer.write(message);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            AssertTypeStatement stmt = new AssertTypeStatement();
            int i = 1;
            if (tokens.length > i) stmt.expectedType = AssertionDataType.valueOf(tokens[i++]);
            if (tokens.length > i) stmt.actualValue = tokens[i++];
            if (tokens.length > i) stmt.message = tokens[i++];
            return stmt;
        }
    }

    public static class BreakpointStatement extends AbstractAssertStatement {
        public static final String opcode = "breakpoint";

        public ConditionOp op = ConditionOp.always;
        public String value = s("x"), compare = s("false");

        public BreakpointStatement() {
            super();
        }

        protected void rebuild(Table table) {
            table.clearChildren();
            table.left();

            if (op != ConditionOp.always) table.add(bundle("when")).padLeft(10);
            JumpStatement.addOp(this, table, op, o -> {
                op = o;
                rebuild(table);
            }, value, str -> value = str, compare, str -> compare = str);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.BreakpointI(op, builder.var(value), builder.var(compare));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(op.name());
            writer.write(value);
            writer.write(compare);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            BreakpointStatement stmt = new BreakpointStatement();
            int i = 1;
            if (tokens.length > i) stmt.op = ConditionOp.valueOf(tokens[i++]);
            if (tokens.length > i) stmt.value = tokens[i++];
            if (tokens.length > i) stmt.compare = tokens[i++];
            return stmt;
        }
    }

    public static class ErrorStatement extends AbstractMessageStatement {
        public static final String opcode = "error";

        public ErrorStatement() {
            super(opcode, "Runtime error", false, false);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar[] vars = new LVar[10];
            for (int i = 0; i < params.length; i++) vars[i] = builder.var(params[i]);
            return new LogicInstructions.ErrorI(vars);
        }

        public static LStatement read(String[] tokens) {
            return new ErrorStatement().readTokens(tokens);
        }
    }

    public static class LogStatement extends AbstractMessageStatement {
        public static final String opcode = "log";

        public LogStatement() {
            super(opcode, "Logging a message", true, true);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar[] vars = new LVar[10];
            for (int i = 0; i < params.length; i++) vars[i] = builder.var(params[i]);
            return new LogicInstructions.LogI(level, builder.var(category), vars);
        }

        public static LStatement read(String[] tokens) {
            return new LogStatement().readTokens(tokens);
        }
    }

    public static class ProfileStatement extends AbstractAssertStatement {
        public static final String opcode = "profile";

        public ProfilingCommand command = ProfilingCommand.start;
        public String block = s("@this");

        public ProfileStatement() {
            super();
        }

        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();

            select(t, "", ProfilingCommand.all, command, o -> command = o, 1, 70f);
            fields(t, s("profiling of"), false, block, str -> block = str);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.ProfileI(command, builder.var(block));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(command.name());
            writer.write(block);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            ProfileStatement stmt = new ProfileStatement();
            int i = 1;
            if (tokens.length > i) stmt.command = ProfilingCommand.valueOf(tokens[i++]);
            if (tokens.length > i) stmt.block = tokens[i++];
            return stmt;
        }
    }

    public static class RestartStatement extends AbstractAssertStatement {
        public static final String opcode = "restart";

        public String block = s("@this");

        public RestartStatement() {
            super();
        }

        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();

            fields(t, block, str -> block = str);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.RestartI(builder.var(block));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(block);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            RestartStatement stmt = new RestartStatement();
            int i = 1;
            if (tokens.length > i) stmt.block = tokens[i++];
            return stmt;
        }
    }

    public static class SnapshotStatement extends AbstractAssertStatement {
        public static final String opcode = "snapshot";

        public SnapshotType type = SnapshotType.isolated;
        public String block = s("@this");
        public String steps = "20";

        public SnapshotStatement() {
            super();
        }

        protected void rebuild(Table t) {
            t.clearChildren();
            t.left();

            select(t, "create", SnapshotType.all, type, o -> type = o, 2, 130f);
            if (type == SnapshotType.recording) {
                fields(t, s("snapshot of the next"), false, steps, str -> steps = str).width(75f);
                fields(t, s("steps in"), false, block, str -> block = str);
            } else {
                if (type == SnapshotType.global) {
                    t.add(bundle("snapshot"));
                } else {
                    fields(t, s("snapshot of"), false, block, str -> block = str);
                }

                message(t, "name", s(bundle("Snapshot created at #{@counter}.")));
            }
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LogicInstructions.SnapshotI(type, builder.var(block), builder.var(steps), builder.var(message));
        }

        @Override
        public void write(StringBuilder builder) {
            writer.start(builder);
            writer.write(opcode);
            writer.write(type.name());
            writer.write(block);
            writer.write(steps);
            writer.write(message);
            writer.end();
        }

        public static LStatement read(String[] tokens) {
            SnapshotStatement stmt = new SnapshotStatement();
            int i = 1;
            if (tokens.length > i) stmt.type = SnapshotType.valueOf(tokens[i++]);
            if (tokens.length > i) stmt.block = tokens[i++];
            if (tokens.length > i) stmt.steps = tokens[i++];
            if (tokens.length > i) stmt.message = tokens[i++];
            return stmt;
        }
    }

    public static class SystemLogStatement extends AbstractMessageStatement {
        public static final String opcode = "systemlog";

        public SystemLogStatement() {
            super(opcode, "Logging a message", true, false);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar[] vars = new LVar[10];
            for (int i = 0; i < params.length; i++) vars[i] = builder.var(params[i]);
            return new LogicInstructions.SystemLogI(level, vars);
        }

        public static LStatement read(String[] tokens) {
            return new SystemLogStatement().readTokens(tokens);
        }
    }
}
