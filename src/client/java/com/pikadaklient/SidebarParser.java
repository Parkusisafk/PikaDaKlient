package com.pikadaklient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.pikadaklient.ScoreboardPrinter.extractInt;

public class SidebarParser {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Pattern NUMBER_PATTERN = Pattern.compile("([\\d,]+)");

    public static class SidebarData {
        public int rebirth = 0;
        public int ascension = 0;
        public int prestige = 0;
        public int coins = 0;

        @Override
        public String toString() {
            return String.format("Rebirth=%d, Ascension=%d, Prestige=%d, Coins=%d",
                    rebirth, ascension, prestige, coins);
        }
    }
    private static String getUnformattedText(Text text) {
        // This attempts to get the raw string and strip all Minecraft formatting codes.
        // The exact utility method may vary by mod loader/version.
        // A common way is using the raw content and then stripping codes.
        String raw = text.getString();

        // --- BEST PRACTICE FIX: Use the Text utility method to flatten components ---
        // This is the most critical part: converting all components to visible text.
        // In some mod loaders, Text.getString() is the unformatted string.
        // If that fails, we must manually strip codes.

        // Fallback approach if getString() is returning codes: manually strip them.
        // You can check your modding environment's Text utility class for a better method.
        return raw.replaceAll("§[0-9a-fk-or]", "").trim();
    }

    /**
     * Parse the sidebar scoreboard for numeric stats.
     * @return SidebarData with parsed values, or null if no sidebar.
     */

    // Define this method within your SidebarData class or a utility class
    private static double parseNumber(String numStr, String suffix) {
        double numericValue;
        try {
            numericValue = Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }

        // Convert suffix to uppercase for easier comparison
        String upperSuffix = suffix.toUpperCase();

        // Apply the scale multiplier based on the abbreviation
        if (upperSuffix.contains("M")) {
            numericValue *= 1_000_000.0;
        } else if (upperSuffix.contains("B")) {
            numericValue *= 1_000_000_000.0;
        } else if (upperSuffix.contains("T")) {
            numericValue *= 1_000_000_000_000.0;
        } else if (upperSuffix.contains("Q")) {
            // Q is typically Quadrillion (10^15) or Quintillion (10^18)
            // Since you saw 'Qt' (Quintillion), we'll assume the largest common one.
            numericValue *= 1_000_000_000_000_000_000.0; // 10^18 (Quintillion)
        }

        return numericValue;
    }


    public static SidebarData parse() {
        if (mc == null || mc.world == null || mc.player == null) return null;

        SidebarData data = new SidebarData();
        Map<String, Double> foundValues = new HashMap<>();
        List<String> combinedLinesToParse = new ArrayList<>(); // Now stores String (unformatted lines)

        net.minecraft.scoreboard.Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        // --- 1. Map ScoreHolders to their respective Teams' formatting ---
        if (objective != null) {
            Map<String, Team> memberToTeamMap = new HashMap<>();
            // Pre-populate map: key is the ScoreHolder name, value is the Team
            for (Team team : scoreboard.getTeams()) {
                for (String member : team.getPlayerList()) {
                    memberToTeamMap.put(member, team);
                }
            }

            // Get the score holders in the order they appear on the board (highest score first)
            // This is necessary because the ScoreHolder names (like §0§r) are meaningless for order
            Map<ScoreHolder, Integer> scores = new HashMap<>();
            for (ScoreHolder holder : scoreboard.getKnownScoreHolders()) {
                // Use reflection/obfuscated getter method_55397() if getScore returns the object
                // Otherwise, rely on your initial dump logic to get the score value:
                try {
                    Object scoreObj = scoreboard.getScore(holder, objective);
                    // WARNING: The following line might need adjustment based on your environment
                    int value = extractInt(scoreObj); // Use your existing helper to get the int value
                    scores.put(holder, value);
                } catch (Throwable ignored) {}
            }

            // Sort holders by score (descending) to get the visual order
            List<ScoreHolder> orderedHolders = scores.entrySet().stream()
                    .sorted(Map.Entry.<ScoreHolder, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // Reconstruct the full visible line
            for (ScoreHolder holder : orderedHolders) {
                String memberName = holder.getNameForScoreboard();
                Team team = memberToTeamMap.get(memberName);

                String line;
                if (team != null) {
                    // Combine Prefix + ScoreHolder Name (invisible spacer) + Suffix
                    String prefix = getUnformattedText(team.getPrefix());
                    String suffix = getUnformattedText(team.getSuffix());

                    // The actual ScoreHolder name is often the only thing in the middle,
                    // but since it's just a color code (like §c§r) it's ignored for content.
                    // We combine Prefix and Suffix for the final line text.
                    line = prefix + suffix;
                } else {
                    // Fallback: Use the score holder's displayed name
                    line = getUnformattedText(holder.getStyledDisplayName());
                }

                // Only add non-empty lines that aren't the title itself
                if (!line.trim().isEmpty() && !line.contains(getUnformattedText(objective.getDisplayName()))) {
                    combinedLinesToParse.add(line);
                    System.out.println("[SidebarParser] FOUND LINE: '" + line + "'");
                }
            }
        }

        // --- 2. Collect text from Tab List entries (for player prefixes/suffixes) ---
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getDisplayName() != null) {
                // Note: This often gives you the combined Prefix+PlayerName+Suffix if available
                combinedLinesToParse.add(getUnformattedText(entry.getDisplayName()));
            }
        }

        // --- 3. Parse all collected unformatted text lines (OLD LOGIC REMAINS) ---
        for (String line : combinedLinesToParse.stream().distinct().collect(Collectors.toList())) {
            if (line.isEmpty()) continue;

            System.out.println("[SidebarParser] DEBUG: Processing line: '" + line + "'");

            // The remaining parsing logic relies on keyword matching and regex extraction
            Matcher m = NUMBER_PATTERN.matcher(line);
            if (!m.find()) continue;

            // ... (Your existing number parsing logic here) ...
            String numStr = m.group(1).replace(",", "");
            String suffix = m.groupCount() >= 2 ? m.group(2) : "";

            double value;
            // Use more general check for stats that might contain non-integer values or large numbers
            if (line.contains("Tokens:") || line.contains("EXP:") || line.contains("Beacons:")) {
                value = parseNumber(numStr, suffix); // Assumes parseNumber handles 'Qt', 'M', etc.
            } else {
                try {
                    value = Double.parseDouble(numStr);
                } catch (NumberFormatException e) {
                    continue;
                }
            }

            // --- 4. Assign parsed values based on keywords ---
            if (line.contains("Rebirth:")) {
                data.rebirth = (int) value;
                foundValues.put("Rebirth", value);
            } else if (line.contains("Ascension:")) {
                data.ascension = (int) value;
                foundValues.put("Ascension", value);
            } else if (line.contains("Prestige:")) {
                data.prestige = (int) value;
                foundValues.put("Prestige", value);
            } else if (line.contains("Coins:")) {
                data.coins = (int) value;
                foundValues.put("Coins", value);
            }
        }

        // --- 5. Debug result ---
        // ... (Your existing debug logic here) ...
        if (!foundValues.isEmpty()) {
            System.out.println("[SidebarParser] SUCCESS: Parsed values: " + foundValues);
        } else {
            System.out.println("[SidebarParser] FAILURE: No sidebar stats found.");
        }

        return data;
    }
}
