package com.webs.J15t98J.MaintenanceManager;

import com.google.inject.Inject;
import com.webs.J15t98J.MaintenanceManager.command.*;
import com.webs.J15t98J.MaintenanceManager.event.PlayerJoinHandler;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.entity.player.Player;
import org.spongepowered.api.event.EventHandler;
import org.spongepowered.api.event.Subscribe;
import org.spongepowered.api.event.entity.player.PlayerJoinEvent;
import org.spongepowered.api.event.state.InitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.config.DefaultConfig;
import org.spongepowered.api.service.scheduler.Task;
import org.spongepowered.api.service.scheduler.TaskBuilder;
import org.spongepowered.api.service.sql.SqlService;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.sink.MessageSinks;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.GenericArguments;
import org.spongepowered.api.util.command.spec.CommandSpec;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.spongepowered.api.util.command.args.GenericArguments.*;

//TODO: interface - maintenance_start/maintenance_end events?

@Plugin(id = "maintenance", name = "MaintenanceManager", version = "0.2")
public class MaintenanceManager {

	@Inject public Game game;
	@Inject public Logger logger;
	@Inject @DefaultConfig(sharedRoot = false) private File config;
	@Inject @DefaultConfig(sharedRoot = false) private ConfigurationLoader<CommentedConfigurationNode> configManager;

	private CommentedConfigurationNode rootNode;
	private SqlService sql;

	private EventHandler<PlayerJoinEvent> joinHandler = new PlayerJoinHandler();
	private OnCommand onCMD = new OnCommand(this);

	private TaskBuilder taskBuilder;
	private Duration maintenanceWarnTime;

	private HashMap<String, String> beforeCloseMessage = new HashMap<>();
	private HashMap<String, String> onCloseMessage = new HashMap<>();
	private String beforeOpenMessage;
	private String onOpenMessage;

	private boolean maintenance;
	public Long currentScheduleID;

	@Subscribe
	public void onInitialization(InitializationEvent event) {
		//<editor-fold desc="Metrics">
		// TODO: test when fix is available
		/*
        try {
            Metrics metrics = new Metrics(game, game.getPluginManager().getPlugin("maintenance").get());
            metrics.start();
        } catch(IOException e) {
            logger.error("Error starting Metrics: ", e);
        }
        //*/
		//</editor-fold>

		//<editor-fold desc="Config">
		// Attempt to open the config file and grab a root node handle
		try {
			rootNode = configManager.load();
		} catch(IOException e) {
			logger.error("Failed to read from config file:", e);
			rootNode = null;
		}

		if(rootNode != null) {
			// Retrieve any set values, or use defaults if non-existant
			CommentedConfigurationNode messages = rootNode.getNode("message").setComment("Configure the messages displayed to users when they get disconnected.");
			String kickMessage = messages.getNode("kick").getString("The server is closing for maintenance. Please come back later!");
			String joinMessageWithDuration = messages.getNode("joinWithDuration").setComment("Shown to users when they try to connect during maintenance that has a set duration (-l).  %o is replaced with the opening time.").getString("The server is closed for maintenance until %o. Please come back later!");
			String joinMessageIndefinite = messages.getNode("joinNoDuration").setComment("Shown to users when they try to connect during indefinite maintenance.").getString("The server is closed for maintenance. Please come back later!");

			CommentedConfigurationNode announcements = rootNode.getNode("announcement").setComment("Configure the messages of broadcasts to the players. Where appropriate, the plugin will replace %t with your chosen warnTime.");
			String warnTime = announcements.getNode("warnTime").setComment("How long in advance the server will tell players about maintenance. (example: 5m30s)").getString("15m");
			CommentedConfigurationNode beforeCloseNode = announcements.getNode("beforeClose");
			beforeCloseMessage.put("exemptSoft", beforeCloseNode.getNode("exemptSoft").setComment("The warning to users with the maintenance.exempt permission before soft maintenance.").getString("The server will close for maintenance in %t, but as an exempt player you'll still be able to connect after this time."));
			beforeCloseMessage.put("exemptHard", beforeCloseNode.getNode("exemptHard").setComment("The warning to users with the maintenance.exempt permission before hard maintenance.").getString("The server will close for maintenance in %t, but as an exempt player you won't be kicked or prevented from joining."));
			beforeCloseMessage.put("notExemptSoft", beforeCloseNode.getNode("notExemptSoft").setComment("The warning to users without the maintenance.exempt permission before soft maintenance.").getString("The server will close for maintenance in %t. You won't be kicked, but you won't be able to re-connect after this time."));
			beforeCloseMessage.put("notExemptHard", beforeCloseNode.getNode("notExemptHard").setComment("The warning to users without the maintenance.exempt permission before hard maintenance.").getString("The server will close for maintenance in %t. You will be kicked and prevented from re-connecting until the server re-opens."));
			CommentedConfigurationNode onCloseNode = announcements.getNode("onClose");
			onCloseMessage.put("exempt", onCloseNode.getNode("exempt").setComment("The message shown to exempt players when maintenance starts.").getString("The server is now closed for maintenance."));
			onCloseMessage.put("notExempt", onCloseNode.getNode("notExempt").setComment("The message shown to non-exempt players when soft maintenance starts.").getString("The server is now closed for maintenance. If you disconnect now, you won't be able to reconnect!"));
			beforeOpenMessage = announcements.getNode("beforeOpen").setComment("The warning to users before the server opens.").getString("The server will open in %t.");
			onOpenMessage = announcements.getNode("onOpen").setComment("The message shown to players when the server opens.").getString("The server is now open.");

			boolean shouldShare = rootNode.getNode("notifyPlugins").setComment("Controls whether the plugin tells other plugins when maintenance starts/stops.\nOn by default; disable if other plugins add annoying compatability features.").getBoolean(true);

			// Distribute config values to relevant classes
			onCMD.setKickMessage(kickMessage);
			((PlayerJoinHandler) joinHandler).setJoinMessageIndefinite(joinMessageIndefinite);
			((PlayerJoinHandler) joinHandler).setJoinMessageWithDuration(joinMessageWithDuration);

			try {
				maintenanceWarnTime = Duration.parse("PT" + warnTime);
			} catch(DateTimeParseException e) {
				logger.error("Found badly-formatted warnTime config option; resetting to 15mins!");
				warnTime = "15m";
				maintenanceWarnTime = Duration.ofMinutes(15);
			}

			// Overwrite all values (all pre-existing values stay the same, the rest are created and set to default)
			messages.getNode("kick").setValue(kickMessage);
			messages.getNode("joinWithDuration").setValue(joinMessageWithDuration);
			messages.getNode("joinNoDuration").setValue(joinMessageIndefinite);

			announcements.getNode("warnTime").setValue(warnTime);
			announcements.getNode("beforeOpen").setValue(beforeOpenMessage);
			announcements.getNode("onOpen").setValue(onOpenMessage);

			beforeCloseNode.getNode("exemptSoft").setValue(beforeCloseMessage.get("exemptSoft"));
			beforeCloseNode.getNode("exemptHard").setValue(beforeCloseMessage.get("exemptHard"));
			beforeCloseNode.getNode("notExemptSoft").setValue(beforeCloseMessage.get("notExemptSoft"));
			beforeCloseNode.getNode("notExemptHard").setValue(beforeCloseMessage.get("notExemptHard"));

			onCloseNode.getNode("exempt").setValue(onCloseMessage.get("exempt"));
			onCloseNode.getNode("notExempt").setValue(onCloseMessage.get("notExempt"));

			rootNode.getNode("notifyPlugins").setValue(shouldShare);

			// Substitue %t in relevant messages
			for(String key : beforeCloseMessage.keySet()) {
				beforeCloseMessage.replace(key, beforeCloseMessage.get(key).replaceAll("%t", warnTime));
			}
			beforeOpenMessage = beforeOpenMessage.replaceAll("%t", warnTime);
		}

		saveConfig();
		//</editor-fold>

		//<editor-fold desc="Schedule registry">
		taskBuilder = game.getScheduler().getTaskBuilder();
		try {
			for(ScheduleObject item : getSchedule(false)) {
				scheduleMaintenance(new ScheduleObject(item.start, item.duration, item.kickPlayers, item.persistRestart, item.id));
			}
		} catch(SQLException e) {
			logger.error("Error with database: ", e);
		}
		//</editor-fold>

		//<editor-fold desc="Commands">
		HashMap<String, String> eventChoices = new HashMap<>();
		eventChoices.put("onStart", "onStart");
		CommandSpec onCommand = CommandSpec.builder().description(Texts.of("Activate maintenance mode")).permission("maintenance.on").executor(onCMD).arguments(flags().permissionFlag("maintenance.kick", "k", "-kick").permissionFlag("maintenance.persist", "p", "-persist").valueFlag(string(Texts.of("date")), "d", "-date").valueFlag(string(Texts.of("time")), "t", "-time").valueFlag(string(Texts.of("duration")), "l", "-length").valueFlag(choices(Texts.of("event"), eventChoices), "e", "-event").buildWith(GenericArguments.none())).build();

		CommandSpec offCommand = CommandSpec.builder().description(Texts.of("Deactivate maintenance mode")).permission("maintenance.off").executor(new OffCommand(this)).arguments(flags().valueFlag(string(Texts.of("time")), "s", "-schedule").buildWith(GenericArguments.none())).build();

		CommandSpec scheduleCommand = CommandSpec.builder().description(Texts.of("View or delete scheduled periods of maintenance.")).permission("maintenance.list").executor(new ScheduleCommand(this)).arguments(optional(seq(literal(Texts.of("operation"), "delete"), firstParsing(literal(Texts.of("all"), "all"), integer(Texts.of("ID")))))).build();

		CommandSpec extendCommand = CommandSpec.builder().description(Texts.of("Extend the current maintenance period.")).permission("maintenance.schedule").executor(new ExtendCommand()).arguments().build();

		CommandSpec statusCommand = CommandSpec.builder().description(Texts.of("Check the current maintenance status.")).permission("maintenance.status").executor(new StatusCommand()).build();

		CommandSpec maintenanceCommand = CommandSpec.builder().description(Texts.of("Maintenance plugin command")).extendedDescription(Texts.of("Manage maintenance options for your server.")).child(onCommand, "on").child(offCommand, "off").child(scheduleCommand, "schedule").child(extendCommand, "extend").child(statusCommand, "status").build();

		game.getCommandDispatcher().register(this, maintenanceCommand, "maintenance", "mn");
		//</editor-fold>
	}

	private boolean saveConfig() {
		// Attempt to save any changes to the config
		try {
			if(!config.getParentFile().exists()) {
				config.getParentFile().mkdir();
			}
			configManager.save(rootNode);
			return true;
		} catch(IOException e) {
			logger.error("", e);
		}
		return false;
	}

	private DataSource getDB() throws SQLException {
		if(sql == null) {
			sql = game.getServiceManager().provide(SqlService.class).get();
		}
		return sql.getDataSource("jdbc:h2:./config/maintenance/schedule.db");
	}

	public ArrayList<ScheduleObject> getSchedule(boolean includeCurrent) throws SQLException {
		ArrayList<ScheduleObject> returnResult = new ArrayList<>();

		try(Connection connection = getDB().getConnection()) {
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS schedule(id INTEGER PRIMARY KEY AUTO_INCREMENT, start TEXT, duration TEXT, kickPlayers BOOLEAN, persistRestart BOOLEAN)").execute();
			ResultSet results = connection.prepareStatement("SELECT * FROM schedule").executeQuery();
			results.beforeFirst();
			ScheduleObject item;
			while(results.next()) {
				item = new ScheduleObject(LocalDateTime.parse(results.getString("start"), DateTimeFormatter.ISO_LOCAL_DATE_TIME), !results.getString("duration").equals("")? Duration.parse(results.getString("duration")) : Duration.ofHours(0), results.getBoolean("kickPlayers"), results.getBoolean("persistRestart"), results.getLong("id"));
				if(!item.start.isBefore(LocalDateTime.now()) || (!item.start.plus(item.duration).isBefore(LocalDateTime.now()) && (item.persistRestart || includeCurrent))) {
					returnResult.add(item);
				} else {
					connection.prepareStatement("DELETE FROM schedule WHERE id=?").setInt(1, results.getInt("id"));
				}
			}
			connection.close();
		}
		return returnResult;
	}

	private Long addToSchedule(ScheduleObject item) throws SQLException {
		try(Connection connection = getDB().getConnection()) {
			PreparedStatement statement = connection.prepareStatement("INSERT INTO schedule(start, duration, kickPlayers, persistRestart) VALUES(?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, item.start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
			statement.setString(2, item.duration != null? item.duration.toString() : "");
			statement.setBoolean(3, item.kickPlayers);
			statement.setBoolean(4, item.persistRestart);
			statement.execute();
			ResultSet keys = statement.getGeneratedKeys();
			keys.first();
			return keys.getLong(1);
		}

	}

	public void removeFromSchedule(Integer itemID) throws SQLException {
		try(Connection connection = getDB().getConnection()) {
			PreparedStatement statement = connection.prepareStatement("DELETE FROM schedule WHERE id=?");
			statement.setInt(1, itemID);
			statement.execute();
		}
		for(Task task : game.getScheduler().getScheduledTasks(this)) {
			if(task.getName().contains(String.valueOf(itemID))) {
				task.cancel();
			}
		}
	}

	public void clearSchedule() throws SQLException {
		try(Connection connection = getDB().getConnection()) {
			connection.prepareStatement("DELETE FROM schedule").execute();
		}
		for(Task task : game.getScheduler().getScheduledTasks(this)) {
			task.cancel();
		}
	}

	public boolean inMaintenance() {
		return maintenance;
	}

	public boolean setMaintenance(Status status) {
		return setMaintenance(status, false, false);
	}

	public boolean setMaintenance(Status status, boolean kickPlayers, boolean persistRestart) {
		switch(status) {
			case ON:
				boolean couldPersist = persistRestart;
				if(rootNode != null) {
					rootNode.getNode("persistence").getNode("lastStatus").setValue(true);
					rootNode.getNode("persistence").getNode("persist").setValue(persistRestart);

					if(!saveConfig()) {
						logger.error("Failed to write to config file" + (persistRestart? "; persistance will not work!" : "."));
						couldPersist = false;
					}
				} else {
					logger.error("Failed to read from config file" + (persistRestart? "; persistance will not work!" : "."));
					couldPersist = false;
				}

				logger.info("Enabled " + (persistRestart && couldPersist? "persistent " : "") + "maintenence.");
				game.getEventManager().register(this, PlayerJoinEvent.class, joinHandler);
				maintenance = true;

				if(!kickPlayers) {
					MessageSinks.to(playersWithoutPermission("maintenance.exempt")).sendMessage(Texts.of(onCloseMessage.get("notExempt")));
				}
				MessageSinks.toPermission("maintenance.exempt").sendMessage(Texts.of(onCloseMessage.get("exempt")));


				return couldPersist == persistRestart;
			case OFF:
				boolean success = true;

				if(inMaintenance()) {
					try {
						removeFromSchedule(currentScheduleID.intValue());
					} catch(SQLException e) {
						logger.error("Error with database:", e);
					}
				}

				game.getEventManager().unregister(joinHandler);
				maintenance = false;
				MessageSinks.toAll().sendMessage(Texts.of(onOpenMessage));

				if(rootNode != null) {
					rootNode.getNode("persistence").getNode("lastStatus").setValue(false);
					rootNode.getNode("persistence").getNode("persist").setValue(false);

					if(!saveConfig()) {
						logger.error("Failed to write to the config file; players will be able to join but strange things may happen on restart/reload.");
						success = false;
					}
				} else {
					logger.error("Failed to open the config file; players will be able to join but strange things may happen on restart/reload.");
					success = false;
				}

				logger.info("Disabled maintenence.");
				return success;
			default:
				return false;
		}
	}

	public boolean scheduleMaintenance(ScheduleObject item) {
		// TODO: if given item overlaps with another, merge them
		try {
			if(item.id == null) {
				item.id = addToSchedule(item);
			}

			createTasks(Status.ON, item.kickPlayers, item.persistRestart, item.start, item.id);
			if(item.duration != null) {
				createTasks(Status.OFF, false, false, item.start.plus(item.duration), item.id);
				((PlayerJoinHandler) joinHandler).setOpeningTime(item.start.plus(item.duration).atZone(ZoneId.systemDefault()));
			} else {
				((PlayerJoinHandler) joinHandler).setOpeningTime(null);
			}

			return true;
		} catch(SQLException e) {
			logger.error("Unable to schedule maintenance: ", e);
		}
		return false;
	}

	private void createTasks(Status status, boolean kickPlayers, boolean persistRestart, LocalDateTime start, Long ID) {
		taskBuilder.execute(new Runnable() {
			@Override
			public void run() {
				if(status == Status.ON) {
					currentScheduleID = ID;
				} else {
					currentScheduleID = null;
				}
				setMaintenance(status, kickPlayers, persistRestart);
			}
		}).name("maintenance_action_" + status.toString() + "_" + ID).delay(Math.max(LocalDateTime.now().until(start, ChronoUnit.MILLIS), 0), TimeUnit.MILLISECONDS).submit(this);


		Long warnDelay = LocalDateTime.now().until(start.minus(maintenanceWarnTime), ChronoUnit.MILLIS);
		if(warnDelay > 0) {
			taskBuilder.execute(new Runnable() {
				@Override
				public void run() {
					if(status == Status.ON) {
						MessageSinks.to(playersWithoutPermission("maintenance.exempt")).sendMessage(kickPlayers? Texts.of(beforeCloseMessage.get("notExemptHard")) : Texts.of("notExemptSoft"));
						MessageSinks.toPermission("maintenance.exempt").sendMessage(kickPlayers? Texts.of(beforeCloseMessage.get("exemptHard")) : Texts.of(beforeCloseMessage.get("exemptSoft")));
					} else {
						MessageSinks.toAll().sendMessage(Texts.of(beforeOpenMessage));
					}
				}
			}).name("maintenance_warn_" + status.toString() + "_" + ID).delay(warnDelay, TimeUnit.MILLISECONDS).submit(this);
		}
	}

	private Set<CommandSource> playersWithoutPermission(String permission) {
		Set<CommandSource> recipients = new HashSet<>();
		for(Player player : game.getServer().getOnlinePlayers()) {
			if(!player.hasPermission(permission)) {
				recipients.add(player);
			}
		}
		return recipients;
	}
}
