package dev.joshlucem.nowquiz.quiz;

import java.util.ArrayList;
import java.util.List;

/**
 * Bundle of rewards that can be granted to a winner.
 */
public record RewardDefinition(
    Double money,
    Integer xp,
    List<RewardItem> items,
    List<String> legacyCommands,
    List<String> consoleCommands,
    List<String> playerCommands
) {

    public RewardDefinition {
        items = List.copyOf(items);
        legacyCommands = List.copyOf(legacyCommands);
        consoleCommands = List.copyOf(consoleCommands);
        playerCommands = List.copyOf(playerCommands);
    }

    public static RewardDefinition empty() {
        return new RewardDefinition(null, null, List.of(), List.of(), List.of(), List.of());
    }

    public RewardDefinition merge(RewardDefinition override) {
        if (override == null) {
            return this;
        }

        List<RewardItem> mergedItems = new ArrayList<>(this.items);
        if (!override.items.isEmpty()) {
            mergedItems = new ArrayList<>(override.items);
        }

        List<String> mergedLegacyCommands = new ArrayList<>(this.legacyCommands);
        mergedLegacyCommands.addAll(override.legacyCommands);

        List<String> mergedConsoleCommands = new ArrayList<>(this.consoleCommands);
        mergedConsoleCommands.addAll(override.consoleCommands);

        List<String> mergedPlayerCommands = new ArrayList<>(this.playerCommands);
        mergedPlayerCommands.addAll(override.playerCommands);

        return new RewardDefinition(
            override.money != null ? override.money : this.money,
            override.xp != null ? override.xp : this.xp,
            mergedItems,
            mergedLegacyCommands,
            mergedConsoleCommands,
            mergedPlayerCommands
        );
    }
}
