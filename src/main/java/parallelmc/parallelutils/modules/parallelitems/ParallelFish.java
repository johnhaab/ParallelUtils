package parallelmc.parallelutils.modules.parallelitems;

import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;

public record ParallelFish(int id, String key, int hunger, float saturation, @Nullable String cooked_key, ItemStack item) { }
