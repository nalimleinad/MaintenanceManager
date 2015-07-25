package com.webs.J15t98J.MaintenanceManager;

import com.google.inject.Inject;
import com.webs.J15t98J.MaintenanceManager.command.OffCommand;
import com.webs.J15t98J.MaintenanceManager.command.OnCommand;
import com.webs.J15t98J.MaintenanceManager.event.PlayerJoinHandler;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.event.EventHandler;
import org.spongepowered.api.event.Subscribe;
import org.spongepowered.api.event.entity.player.PlayerJoinEvent;
import org.spongepowered.api.event.state.InitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.config.DefaultConfig;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.util.command.args.GenericArguments;
import org.spongepowered.api.util.command.spec.CommandSpec;

import java.io.IOException;


//TODO: Schedule command
//TODO: maintenance_start/maintenance_end events for other plugins to hook
//TODO: Possibly refactor commands into one executor, and fix bindWith() flag calls so that /help displays it properly (but Sponge doesn't seem to display plugin commands in /help yet)

@Plugin(id = "maintenance", name = "MaintenanceManager", version = "0.1")
public class MaintenanceManager {

    @Inject public Game game;
    @Inject private Logger logger;
    @Inject @DefaultConfig(sharedRoot = true) private ConfigurationLoader<CommentedConfigurationNode> configManager;

    private EventHandler<PlayerJoinEvent> joinHandler = new PlayerJoinHandler();
    private OnCommand onCMD = new OnCommand(this);

    private boolean maintenance;
    private ConfigurationNode rootNode;

    @Subscribe
    public void onInitialization(InitializationEvent event) {
        // Attempt to open the config file and grab a root node handle
        try {
            rootNode = configManager.load();
        } catch (IOException e) {
            logger.error("Failed to read from config file:", e);
            rootNode = null;
        }

        if(rootNode != null) {
            // Retrieve any set values, or use defaults if non-existant
            ConfigurationNode messages = rootNode.getNode("message");
            String kickMessage = messages.getNode("kickMessage").getString("The server is closing for maintenance. Please come back later!");
            String joinMessage = messages.getNode("joinMessage").getString("The server is closed for maintenance. Please come back later!");
            boolean warnPlayers = messages.getNode("warnPlayers").getBoolean(true);
            String warnTime = messages.getNode("warnTime").getString("0:15:0");

            ConfigurationNode persistence = rootNode.getNode("persistence");
            boolean shouldPersist = persistence.getNode("persist").getBoolean(false);
            boolean lastStatus = persistence.getNode("lastStatus").getBoolean(false);

            boolean shouldShare = rootNode.getNode("notifyPlugins").getBoolean(true);

            // Distribute config values to relevant classes
            if(lastStatus && shouldPersist) {
                setMaintenance(Status.ON, false, true);
            }
            onCMD.setKickMessage(kickMessage);
            ((PlayerJoinHandler)joinHandler).setJoinMessage(joinMessage);

            // Overwrite all values (all pre-existing values stay the same, the rest are created and set to default)
            messages.getNode("kickMessage").setValue(kickMessage);
            messages.getNode("joinMessage").setValue(joinMessage);
            messages.getNode("warnPlayers").setValue(warnPlayers);
            messages.getNode("warnTime").setValue(warnTime);

            persistence.getNode("persist").setValue(shouldPersist);
            persistence.getNode("lastStatus").setValue(lastStatus);

            rootNode.getNode("notifyPlugins").setValue(shouldShare);
        }

        saveConfig();

        // Register commands
        CommandSpec onCommand = CommandSpec.builder()
                .description(Texts.of("Activate maintenance mode"))
                .permission("maintenance.on")
                .executor(onCMD)
                .arguments(GenericArguments.flags()
                        .permissionFlag("maintenance.kick", "k")
                        .permissionFlag("maintenance.persist", "p")
                        .permissionFlag("maintenance.schedule", "s")
                        .buildWith(GenericArguments.none()))
                .build();

        CommandSpec offCommand = CommandSpec.builder()
                .description(Texts.of("Deactivate maintenance mode"))
                .permission("maintenance.off")
                .executor(new OffCommand(this))
                .arguments(GenericArguments.flags()
                        .permissionFlag("maintenance.schedule", "s")
                        .buildWith(GenericArguments.none()))
                .build();

        CommandSpec maintenanceCommand = CommandSpec.builder()
                .description(Texts.of("Maintenance plugin command"))
                .extendedDescription(Texts.of("Manage maintenance options for your server."))
                .child(onCommand, "on")
                .child(offCommand, "off")
                .build();

        game.getCommandDispatcher().register(this, maintenanceCommand, "maintenance", "mn");
    }

    private boolean saveConfig() {
        // Attempt to save any changes to the config
        try {
            configManager.save(rootNode);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save to config file:", e);
        }
        return false;
    }

    public boolean inMaintenance() {
        return maintenance;
    }

    public void setMaintenance(Status status) {
        setMaintenance(status, false, false);
    }

    public void setMaintenance(Status status, boolean kickPlayers, boolean persistRestart) {
        switch(status) {
            case ON:
                game.getEventManager().register(this, PlayerJoinEvent.class, joinHandler);
                maintenance = true;
                if(!(rootNode == null)) {
                    rootNode.getNode("persistence").getNode("lastStatus").setValue(true);
                    rootNode.getNode("persistence").getNode("persist").setValue(persistRestart);
                    if(saveConfig()) {
                        logger.info("Enabled " + (persistRestart ? "persistent " : "") + "maintenence.");
                    } else {
                        logger.error("Failed to activate persistence.");
                    }
                } else {
                    logger.error("Failed to activate persistence - could not open the config file.");
                }
                return;
            case SCHEDULED:
                // NYI
                return;
            case OFF:
                game.getEventManager().unregister(joinHandler);
                maintenance = false;
                if(!(rootNode == null)) {
                    rootNode.getNode("persistence").getNode("lastStatus").setValue(false);
                    rootNode.getNode("persistence").getNode("persist").setValue(false);
                    if(saveConfig()) {
                        logger.info("Disabled maintenence.");
                    } else {
                        logger.error("Failed to disable persistence.");
                    }
                } else {
                    logger.error("Failed to disable persistence - could not open the config file.");
                }
        }
    }
}
