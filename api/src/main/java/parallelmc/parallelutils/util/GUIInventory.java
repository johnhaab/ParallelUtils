package parallelmc.parallelutils.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public abstract class GUIInventory {
    public Inventory inventory;

    // represents a placeholder item in a gui
    public static ItemStack PLACEHOLDER;

    // represents air in a gui
    public static ItemStack AIR;

    // initialize the placeholder on class load
    static {
        PLACEHOLDER = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = PLACEHOLDER.getItemMeta();
        meta.displayName(Component.text('-', NamedTextColor.AQUA));
        PLACEHOLDER.setItemMeta(meta);

        AIR = new ItemStack(Material.AIR);
    }

    public GUIInventory(int size, Component name) {
        inventory = Bukkit.createInventory(null, size, name);
    }

    public abstract void onOpen(Player player);

    public abstract void onSlotClicked(Player player, int slotNum, ItemStack itemClicked);

}
