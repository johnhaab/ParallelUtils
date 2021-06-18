package parallelmc.parallelutils.modules.custommobs;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftZombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import parallelmc.parallelutils.Constants;
import parallelmc.parallelutils.ParallelModule;
import parallelmc.parallelutils.Parallelutils;
import parallelmc.parallelutils.modules.custommobs.commands.ParallelCreateSpawnerCommand;
import parallelmc.parallelutils.modules.custommobs.commands.ParallelDeleteSpawnerCommand;
import parallelmc.parallelutils.modules.custommobs.commands.ParallelListSpawnersCommand;
import parallelmc.parallelutils.modules.custommobs.commands.ParallelSummonCommand;
import parallelmc.parallelutils.modules.custommobs.events.CustomMobsEventRegistrar;
import parallelmc.parallelutils.modules.custommobs.nmsmobs.EntityData;
import parallelmc.parallelutils.modules.custommobs.nmsmobs.EntityFireWisp;
import parallelmc.parallelutils.modules.custommobs.nmsmobs.EntityWisp;
import parallelmc.parallelutils.modules.custommobs.nmsmobs.SpawnReason;
import parallelmc.parallelutils.modules.custommobs.particles.ParticleOptions;
import parallelmc.parallelutils.modules.custommobs.registry.EntityRegistry;
import parallelmc.parallelutils.modules.custommobs.registry.ParticleRegistry;
import parallelmc.parallelutils.modules.custommobs.registry.SpawnerRegistry;
import parallelmc.parallelutils.modules.custommobs.spawners.LeashTask;
import parallelmc.parallelutils.modules.custommobs.spawners.SpawnTask;
import parallelmc.parallelutils.modules.custommobs.spawners.SpawnerData;
import parallelmc.parallelutils.modules.custommobs.spawners.SpawnerOptions;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class CustomMobs implements ParallelModule {

	private Parallelutils puPlugin;
	private Connection dbConn;

	private boolean finishedSetup = false;

	public void onEnable() {
		PluginManager manager = Bukkit.getPluginManager();
		Plugin plugin = manager.getPlugin(Constants.PLUGIN_NAME);

		if (plugin == null) {
			Parallelutils.log(Level.SEVERE, "Unable to enable CustomMobs. Plugin " + Constants.PLUGIN_NAME + " does not exist!");
			return;
		}

		puPlugin = (Parallelutils) plugin;

		if (!puPlugin.registerModule("CustomMobs", this)) {
			Parallelutils.log(Level.SEVERE, "Unable to register module CustomMobs! Module may already be registered. Quitting...");
			return;
		}

		SpawnerRegistry.getInstance().registerSpawnerType("wisp", new SpawnerOptions(0, 0, 8,
				1, 400, 0, true, 40, 16,
				false, false));
		SpawnerRegistry.getInstance().registerSpawnerType("fire_wisp", new SpawnerOptions(0, 0, 8,
				1, 400, 0, true, 40, 16,
				false, false));


		// Get dbConn

		dbConn = puPlugin.getDbConn();

		// Create the table if it doesn't exist
		try {
			Statement statement = dbConn.createStatement();
			statement.setQueryTimeout(15);
			statement.execute("""
					create table if not exists WorldMobs
					(
					    UUID        varchar(36) not null,
					    Type        varchar(16) not null,
					    World       varchar(32) not null,
					    ChunkX      int         not null,
					    ChunkZ      int         not null,
					    spawnReason varchar(32) not null,
					    spawnerId   varchar(36) null,
					    constraint WorldMobs_UUID_uindex
					        unique (UUID)
					);""");
			dbConn.commit();

			statement.execute("""
					create table if not exists Spawners
					(
					    id       varchar(36) not null,
					    type     varchar(16) not null,
					    world    varchar(32) null,
					    x        int         not null,
					    y        int         not null,
					    z        int         not null,
					    hasLeash tinyint     not null,
					    constraint Spawners_id_uindex
					        unique (id)
					);""");
			dbConn.commit();

			statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// Load spawners and mobs
		try {
			Statement statement = dbConn.createStatement();
			statement.setQueryTimeout(15);

			ResultSet spawnerResults = statement.executeQuery("SELECT * FROM Spawners");

			readSpawners(spawnerResults);

			ResultSet result = statement.executeQuery("SELECT * FROM WorldMobs");

			readMobs(result);

			statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		ParticleRegistry.getInstance().registerParticles("wisp", new ParticleOptions
				(Particle.CLOUD, 50, 0.5, 1, 0));
		ParticleRegistry.getInstance().registerParticles("fire_wisp", new ParticleOptions
				(Particle.LAVA, 40, 0.25, 0.5, 0));


		// Register events for the CustomMobs module
		CustomMobsEventRegistrar.registerEvents();

		// Setup commands
		puPlugin.addCommand("summon", new ParallelSummonCommand());
		puPlugin.addCommand("createspawner", new ParallelCreateSpawnerCommand());
		puPlugin.addCommand("listspawners", new ParallelListSpawnersCommand());
		puPlugin.addCommand("deletespawner", new ParallelDeleteSpawnerCommand());

		finishedSetup = true;
	}

	public void onDisable() {
		// Clear the database
		if (finishedSetup) {
			try {
				Statement removeStatement = dbConn.createStatement();
				removeStatement.setQueryTimeout(15);
				removeStatement.execute("TRUNCATE TABLE WorldMobs");
				removeStatement.execute("TRUNCATE TABLE Spawners");
				dbConn.commit();
			} catch (SQLException e) {
				Parallelutils.log(Level.WARNING, "Could not connect to DB");
				Parallelutils.log(Level.WARNING, "Trying again...");

				// Try reconnecting
				try {
					puPlugin.resetDb();

					Statement removeStatement = dbConn.createStatement();
					removeStatement.setQueryTimeout(15);
					removeStatement.execute("TRUNCATE TABLE WorldMobs");
					removeStatement.execute("TRUNCATE TABLE Spawners");
					dbConn.commit();

				} catch (SQLException | ClassNotFoundException ex) {
					ex.printStackTrace();
					Parallelutils.log(Level.WARNING, "Failed Twice. Something is broken!!!");
				}
			}

			Parallelutils.log(Level.INFO, "Cleared tables");

			// Insert all mobs that we care about into the database
			try (PreparedStatement statement = dbConn.prepareStatement("INSERT INTO WorldMobs " +
					"(UUID, Type, World, ChunkX, ChunkZ, spawnReason, spawnerId) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
				int i = 0;
				statement.setQueryTimeout(15);

				for (EntityData ep : EntityRegistry.getInstance().getEntities()) {
					Entity e = ep.entity;
					CraftEntity craftEntity = e.getBukkitEntity();

					String uuid = craftEntity.getUniqueId().toString();

					Parallelutils.log(Level.INFO, "Storing entity " + uuid);

					String type = ep.type;

					if (type == null) {
						Parallelutils.log(Level.WARNING, "Unknown entity type for entity " + uuid);
						continue;
					}

					String world = craftEntity.getWorld().getName();

					Chunk c = craftEntity.getChunk();

					SpawnReason reason = ep.spawnReason;

					statement.setString(1, uuid);
					statement.setString(2, type);
					statement.setString(3, world);
					statement.setInt(4, c.getX());
					statement.setInt(5, c.getZ());
					statement.setString(6, reason.name());
					statement.setString(7, null);

					if (reason == SpawnReason.SPAWNER) {
						SpawnerData data = SpawnerRegistry.getInstance().getSpawner(ep.spawnOrigin);

						if (data != null) {
							String spawnerId = data.getUuid();
							statement.setString(7, spawnerId);
						} else {
							Parallelutils.log(Level.INFO, "Spawner does not exist. Ignoring");
							statement.setString(6, SpawnReason.UNKNOWN.name());
						}
					}

					// This just lets us execute a bunch of changes at once
					statement.addBatch();

					// This is here because some implementations of MySQL are weird and don't like very large batches
					i++;
					if (i >= 1000) {
						statement.executeBatch();
						i = 0;
					}
				}

				statement.executeBatch();

				dbConn.commit();
			} catch (SQLException e) {
				e.printStackTrace();
			}

			try (PreparedStatement statement = dbConn.prepareStatement("INSERT INTO Spawners " +
					"(id, type, world, x, y, z, hasLeash) VALUES (?,?,?,?,?,?,?)")) {
				int i = 0;
				statement.setQueryTimeout(15);

				for (SpawnerData sd : SpawnerRegistry.getInstance().getSpawnerData()) {
					Parallelutils.log(Level.INFO, sd.toString());
					statement.setString(1, sd.getUuid());
					statement.setString(2, sd.getType());
					Location location = sd.getLocation();
					statement.setString(3, location.getWorld().getName());
					statement.setInt(4, location.getBlockX());
					statement.setInt(5, location.getBlockY());
					statement.setInt(6, location.getBlockZ());
					statement.setBoolean(7, sd.hasLeash());

					statement.addBatch();

					i++;
					if (i >= 1000) {
						statement.executeBatch();
						i = 0;
					}
				}

				statement.executeBatch();

				dbConn.commit();

			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * A helper method to parse the ResultSet from SQL and register the spawner data
	 * @param result The ResultSet to parse
	 * @throws SQLException if a database access error occurs or this method is called on a closed result set
	 */
	private void readSpawners(ResultSet result) throws SQLException {
		while (result.next()) {
			String id = result.getString("id");
			String type = result.getString("type");
			String world = result.getString("world");
			int x = result.getInt("x");
			int y = result.getInt("y");
			int z = result.getInt("z");
			boolean hasLeash = result.getBoolean("hasLeash");

			Location location = new Location(puPlugin.getServer().getWorld(world), x, y, z);

			SpawnerRegistry.getInstance().registerSpawner(id, type, location, hasLeash);

			// TODO: Replace puPlugin with this when in separate plugins
			BukkitTask task = new SpawnTask(type, location, 0)
					.runTaskTimer(puPlugin, 0, SpawnerRegistry.getInstance().
							getSpawnerOptions(type).cooldown);
			SpawnerRegistry.getInstance().addSpawnTaskID(location, task.getTaskId());
		}
	}

	/**
	 * A helper method to parse the ResultSet from SQL and register the mob data
	 * @param result The ResultSet to parse
	 * @throws SQLException if a database access error occurs or this method is called on a closed result set
	 */
	private void readMobs(ResultSet result) throws SQLException {
		while (result.next()) {
			String uuid = result.getString("UUID");
			String type = result.getString("Type");
			String world = result.getString("World");
			String chunkX = result.getString("ChunkX");
			String chunkZ = result.getString("ChunkZ");
			SpawnReason spawnReason = SpawnReason.valueOf(result.getString("spawnReason"));
			String spawnerId = result.getString("spawnerId");

			Location spawnerLocation = null;
			if (spawnReason == SpawnReason.SPAWNER) {
				PreparedStatement statement = dbConn.prepareStatement("SELECT * FROM Spawners WHERE id=?");

				statement.setString(1, spawnerId);

				ResultSet spawnerResults = statement.executeQuery();
				if (!spawnerResults.next()) {
					Parallelutils.log(Level.WARNING, "Invalid spawner id " + spawnerId);
					continue;
				}

				String spawnerWorld = spawnerResults.getString("world");
				int spawnerX = spawnerResults.getInt("x");
				int spawnerY = spawnerResults.getInt("y");
				int spawnerZ = spawnerResults.getInt("z");

				spawnerLocation = new Location(Bukkit.getWorld(spawnerWorld), spawnerX, spawnerY, spawnerZ);
			}

			int worldX = 16 * Integer.parseInt(chunkX);
			int worldZ = 16 * Integer.parseInt(chunkZ);

			//Bukkit.getServer().createWorld(new WorldCreator(world)); // This loads the world

			Location location = new Location(Bukkit.getWorld(world), worldX, 70, worldZ);

			if (!location.getChunk().isLoaded()) {
				location.getChunk().load();
			}

			CraftEntity mob = (CraftEntity) Bukkit.getEntity(UUID.fromString(uuid));

			String entityType = "";
			EntityInsentient setupEntity = null;

			if (mob != null) {
				switch (type) {
					case "wisp" -> {
						entityType = "wisp";
						setupEntity = EntityWisp.setup(puPlugin, (CraftZombie) mob);
					}
					case "fire_wisp" -> {
						entityType = "fire_wisp";
						setupEntity = EntityFireWisp.setup(puPlugin, (CraftZombie) mob);
					}
					default -> Parallelutils.log(Level.WARNING, "Unknown entity type \"" + type + "\"");
				}
			} else {
				Parallelutils.log(Level.WARNING, "Mob is null! Report this to the devs! Expected UUID: " + uuid);
			}

			if (setupEntity != null) {
				if (spawnerLocation != null) {
					EntityRegistry.getInstance().registerEntity(uuid, entityType, setupEntity, spawnReason, spawnerLocation);
					SpawnerRegistry.getInstance().incrementMobCount(spawnerLocation);
					if (SpawnerRegistry.getInstance().getSpawner(spawnerLocation).hasLeash()) {
						SpawnerRegistry.getInstance().addLeashedEntity(spawnerLocation, uuid);
						if (SpawnerRegistry.getInstance().getLeashTaskID(spawnerLocation) == null) {
							BukkitTask task = new LeashTask(spawnerLocation).runTaskTimer(puPlugin, 0, 10);
							SpawnerRegistry.getInstance().addLeashTaskID(spawnerLocation, task.getTaskId());
						}
					}
				} else {
					EntityRegistry.getInstance().registerEntity(uuid, entityType, setupEntity, spawnReason);
				}
			}
		}
	}
}
