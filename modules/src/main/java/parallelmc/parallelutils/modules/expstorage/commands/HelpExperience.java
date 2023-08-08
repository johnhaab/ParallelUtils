package parallelmc.parallelutils.modules.expstorage.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import parallelmc.parallelutils.ParallelUtils;
import parallelmc.parallelutils.modules.parallelchat.ParallelChat;

import java.util.logging.Level;

public class HelpExperience implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, String[] args) {
        if (commandSender instanceof Player player) {
            ParallelChat.sendMessageTo(player,"To deposit/withdraw experience you must be standing on top of a ender-chest, " +
                    "/depositesp <amount/all> will deposit the experience safely into your ender-chest, and /withdrawexp <amount/all> " +
                    "will withdraw the experience from your ender-chest.");
            return true;
        }
        ParallelUtils.log(Level.WARNING, "Tried to run /helpexp from non-player command source: " + commandSender.getName());
        return true;
    }
}
