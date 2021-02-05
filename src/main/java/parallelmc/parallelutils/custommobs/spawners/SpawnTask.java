package parallelmc.parallelutils.custommobs.spawners;

import net.minecraft.server.v1_16_R3.EntityInsentient;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import parallelmc.parallelutils.custommobs.nmsmobs.EntityWisp;
import parallelmc.parallelutils.custommobs.nmsmobs.SpawnReason;
import parallelmc.parallelutils.custommobs.registry.SpawnerRegistry;
import parallelmc.parallelutils.util.DistanceTools;

import java.util.Collection;
import java.util.Random;

public class SpawnTask extends BukkitRunnable {
	private final JavaPlugin plugin;
	private final String type;
	private final SpawnerOptions options;
	private final Location location;
	private int timer;

	public SpawnTask(JavaPlugin plugin, String type, Location location, int startCount) {
		this.plugin = plugin;
		this.type = type;
		this.options = SpawnerRegistry.getInstance().getSpawnerOptions(type);
		this.location = location;
		this.timer = options.warmup;

		SpawnerRegistry.getInstance().addCount(location, startCount);
	}

	@Override
	public void run() {
		if (location.getWorld() == null) { // if the world is null, we shouldn't be running a spawner in it
			this.cancel();
			return;
		} else if (timer < options.warmup) { // if the warmup isn't over, add cooldown and skip this run
			timer += options.cooldown;
			return;
		}

		timer = 0;
		Random random = new Random();
		if (!options.checkForPlayers || playerInRange()) { // either you don't need to check for players, or one is close
			for (int i = 0; i < options.mobsPerSpawn; i++) { // run at least once
				if (SpawnerRegistry.getInstance().getMobCount(location) < options.maxMobs) { // count from spawner < max
					// create a random spawn location in radius
					Location spawnLocation = new Location(location.getWorld(),
							location.getX(), location.getY(), location.getZ());
					double randomX = random.nextDouble() * options.radiusX;
					double randomY = random.nextDouble() * options.radiusY;
					double randomZ = random.nextDouble() * options.radiusX;
					if (random.nextBoolean()) {
						randomX *= -1;
					}
					if (random.nextBoolean()) {
						randomY *= -1;
					}
					if (random.nextBoolean()) {
						randomZ *= -1;
					}
					spawnLocation.add(randomX, randomY, randomZ);

					//try to spawn entity there
					EntityInsentient setUpEntity = null;
					switch (type) {
						case "wisp":
							if(location.getWorld().getBlockAt(location).isEmpty() &&
									location.getWorld().getBlockAt(location.add(0,1,0)).isEmpty()){
								setUpEntity = EntityWisp.spawn(plugin, (CraftServer)plugin.getServer(),
									(CraftWorld)location.getWorld(), spawnLocation, SpawnReason.SPAWNER, location);
								SpawnerRegistry.getInstance().incrementMobCount(location);
							}
							break;
					}

					//if (setUpEntity != null) {
						//TODO: leash crap
					//}
				} else {
					break;
				}
			}
		}
	}

	private boolean playerInRange() {
		Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
		for (Player player : online) {
			if (DistanceTools.distance(location, player.getLocation()) < options.activationRange) {
				return true;
			}
		}
		return false;
	}


}
