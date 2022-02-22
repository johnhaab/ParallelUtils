package parallelmc.parallelutils.modules.paralleltutorial;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import parallelmc.parallelutils.Constants;
import parallelmc.parallelutils.ParallelModule;
import parallelmc.parallelutils.Parallelutils;
import parallelmc.parallelutils.modules.parallelchat.ParallelChat;
import parallelmc.parallelutils.modules.paralleltutorial.commands.*;
import parallelmc.parallelutils.modules.paralleltutorial.handlers.OnLeaveDuringTutorial;
import parallelmc.parallelutils.modules.paralleltutorial.handlers.OnSpectatorTeleport;
import parallelmc.parallelutils.modules.paralleltutorial.scripting.Instruction;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;

public class ParallelTutorial implements ParallelModule {

    private Parallelutils puPlugin;

    private ProtocolManager protManager;

    // mostly for use in server crashes/shutdown
    public static HashMap<Player, BukkitTask> runningTutorials = new HashMap<>();

    public static HashMap<Player, ArmorStand> armorStands = new HashMap<>();

    public static HashMap<Player, Location> startPoints = new HashMap<>();

    private final HashMap<String, ArrayList<Instruction>> tutorials = new HashMap<>();

    // doing this again cuz lazy
    private static ParallelTutorial Instance;

    @Override
    public void onEnable() {
        PluginManager manager = Bukkit.getPluginManager();
        Plugin plugin = manager.getPlugin(Constants.PLUGIN_NAME);

        if (plugin == null) {
            Parallelutils.log(Level.SEVERE, "Unable to enable ParallelTutorial. Plugin " + Constants.PLUGIN_NAME
                    + " does not exist!");
            return;
        }

        this.puPlugin = (Parallelutils) plugin;

        if (!puPlugin.registerModule("ParallelTutorial", this)) {
            Parallelutils.log(Level.SEVERE, "Unable to register module ParallelChat! " +
                    "Module may already be registered. Quitting...");
            return;
        }

        this.protManager = ProtocolLibrary.getProtocolManager();

        LoadTutorials();

        manager.registerEvents(new OnSpectatorTeleport(), puPlugin);
        manager.registerEvents(new OnLeaveDuringTutorial(), puPlugin);

        puPlugin.getCommand("starttutorial").setExecutor(new ParallelStartTutorial());
        puPlugin.getCommand("listtutorials").setExecutor(new ParallelListTutorials());
        puPlugin.getCommand("reloadtutorials").setExecutor(new ParallelReloadTutorials());
        puPlugin.getCommand("leavetutorial").setExecutor(new ParallelLeaveTutorial());
        puPlugin.getCommand("tutorialinfo").setExecutor(new ParallelTutorialInfo());

        Instance = this;
    }

    @Override
    public void onDisable() {
        // if anyone is in a tutorial take them out of it
        runningTutorials.forEach((p, t) -> {
            t.cancel();
            endTutorialFor(p);
        });
        runningTutorials.clear();
    }

    public static ParallelTutorial get(){
        return Instance;
    }

    public HashMap<String, ArrayList<Instruction>> GetTutorials() {
        return tutorials;
    }

    // TODO: possibly fix SOUND at some point
    public void LoadTutorials() {
        tutorials.clear();
        AtomicInteger line = new AtomicInteger();
        Path tutorialFolder = Path.of(puPlugin.getDataFolder() + "/tutorials");
        if (!Files.exists(tutorialFolder)) {
            try {
                Parallelutils.log(Level.WARNING, "Tutorial folder not found, attempting to create one...");
                Files.createDirectory(tutorialFolder);
                Parallelutils.log(Level.WARNING, "Created Tutorial folder!");
            }
            catch (IOException e) {
                Parallelutils.log(Level.SEVERE, "Failed to create tutorial folder!");
            }
        }
        try (Stream<Path> paths = Files.walk(tutorialFolder)) {
            paths.filter(Files::isRegularFile).forEach((f -> {
                ArrayList<Instruction> instructions = new ArrayList<>();
                try (Stream<String> stream = Files.lines(f)) {
                    stream.forEach(s -> {
                        String[] split = s.split(" ");
                        switch (split[0]) {
                            case "START", "TELEPORT" -> {
                                if (split.length != 4) {
                                    Parallelutils.log(Level.SEVERE, "Tutorial Parse Error: Expected 3 arguments on line " + line.get());
                                    return;
                                }
                                instructions.add(new Instruction(split[0], new String[]{split[1], split[2], split[3]}));
                            }
                            case "MOVE" -> {
                                if (split.length != 5) {
                                    Parallelutils.log(Level.SEVERE, "Tutorial Parse Error: Expected 4 arguments on line " + line.get());
                                    return;
                                }
                                instructions.add(new Instruction("MOVE", new String[]{split[1], split[2], split[3], split[4]}));
                            }
                            case "ROTATE" -> {
                                if (split.length != 3) {
                                    Parallelutils.log(Level.SEVERE, "Tutorial Parse Error: Expected 2 arguments on line " + line.get());
                                    return;
                                }
                                instructions.add(new Instruction("ROTATE", new String[]{split[1], split[2]}));
                            }
                            case "WAIT" -> {
                                if (split.length != 2) {
                                    Parallelutils.log(Level.SEVERE, "Tutorial Parse Error: Expected 1 argument on line " + line.get());
                                    return;
                                }
                                instructions.add(new Instruction("WAIT", new String[]{split[1]}));
                            }
                            case "SAY" -> {
                                if (split.length < 2) {
                                    Parallelutils.log(Level.SEVERE, "Tutorial Parse Error: Expected 1 argument on line " + line.get());
                                    return;
                                }
                                instructions.add(new Instruction("SAY", Arrays.copyOfRange(split, 1, split.length)));
                            }
                            /*case "SOUND" -> {
                                if (split.length < 2) {
                                    Parallelutils.log(Level.SEVERE, "Tutorial Parse Error: Expected 1 argument on line " + line.get());
                                    break;
                                }
                                instructions.add(new Instruction("SOUND", new String[]{split[1]}));
                            }*/
                            case "END" -> {
                                instructions.add(new Instruction("END", null));
                            }
                            default -> {
                                Parallelutils.log(Level.SEVERE, "Tutorial Parse Error: Unknown instruction on line " + line.get());
                            }
                        }
                        line.getAndIncrement();

                    });
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                // beautiful line of code
                this.tutorials.put(f.getFileName().toString().split("\\.")[0].toLowerCase(), instructions);
                Parallelutils.log(Level.INFO, "Loaded " + instructions.size() + " instructions from tutorial " + f.getFileName().toString());
            }));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean HasTutorial(String tutorial) {
        return tutorials.containsKey(tutorial.toLowerCase());
    }

    public void RunTutorialFor(@NotNull Player player, @NotNull String tutorial) {
        final World world = player.getWorld();
        // TIL entities can't be spawned in async runnables
        Bukkit.getScheduler().runTaskAsynchronously(puPlugin, new Runnable() {
            Vector lookAt = null;
            final ArrayList<Instruction> instructions = tutorials.get(tutorial.toLowerCase());
            boolean instructionFinished = true;
            int instructionIndex = 0;
            BukkitTask loop;
            @Override
            public void run() {
                startPoints.put(player, player.getLocation());
                loop = new BukkitRunnable() {
                    ArmorStand stand;
                    @Override
                    public void run() {
                        if (stand != null && player.getLocation().distanceSquared(stand.getLocation()) > 256) {
                            Bukkit.getScheduler().runTask(puPlugin, () -> {
                                player.teleport(stand.getLocation());
                            });
                        }
                        // only run the next instruction if the current one is finished
                        if (instructionFinished) {
                            Instruction i = instructions.get(instructionIndex);
                            instructionFinished = false;
                            switch (i.name()) {
                                case "START" -> {
                                    Bukkit.getScheduler().runTask(puPlugin, () -> {
                                        Location start = new Location(world, Double.parseDouble(i.args()[0]), Double.parseDouble(i.args()[1]), Double.parseDouble(i.args()[2]));
                                        stand = (ArmorStand)world.spawnEntity(start, EntityType.ARMOR_STAND, CreatureSpawnEvent.SpawnReason.COMMAND);
                                        stand.setGravity(false);
                                        stand.setVisible(false);
                                        stand.setBasePlate(false);
                                        stand.setInvulnerable(true);
                                        stand.setHeadPose(EulerAngle.ZERO);
                                        player.setGameMode(GameMode.SPECTATOR);
                                        player.setFlySpeed(0F);
                                        // force player to spectate the armor stand
                                        // the player's actual model will be stuck back at the start
                                        forceSpectate(player, stand.getEntityId());
                                        armorStands.put(player, stand);
                                        instructionFinished = true;
                                    });
                                }
                                case "MOVE" -> {
                                    final Location a = stand.getLocation();
                                    final Location b = new Location(world, Double.parseDouble(i.args()[0]), Double.parseDouble(i.args()[1]), Double.parseDouble(i.args()[2]));
                                    new BukkitRunnable() {
                                        final float duration = Float.parseFloat(i.args()[3]) * 20f;
                                        float steps = 0f;
                                        @Override
                                        public void run() {
                                            if (steps == duration) {
                                                if (lookAt != null) {
                                                    b.setYaw((float)lookAt.getX());
                                                    b.setPitch((float)lookAt.getY());
                                                }
                                                stand.teleport(b);
                                                instructionFinished = true;
                                                this.cancel();
                                            }
                                            else {
                                                float t = steps / duration;
                                                if (t < 0f)
                                                    t = 0f;
                                                else if (t > 1F)
                                                    t = 1f;
                                                Location point = new Location(world,
                                                        a.getX() + (b.getX() - a.getX()) * t,
                                                        a.getY() + (b.getY() - a.getY()) * t,
                                                        a.getZ() + (b.getZ() - a.getZ()) * t);
                                                if (lookAt != null) {
                                                    point.setYaw((float)lookAt.getX());
                                                    point.setPitch((float)lookAt.getY());
                                                }
                                                stand.teleport(point);
                                                steps++;
                                            }
                                        }
                                    }.runTaskTimer(puPlugin, 1L, 1L);
                                }
                                case "TELEPORT" -> {
                                    final Location newPoint = new Location(world, Double.parseDouble(i.args()[0]), Double.parseDouble(i.args()[1]), Double.parseDouble(i.args()[2]));
                                    new BukkitRunnable() {
                                        @Override
                                        // wait for the player to be successfully teleported
                                        public void run() {
                                            if(player.teleport(newPoint)) {
                                                if (lookAt != null) {
                                                    newPoint.setYaw((float)lookAt.getX());
                                                    newPoint.setPitch((float)lookAt.getY());
                                                }
                                                if (stand.teleport(newPoint)) {
                                                    instructionFinished = true;
                                                    this.cancel();
                                                }
                                            }
                                        }
                                    }.runTaskTimer(puPlugin, 0L, 2L);
                                }
                                case "ROTATE" -> {
                                    lookAt = new Vector(Double.parseDouble(i.args()[0]), Double.parseDouble(i.args()[1]), 0);
                                    instructionFinished = true;
                                }
                                case "WAIT" -> {
                                    long ticks = Long.parseLong(i.args()[0]);
                                    Bukkit.getScheduler().runTaskLater(puPlugin, () -> {
                                        instructionFinished = true;
                                    }, ticks);
                                }
                                case "SAY" -> {
                                    String string = ParallelChat.getStringArg(i.args());

                                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                                            "<dark_aqua><bold>\n---------------------------------------------\n\n<reset>"
                                                    + string +
                                                    "<dark_aqua><bold>\n---------------------------------------------\n"));

                                    instructionFinished = true;
                                }
                                /*case "SOUND" -> {
                                    //player.stopSound(SoundStop.all());
                                    //player.playSound(Sound.sound(Key.key(Key.MINECRAFT_NAMESPACE, i.args()[0]), Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self());
                                    instructionFinished = true;
                                }*/
                                case "END" -> {
                                    endTutorialFor(player);
                                    instructionFinished = true;
                                    loop.cancel();
                                }
                            }
                            instructionIndex++;
                        }
                    }
                }.runTaskTimer(puPlugin, 0L, 1L);

                runningTutorials.put(player, loop);
            }
        });
    }

    public void endTutorialFor(Player player) {
        Location endPoint = startPoints.get(player);
        new BukkitRunnable() {
            @Override
            public void run() {
                // wait for player to be successfully teleported
                if (player.teleport(endPoint)) {
                    player.stopSound(SoundStop.all());
                    // making the player spectate themselves brings them back to the start
                    forceSpectate(player, player.getEntityId());
                    if (armorStands.containsKey(player)) {
                        armorStands.get(player).remove();
                        armorStands.remove(player);
                    }
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setFlySpeed(0.1F);
                    startPoints.remove(player);
                    runningTutorials.remove(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(puPlugin, 0L, 2L);
    }

    private void forceSpectate(Player player, int entityId) {
        PacketContainer packet = protManager.createPacket(PacketType.Play.Server.CAMERA);
        packet.getIntegers().write(0, entityId);
        try {
            protManager.sendServerPacket(player, packet);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

}
