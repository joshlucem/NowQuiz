package dev.joshlucem.nowquiz.quiz;

import java.util.List;
import org.bukkit.Material;

/**
 * Configurable item reward definition.
 */
public record RewardItem(Material material, int amount, String name, List<String> lore) {
}
