package com.skps2010;

import java.util.ArrayList;
import java.util.List;

public class FDConfig {
    public float cravingMultiplier = 2.0f;
    public String cravingDisplay = "cf.craving";
    public long cravingChangeInterval = 24000L;
    public int recordingFoodCount = 32;
    public int cravingMaxCount = 8;
    /** Item ids that may never be chosen as a craving, e.g. "minecraft:cake" (namespace optional). */
    public List<String> cravingBlacklist = new ArrayList<>();
    /** Extra item ids to force into the craving pool even if not auto-detected as food (namespace optional). */
    public List<String> cravingWhitelist = new ArrayList<>();
    public int hudX = 379;
    public int hudY = 380;
    public float hudScale = 1.0f;
    public boolean hudEnabled = true;
    // Second HUD module, drawn on top of the inventory / creative screens
    public int invHudX = 344;
    public int invHudY = 104;
    public float invHudScale = 1.0f;
    public boolean invHudEnabled = true;
    public List<Rule> rules = List.of(
            new Rule(0, 2.0f, "cf.rule.never"),
            new Rule(7, 1.4f, "cf.rule.rare"),
            new Rule(-1, 0.8f, "cf.rule.often")
    );

    public static class Rule {
        public int maxCount;
        public float multiplier;
        public String display;

        public Rule(int m, float k, String d) {
            maxCount = m;
            multiplier = k;
            display = d;
        }
    }
}
