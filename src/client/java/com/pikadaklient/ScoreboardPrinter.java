package com.pikadaklient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Deep scoreboard inspector for debugging purposes.
 * Dumps *everything* it can find about objectives, score holders, slots, and values.
 *
 * Compatible with 1.21.8 mappings where:
 * - getAllPlayerScores() no longer exists
 * - ScoreHolder has getDisplayName(), getStyledDisplayName(), getNameForScoreboard()
 * - Scoreboard.getScore(holder, objective) returns a ScoreAccess-like object
 */
public class ScoreboardPrinter {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void dumpEverything() {
        System.out.println("====================== [SCOREBOARD DUMP] ======================");

        if (mc == null) {
            System.out.println("MinecraftClient is null!");
            return;
        }
        if (mc.world == null) {
            System.out.println("World is null (probably not in a game).");
            return;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) {
            System.out.println("Scoreboard is null.");
            return;
        }

        dumpTeams(scoreboard);
        // --- Dump all objectives ---
        Collection<ScoreboardObjective> objectives = scoreboard.getObjectives();
        System.out.println("Found " + objectives.size() + " objectives:");
        for (ScoreboardObjective obj : objectives) {
            dumpObjective(scoreboard, obj);
        }

        // --- Dump display slots ---
        System.out.println("\nDisplay slots:");
        for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
            ScoreboardObjective obj = scoreboard.getObjectiveForSlot(slot);
            if (obj != null) {
                System.out.println("  Slot " + slot + " -> " + safeText(obj.getDisplayName()) + " [" + obj.getName() + "]");
            } else {
                System.out.println("  Slot " + slot + " -> (empty)");
            }
        }

        // --- Dump all known score holders ---
        Collection<ScoreHolder> holders = scoreboard.getKnownScoreHolders();
        System.out.println("\nKnown score holders: " + holders.size());
        for (ScoreHolder holder : holders) {
            dumpScoreHolder(scoreboard, holder, objectives);
        }

        System.out.println("====================== [END SCOREBOARD DUMP] ======================");
    }


    public static void dumpTeams(Scoreboard scoreboard) {
        System.out.println("\n====================== [TEAMS DUMP] ======================");
        Collection<Team> teams = scoreboard.getTeams();
        System.out.println("Found " + teams.size() + " teams:");

        for (Team team : teams) {
            String prefix = safeText(team.getPrefix());
            String suffix = safeText(team.getSuffix());
            String color = team.getColor().getName();

            // Get the members of the team. For a sidebar, this is often the invisible ScoreHolder.
            Collection<String> members = team.getPlayerList();

            System.out.println("\n--- Team: " + team.getName() + " ---");
            System.out.println("  Display Name: " + safeText(team.getDisplayName()));
            System.out.println("  Color: " + color);
            System.out.println("  Prefix: " + (prefix.isEmpty() ? "(empty)" : prefix));
            System.out.println("  Suffix: " + (suffix.isEmpty() ? "(empty)" : suffix));

            if (!members.isEmpty()) {
                System.out.println("  Members (" + members.size() + "):");
                for (String member : members) {
                    System.out.println("    -> " + member);
                }
            }
        }
        System.out.println("====================== [END TEAMS DUMP] ======================");
    }


    private static void dumpObjective(Scoreboard scoreboard, ScoreboardObjective obj) {
        System.out.println("\n=== Objective ===");
        System.out.println("Name: " + obj.getName());
        System.out.println("Display Name: " + safeText(obj.getDisplayName()));
        System.out.println("Criterion: " + safeToString(obj.getCriterion()));
        System.out.println("Render Type: " + safeToString(obj.getRenderType()));
        System.out.println("Number Format: " + (obj.getNumberFormat() != null ? obj.getNumberFormat() : "(none)"));

        // Dump every holder that has a score in this objective
        Collection<ScoreHolder> holders = scoreboard.getKnownScoreHolders();
        for (ScoreHolder holder : holders) {
            try {
                Object scoreObj = scoreboard.getScore(holder, obj);
                if (scoreObj == null) continue;

                int value = extractInt(scoreObj);
                if (value != Integer.MIN_VALUE) {
                    System.out.println("  " + holder.getNameForScoreboard() + " -> " + value);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void dumpScoreHolder(Scoreboard scoreboard, ScoreHolder holder, Collection<ScoreboardObjective> objectives) {
        System.out.println("\n--- ScoreHolder ---");
        System.out.println("Class: " + holder.getClass().getName());
        System.out.println("NameForScoreboard: " + safeCall(() -> holder.getNameForScoreboard()));
        System.out.println("DisplayName: " + safeText(holder.getDisplayName()));
        System.out.println("StyledDisplayName: " + safeText(holder.getStyledDisplayName()));

        // Dump all declared methods for ScoreHolder (for curiosity)
        Method[] methods = holder.getClass().getDeclaredMethods();
        System.out.println("Declared methods (" + methods.length + "):");
        for (Method m : methods) {
            System.out.println("  " + m.toString());
        }

        // Print scores across all objectives
        for (ScoreboardObjective obj : objectives) {
            try {
                Object scoreObj = scoreboard.getScore(holder, obj);
                if (scoreObj == null) continue;
                int value = extractInt(scoreObj);
                System.out.println("  [" + obj.getName() + "] " + safeText(obj.getDisplayName()) + " -> " + value);

                // Reflection dump of score object structure
                dumpReflection("ScoreObject for " + holder.getNameForScoreboard() + "@" + obj.getName(), scoreObj);
            } catch (Throwable t) {
                System.out.println("  [" + obj.getName() + "] (error fetching score: " + t + ")");
            }
        }
    }

    static int extractInt(Object scoreObj) {
        if (scoreObj == null) return Integer.MIN_VALUE;
        Class<?> c = scoreObj.getClass();

        // 1. Try known obfuscated or mapped methods
        List<String> methodCandidates = List.of(
                "method_55397", // common obf getter
                "getScore", "getValue", "getScoreValue",
                "method_55399", "method_55398"
        );
        for (String name : methodCandidates) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                Object result = m.invoke(scoreObj);
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            } catch (Throwable ignored) {}
        }

        // 2. Try public methods in superclass or interfaces
        for (Method m : c.getMethods()) {
            if (m.getReturnType() == int.class || m.getReturnType() == Integer.class) {
                try {
                    m.setAccessible(true);
                    Object result = m.invoke(scoreObj);
                    if (result instanceof Number) return ((Number) result).intValue();
                } catch (Throwable ignored) {}
            }
        }

        // 3. Try fields (some builds store score directly in a private int)
        for (Field f : c.getDeclaredFields()) {
            if (f.getType() == int.class || f.getType() == Integer.class) {
                try {
                    f.setAccessible(true);
                    return f.getInt(scoreObj);
                } catch (Throwable ignored) {}
            }
        }

        System.out.println("  [extractInt] could not extract from " + c.getName());
        return Integer.MIN_VALUE;
    }


    private static void dumpReflection(String header, Object obj) {
        if (obj == null) return;
        System.out.println("  --- Reflection dump of " + header + " ---");
        Class<?> clazz = obj.getClass();
        System.out.println("  Class: " + clazz.getName());

        for (Method m : clazz.getDeclaredMethods()) {
            try {
                m.setAccessible(true);
                Object val = null;
                if (m.getParameterCount() == 0) {
                    try {
                        val = m.invoke(obj);
                    } catch (Throwable ignored) {}
                }
                System.out.println("    " + m.getName() + "() -> " + (val != null ? val : "(no result)"));
            } catch (Throwable ignored) {}
        }
    }

    private static String safeText(Text t) {
        return t == null ? "(null)" : t.getString();
    }

    private static String safeToString(Object o) {
        return o == null ? "(null)" : o.toString();
    }

    private static String safeCall(SupplierLike<String> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return "(error: " + t + ")";
        }
    }

    @FunctionalInterface
    private interface SupplierLike<T> {
        T get() throws Exception;
    }
}
