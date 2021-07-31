package parallelmc.parallelutils.modules.custommobs.registry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import parallelmc.parallelutils.Constants;
import parallelmc.parallelutils.Parallelutils;
import parallelmc.parallelutils.modules.custommobs.spawners.SpawnerData;
import parallelmc.parallelutils.modules.custommobs.spawners.SpawnerOptions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * This singleton class is responsible for keeping track of entities with their UUID and their EntityData object
 */
public class SpawnerRegistry {

	private final HashMap<String, SpawnerOptions> spawnerTypes;

	private final HashMap<Location, SpawnerData> spawners;

	private final HashMap<Location, Integer> mobCounts;

	private final HashMap<Location, Integer> spawnTaskID;

	private final HashMap<Location, Integer> leashTaskID;

	private final HashMap<Location, ArrayList<String>> leashedEntityLists;

	private static SpawnerRegistry registry;

	private final Parallelutils puPlugin;

	private SpawnerRegistry() {
		spawnerTypes = new HashMap<>();
		spawners = new HashMap<>();
		mobCounts = new HashMap<>();
		spawnTaskID = new HashMap<>();
		leashTaskID = new HashMap<>();
		leashedEntityLists = new HashMap<>();

		PluginManager manager = Bukkit.getPluginManager();
		Plugin plugin = manager.getPlugin(Constants.PLUGIN_NAME);

		if (plugin == null) {
			Parallelutils.log(Level.SEVERE, "Unable to get ParallelUtils. Plugin " + Constants.PLUGIN_NAME + " does not exist!");
			puPlugin = null;
			return;
		}

		puPlugin = (Parallelutils) plugin;
	}

	/**
	 * Retrieve the singleton instance of this class
	 * @return The instance of this class
	 */
	public static SpawnerRegistry getInstance() {
		if (registry == null) {
			registry = new SpawnerRegistry();
		}

		return registry;
	}

	/**
	 * Register a spawner in the Registry to keep track of the type of spawner,
	 * the location of the spawner, and if the spawner leashes entities with a random UUID
	 * @param type The type of spawner
	 * @param location The location of the spawner
	 * @param hasLeash True if the spawner leashes entities, false otherwise
	 */
	public void registerSpawner(String type, Location location, boolean hasLeash) {
		registerSpawner(UUID.randomUUID().toString(), type, location, hasLeash);
	}

	/**
	 * Register a spawner in the Registry to keep track of a given UUID, the type of spawner,
	 * the location of the spawner, and if the spawner leashes entities
	 * @param uuid The specified UUID of the spawner
	 * @param type The type of spawner
	 * @param location The location of the spawner
	 * @param hasLeash True if the spawner leashes entities, false otherwise
	 */
	public void registerSpawner(String uuid, String type, Location location, boolean hasLeash) {
		location.setPitch(0);
		location.setYaw(0);
		Parallelutils.log(Level.INFO, "Registering spawner " + uuid + " location: " + location.toString());
		spawners.put(location, new SpawnerData(uuid, type, location.toBlockLocation(), hasLeash));
		updateSpawnerDatabase(location);
	}

	/**
	 * Returns the spawner at a given location or null if no spawner at that location exists
	 * @param location The location of the spawner
	 * @return The spawner at the specified location
	 */
	public SpawnerData getSpawner(Location location) {
		return spawners.get(location);
	}

	/**
	 * Returns the Location of a spawner given its UUID
	 * @param uuid The UUID of the spawner
	 * @return The location of the spawner
	 */
	public Location getSpawnerLoc(String uuid) {
		Optional<SpawnerData> data = getSpawnerData().stream().filter(x -> x.getUuid().equals(uuid)).findFirst();

		return data.map(SpawnerData::getLocation).orElse(null);
	}

	/**
	 * Unregister a spawner at a given location from the registry
	 * @param location The location of the spawner to remove
	 * @return Returns true if the spawner was removed or false if the spawner does not exist
	 */
	public boolean deleteSpawner(Location location) {
		Parallelutils.log(Level.INFO, "Removing spawner " + location.toString());
		SpawnerData result = spawners.remove(location);

		if (result != null) {
			deleteSpawnerDatabase(result.getUuid());
			return true;
		}

		return false;
	}

	/**
	 * Returns a Collection of all of the spawner locations registered
	 * @return A Collection of the locations of all registered spawners
	 */
	public Collection<Location> getSpawnerLocations() {
		return spawners.keySet();
	}

	/**
	 * Returns a Collection of the SpawnerData objects of all registered spawners
	 * @return A Collection of the SpawnerData objects of all registered spawners
	 */
	public Collection<SpawnerData> getSpawnerData() {
		return spawners.values();
	}

	/**
	 * Register a new spawner type with a set of options to the Registry
	 * @param type The name of the new type of spawner
	 * @param options The options for this spawner type to use
	 */
	public void registerSpawnerType(String type, SpawnerOptions options) {
		Parallelutils.log(Level.INFO, "Registering spawner type for " + type);
		spawnerTypes.put(type, options);
	}

	/**
	 * Returns the SpawnerOptions for the given type of spawner
	 * @param type The type of spawner to retrieve options from
	 * @return The SpawnerOptions for the specified type of spawner
	 */
	public SpawnerOptions getSpawnerOptions(String type) {
		return spawnerTypes.get(type);
	}

	/**
	 * Register a new mob count for a spawner
	 * @param loc The location of the spawner
	 * @param count The initial mob count
	 */
	public void addCount(Location loc, int count) {
		Parallelutils.log(Level.INFO, "Registering counter for " + loc.toString());
		mobCounts.put(loc, count);
	}

	/**
	 * Returns the number of mobs counted for this spawner or null if the spawner does not exist
	 * @param loc The location of the spawner to check
	 * @return The number of mobs counted for this spawner or null if the spawner does not exist
	 */
	public Integer getMobCount(Location loc) {
		return mobCounts.get(loc);
	}

	/**
	 * Sets the mob count for a spawner to the value given
	 * @param loc The location of the spawner to set the mob count of
	 * @param count The new mob count for the spawner
	 */
	public void setMobCount(Location loc, int count) {
		mobCounts.replace(loc, count);
	}

	/**
	 * Increment the mob count of a spawner
	 * @param loc The location of the spawner to increment the mob count of
	 */
	public void incrementMobCount(Location loc) {
		Parallelutils.log(Level.INFO, "Incrementing mob count for " + loc.toString());
		if (!mobCounts.containsKey(loc)) {
			addCount(loc, 1);
		} else {
			mobCounts.replace(loc, mobCounts.get(loc) + 1);
		}
	}

	/**
	 * Decrement the mob count of a spawner
	 * @param loc The location of the spawner to decrement the mob count of
	 */
	public void decrementMobCount(Location loc) {
		Parallelutils.log(Level.INFO, "Decrementing mob count for " + loc.toString());
		if (!mobCounts.containsKey(loc)) {
			return;
		}
		mobCounts.replace(loc, mobCounts.get(loc) - 1);
	}

	/**
	 * Remove the mob count object associated with the spawner
	 * @param loc The location of the spawner to remove the mob count of
	 */
	public void removeMobCount(Location loc) {
		mobCounts.remove(loc);
	}

	/**
	 * Register the id of the spawn task associated with a spawner
	 * @param loc The location of the spawner
	 * @param id The id of the BukkitRunnable associated with spawning mobs for this spawner
	 */
	public void addSpawnTaskID(Location loc, int id) {
		Parallelutils.log(Level.INFO, "Adding spawn task ID for " + loc.toString());
		spawnTaskID.put(loc, id);
	}

	/**
	 * Returns the id of the BukkitRunnable associated with spawning mobs for this spawner
	 * @param loc The location of the spawner
	 * @return The spawn task id
	 */
	public Integer getSpawnTaskID(Location loc) {
		return spawnTaskID.get(loc);
	}

	/**
	 * Deregister the id of the spawn task associated with a spawner
	 * @param loc The location of the spawner
	 */
	public void removeSpawnTaskID(Location loc) {
		spawnTaskID.remove(loc);
	}

	/**
	 *  Register the id of the leash task associated with a spawner
	 * @param loc The location of the spawner
	 * @param id The id of the BukkitRunnable associated with leashing mobs for this spawner
	 */
	public void addLeashTaskID(Location loc, int id) {
		leashTaskID.put(loc, id);
	}

	/**
	 * Returns the id of the BukkitRunnable associated with leashing  mobs for this spawner
	 * @param loc The location of the spawner
	 * @return The leash task id
	 */
	public Integer getLeashTaskID(Location loc) {
		return leashTaskID.get(loc);
	}

	/**
	 * Deregister the id of the leash task associated with a spawner
	 * @param loc The location of the spawner
	 */
	public void removeLeashTaskID(Location loc) {
		leashTaskID.remove(loc);
	}

	/**
	 * Register the UUID of a leashed entity associated with the given spawner
	 * @param loc The location of the spawner
	 * @param id The UUID of the entity to be leashed to the spawner
	 */
	public void addLeashedEntity(Location loc, String id) {
		Parallelutils.log(Level.INFO, "Adding leashed entity for " + loc.toString());
		if (!leashedEntityLists.containsKey(loc)) {
			leashedEntityLists.put(loc, new ArrayList<>());
		}
		leashedEntityLists.get(loc).add(id);
	}

	/**
	 * Deregister the UUID of a leashed entity associated with the given spawner
	 * @param loc The location of the spawner
	 * @param id The UUID of the entity to be deregistered from the spawner
	 */
	public void removeLeashedEntity(Location loc, String id) {
		leashedEntityLists.get(loc).remove(id);
	}

	/**
	 * Returns a Collection of the entities leashed to the specified spawner
	 * @param loc The location of the spawner
	 * @return A Collection of the entities leashed
	 */
	public Collection<String> getLeashedEntities(Location loc) {
		return leashedEntityLists.get(loc);
	}

	/**
	 * Removes leash data associated with the specified spawner
	 * @param loc The location of the spawner
	 */
	public void removeSpawnerLeash(Location loc) {
		//ArrayList<String> mobs = leashedEntityLists.get(loc);
		leashedEntityLists.remove(loc);
	}

	/**
	 * Adds/updates a spawner in the database
	 * @param loc The location of the spawner
	 */
	private void updateSpawnerDatabase(Location loc) {
		SpawnerData spawner = getSpawner(loc);

		if (spawner == null) return;

		Bukkit.getScheduler().runTaskAsynchronously(puPlugin, new Runnable() {
			@Override
			public void run() {
				try (Connection conn = puPlugin.getDbConn()) {
					if (conn == null) throw new SQLException("Unable to establish connection!");

					PreparedStatement statement = conn.prepareStatement(
							"REPLACE Spawners(id, type, world, x, y, z, hasLeash) " +
									"VALUES(?, ?, ?, ?, ?, ?, ?)"
					);

					statement.setString(1, spawner.getUuid());
					statement.setString(2, spawner.getType());
					statement.setString(3, loc.getWorld().getName());
					statement.setInt(4, loc.getBlockX());
					statement.setInt(5, loc.getBlockY());
					statement.setInt(6, loc.getBlockZ());
					statement.setBoolean(7, spawner.hasLeash());

					statement.execute();

					conn.commit();

					statement.close();
				} catch (SQLException e) {
					Parallelutils.log(Level.WARNING, "Unable to update spawner in database!");
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Removes a spawner from the database
	 * @param uuid The UUID of the spawner
	 */
	private void deleteSpawnerDatabase(String uuid) {
		Bukkit.getScheduler().runTaskAsynchronously(puPlugin, new Runnable() {
			@Override
			public void run() {
				try (Connection conn = puPlugin.getDbConn()) {
					if (conn == null) throw new SQLException("Unable to establish connection!");

					PreparedStatement statement = conn.prepareStatement(
							"DELETE FROM Spawners WHERE id = ?"
					);

					statement.setString(1, uuid);

					statement.execute();

					conn.commit();

					statement.close();
				} catch (SQLException e) {
					Parallelutils.log(Level.WARNING, "Unable to delete spawner from database!");
					e.printStackTrace();
				}
			}
		});
	}
}
