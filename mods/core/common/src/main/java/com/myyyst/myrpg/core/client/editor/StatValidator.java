package com.myyyst.myrpg.core.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side validation of a working stat definition.
 *
 * <p>Advisory only: the server re-validates through the real codec in {@code OverlaySaver},
 * so this exists to give immediate feedback in the editor rather than to enforce anything.
 * It catches the mistakes a codec cannot - a default outside its own range, overlapping
 * stages, gaps in the stage timeline.</p>
 */
public final class StatValidator {

    /** ERROR blocks a sensible save; WARNING is a hint the author may deliberately ignore. */
    public enum Level { ERROR, WARNING }

    /**
     * One problem found.
     * @param page which editor tab to send the author to (GENERAL, VALUE, STAGES, ...)
     */
    public record Issue(Level level, String page, String message, String detail) {}

    /** Runs every check and returns the issues found, in page order. Never throws. */
    public static List<Issue> validate(String statId, JsonObject json) {
        List<Issue> issues = new ArrayList<>();

        // identity
        if (!statId.contains(":") || statId.contains(" ")
                || !statId.equals(statId.toLowerCase())) {
            issues.add(new Issue(Level.ERROR, "GENERAL",
                    "Invalid resource ID", statId));
        }

        // value
        double min = JsonEdit.getDouble(json, "value.min", 0);
        double max = JsonEdit.getDouble(json, "value.max", 100);
        double def = JsonEdit.getDouble(json, "value.default", 0);
        if (min > max) {
            issues.add(new Issue(Level.ERROR, "VALUE",
                    "Minimum is greater than maximum", trim(min) + " > " + trim(max)));
        }
        if (def < min || def > max) {
            issues.add(new Issue(Level.ERROR, "VALUE",
                    "Default is outside the range", trim(def) + " not in " + trim(min) + " - " + trim(max)));
        }

        // stages: overlaps (error), gaps (warning), bad ranges, duplicate ids
        if (json.has("stages")) {
            JsonArray stages = json.getAsJsonArray("stages");
            List<double[]> ranges = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < stages.size(); i++) {
                JsonObject stage = stages.get(i).getAsJsonObject();
                String id = JsonEdit.getString(stage, "id", "?");
                double sMin = JsonEdit.getDouble(stage, "min", 0);
                double sMax = JsonEdit.getDouble(stage, "max", 0);
                if (sMin > sMax) {
                    issues.add(new Issue(Level.ERROR, "STAGES",
                            "Stage '" + id + "' has min > max", trim(sMin) + " > " + trim(sMax)));
                }
                if (ids.contains(id)) {
                    issues.add(new Issue(Level.ERROR, "STAGES",
                            "Duplicate stage id '" + id + "'", ""));
                }
                ids.add(id);
                ranges.add(new double[]{sMin, sMax});
            }
            // Sorting by lower bound turns overlap/gap detection into a single linear pass:
            // each range only has to be compared with the one before it.
            ranges.sort((a, b) -> Double.compare(a[0], b[0]));
            for (int i = 1; i < ranges.size(); i++) {
                if (ranges.get(i)[0] <= ranges.get(i - 1)[1]) {
                    issues.add(new Issue(Level.ERROR, "STAGES",
                            "Stages overlap", "range " + trim(ranges.get(i)[0]) + " - " + trim(Math.min(ranges.get(i - 1)[1], ranges.get(i)[1]))));
                } else if (ranges.get(i)[0] > ranges.get(i - 1)[1] + 1) {
                    issues.add(new Issue(Level.WARNING, "STAGES",
                            "Gap in timeline", "no stage covers " + trim(ranges.get(i - 1)[1] + 1) + " - " + trim(ranges.get(i)[0] - 1)));
                }
            }
            // ...and one more check for the space between the stat's min and the first stage.
            if (!ranges.isEmpty() && ranges.get(0)[0] > min) {
                issues.add(new Issue(Level.WARNING, "STAGES",
                        "Gap in timeline", "no stage covers " + trim(min) + " - " + trim(ranges.get(0)[0] - 1)));
            }
        }

        // rules: trigger presence + unknown types
        if (json.has("rules")) {
            JsonArray rules = json.getAsJsonArray("rules");
            for (int i = 0; i < rules.size(); i++) {
                JsonObject rule = rules.get(i).getAsJsonObject();
                if (!rule.has("trigger")) {
                    issues.add(new Issue(Level.ERROR, "RULES",
                            "Rule " + (i + 1) + " has no trigger", ""));
                }
                checkTypes(issues, rule, "conditions", ConditionSchemas.all().keySet(), i);
                checkTypes(issues, rule, "actions", ActionSchemas.all().keySet(), i);
            }
        }

        // hud
        String visibility = JsonEdit.getString(json, "hud.visibility", "always");
        if ((visibility.equals("above_value") || visibility.equals("below_value"))
                && !json.has("hud")) {
            // unreachable shape, but keep the pattern for when visibility_value matters:
        }
        if ((visibility.equals("above_value") || visibility.equals("below_value"))
                && JsonEdit.getDouble(json, "hud.visibility_value", Double.NaN) != JsonEdit.getDouble(json, "hud.visibility_value", Double.NaN)) {
            issues.add(new Issue(Level.WARNING, "DISPLAY",
                    "Visibility needs a value", "set visibility_value for " + visibility));
        }

        return issues;
    }

    /**
     * Warns about condition/action types the editor has no schema for.
     *
     * <p>Only a warning, never an error: a type registered by another mod - or written by
     * hand - is perfectly valid at runtime, it simply cannot be edited through the form UI,
     * and the editor preserves it untouched.</p>
     */
    private static void checkTypes(List<Issue> issues, JsonObject rule, String key,
                                   java.util.Set<String> known, int ruleIndex) {
        if (!rule.has(key)) return;
        JsonArray array = rule.getAsJsonArray(key);
        for (int i = 0; i < array.size(); i++) {
            JsonObject object = array.get(i).getAsJsonObject();
            String type = object.has("type") ? object.get("type").getAsString() : "(none)";
            if (!known.contains(type)) {
                issues.add(new Issue(Level.WARNING, "RULES",
                        "Unknown type in rule " + (ruleIndex + 1), type + " — kept as-is"));
            }
        }
    }

    /** Drops the ".0" from whole numbers in messages. */
    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** Static-only validator: never instantiated. */
    private StatValidator() {}
}