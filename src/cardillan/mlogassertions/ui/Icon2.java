package cardillan.mlogassertions.ui;

import arc.Core;
import arc.scene.style.TextureRegionDrawable;
import cardillan.mlogassertions.Constants;
import org.jetbrains.annotations.NonNls;

public class Icon2 {
    public static final TextureRegionDrawable branching = load("branching");
    public static final TextureRegionDrawable log = load("log");
    public static final TextureRegionDrawable percent = load("percent");
    public static final TextureRegionDrawable sortAsc = load("sort-asc");
    public static final TextureRegionDrawable sortDesc = load("sort-desc");
    public static final TextureRegionDrawable sum = load("sum");
    public static final TextureRegionDrawable time = load("time");

    private static TextureRegionDrawable load(@NonNls String name) {
        return new TextureRegionDrawable(Core.atlas.find(Constants.modName + "-" + name));
    }
}
