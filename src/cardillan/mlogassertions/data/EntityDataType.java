package cardillan.mlogassertions.data;

import arc.Core;
import org.jetbrains.annotations.NonNls;

public enum EntityDataType {
    processor   (10000f, "variables"),
    memory      (550f,   "mlogdevtools.vars.memory"),
    entity      (750f,   "mlogdevtools.vars.properties"),
    ;

    public final float maxColWidth;
    public final String name;

    EntityDataType(float maxColWidth, @NonNls String name) {
        this.maxColWidth = maxColWidth;
        this.name = Core.bundle.get(name);
    }
}
