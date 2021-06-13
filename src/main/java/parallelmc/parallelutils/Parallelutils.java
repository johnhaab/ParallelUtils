package parallelmc.parallelutils;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import parallelmc.parallelutils.commands.Commands;
import parallelmc.parallelutils.commands.ParallelCommand;
import parallelmc.parallelutils.commands.ParallelHelpCommand;
import parallelmc.parallelutils.commands.ParallelTestCommand;
import parallelmc.parallelutils.modules.custommobs.CustomMobs;
import parallelmc.parallelutils.modules.discordintegration.DiscordIntegration;
import parallelmc.parallelutils.versionchecker.UpdateChecker;

import java.sql.*;
import java.util.HashMap;
import java.util.logging.Level;

// TODO: Add proper versioning to prevent loading invalid configs/data
public final class Parallelutils extends JavaPlugin {

	public static Level LOG_LEVEL = Level.INFO;

	String baseDataFolder = this.getDataFolder().getAbsolutePath();
	FileConfiguration config = this.getConfig();

	public static Connection dbConn;

	private String jdbc;
	private String username = "";
	private String password = "";

	private static boolean finishedSetup = false;

	private HashMap<String, ParallelModule> registeredModules;
	private Commands commands;

	@Override
	public void onLoad() {

	}

	@Override
	public void onEnable() {
		finishedSetup = false;
		// Plugin startup logic

		// TODO: Make this read the config properly and actually generate it when it's not there
		// Read config
		this.saveDefaultConfig();
		this.reloadConfig();

		int logLevel = config.getInt("debug", 2);

		switch (logLevel) {
			case 1 -> LOG_LEVEL = Level.ALL;
			case 2 -> LOG_LEVEL = Level.INFO;
			case 3 -> LOG_LEVEL = Level.WARNING;
			case 4 -> LOG_LEVEL = Level.SEVERE;
		}

		Bukkit.getLogger().setLevel(LOG_LEVEL);


		// Check version
		String github_token = config.getString("github_token");

		if (github_token != null && !github_token.trim().equals("")) {
			// Actually check version
			UpdateChecker checker = new UpdateChecker(github_token);
			Version latestVersion = checker.getLatestVersion();

			if (latestVersion != null) {
				int comp = latestVersion.compareTo(Constants.VERSION);

				if (comp > 0) {
					log(Level.WARNING, "There is a new version of ParallelUtils available for download at https://github.com/ParallelMC/ParallelUtils/releases/latest");
				} else if (comp == 0) {
					log(Level.WARNING, "You are running the latest version of ParallelUtils");
				} else {
					log(Level.WARNING, "You are running a dev version of ParallelUtils. If this is on a production server, something is broken!");
				}
			}
		} else {
			log(Level.WARNING, "github_token not found in config. Will not check for updates.");
		}

		// Either get the database connection URL from the config or construct it from the config
		String address, database;
		jdbc = config.getString("sql.jdbc");

		if (jdbc == null || jdbc.trim().equals("")) {
			address = config.getString("sql.address");
			database = config.getString("sql.database");

			jdbc = "jdbc:mysql://" + address + "/" + database;
		}

		username = config.getString("sql.username");
		password = config.getString("sql.password");


		saveConfig();

		// Connect to database

		try {
			openDatabaseConnection(jdbc, username, password);
		} catch (SQLException | ClassNotFoundException e) {
			e.printStackTrace();
		}

		registeredModules = new HashMap<>();
		commands = new Commands();

		addCommand("help", new ParallelHelpCommand());
		addCommand("test", new ParallelTestCommand());

		getCommand("parallelutils").setExecutor(commands);
		getCommand("parallelutils").setTabCompleter(commands);
		getCommand("pu").setExecutor(commands);
		getCommand("pu").setTabCompleter(commands);


		// Setup modules

		// TODO: Eventually break this out into multiple plugins. This is meant to imitate that
		CustomMobs customMobs = new CustomMobs();
		customMobs.onEnable();

		// This will eventually be a separate config file
		DiscordIntegration discordIntegration = new DiscordIntegration();
		discordIntegration.onEnable();

		finishedSetup = true;
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic

		// Clean up modules
		registeredModules.forEach((name, module) -> module.onDisable());

		try {
			dbConn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Opens a database connection with the given details and stores it to dbConn
	 * @param jdbc The jdbc connection string
	 * @param username The username used to connect
	 * @param password The password used to connect
	 * @throws SQLException if a database access error occurs
	 * @throws ClassNotFoundException if the Driver class cannot be found
	 */
	private void openDatabaseConnection(String jdbc, String username, String password) throws SQLException, ClassNotFoundException {
		if (dbConn != null && !dbConn.isClosed()) {
			return;
		}
		// Class.forName("com.mysql.jdbc.Driver");
		dbConn = DriverManager.getConnection(jdbc, username, password);
		dbConn.setAutoCommit(false);
	}

	// TODO: FIGURE OUT WHY THIS IS A THINGGGGGG
	/**
	 * Resets the database connection. Used when things break
	 */
	public void resetDb() throws SQLException, ClassNotFoundException {
		if (!dbConn.isClosed()) {
			dbConn.close();
		}
		openDatabaseConnection(jdbc, username, password);
	}

	/**
	 * Returns the DB connection object for ParallelUtils
	 * @return the DB Connection object
	 */
	public Connection getDbConn() {
		return dbConn;
	}

	/**
	 * Registers a ParallelModule with ParallelUtils
	 * @param name The name of the module
	 * @param module The module object
	 * @return True if the module was successfully registered, false otherwise
	 */
	public boolean registerModule(String name, ParallelModule module) {
		if (registeredModules.containsKey(name)) {
			return false;
		}

		registeredModules.put(name, module);
		return true;
	}

	/**
	 * Wrapper for {@code parallelmc.parallelutils.commands.Commands.addCommand}
	 * Adds a new command to the commandmap
	 * @param name The name of the command
	 * @param command The command to be run when the name is called
	 * @return Returns true when the command was added successfully, false if the command already exists.
	 */
	public boolean addCommand(String name, ParallelCommand command) {
		return commands.addCommand(name, command);
	}

	/**
	 * A helper method to log a message at a specific log level with the prefix "[ParallelUtils] "
	 * @param level The level to log the message at
	 * @param message The message to log
	 */
	public static void log(Level level, String message) {
		Bukkit.getLogger().log(level, "[ParallelUtils] " + message);
	}
}
