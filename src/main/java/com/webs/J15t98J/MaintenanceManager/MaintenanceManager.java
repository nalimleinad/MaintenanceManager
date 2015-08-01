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
import org.spongepowered.api.entity.player.Player;
import org.spongepowered.api.event.EventHandler;
import org.spongepowered.api.event.Subscribe;
import org.spongepowered.api.event.entity.player.PlayerJoinEvent;
import org.spongepowered.api.event.state.InitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.config.DefaultConfig;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.sink.MessageSinks;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.GenericArguments;
import org.spongepowered.api.util.command.spec.CommandSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

//TODO: schedule command and broadcasts to players 15m before scheduled downtime
//TODO: metrics
//TODO: maintenance_start/maintenance_end events for other plugins to hook
//TODO: possibly refactor commands into one executor, and fix bindWith() flag calls so that /help displays it properly (but Sponge doesn't seem to display plugin commands in /help yet)

@Plugin(id = "maintenance", name = "MaintenanceManager", version = "0.1")
public class MaintenanceManager {

    @Inject public Game game;
    @Inject private Logger logger;
    @Inject @DefaultConfig(sharedRoot = true) private ConfigurationLoader<CommentedConfigurationNode> configManager;

    private EventHandler<PlayerJoinEvent> joinHandler = new PlayerJoinHandler();
    private OnCommand onCMD = new OnCommand(this);

    private boolean maintenance;
    private ConfigurationNode rootNode;

    private HashMap<String, String> beforeCloseMessage = new HashMap<>();
    private HashMap<String, String> onCloseMessage = new HashMap<>();
    private String beforeOpenMessage;
    private String onOpenMessage;

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
            String kickMessage = messages.getNode("kick").getString("The server is closing for maintenance. Please come back later!");
            String joinMessage = messages.getNode("join").getString("The server is closed for maintenance. Please come back later!");

            ConfigurationNode announcements = rootNode.getNode("announcement");
            String warnTime = announcements.getNode("warnTime").getString("0:15:0");
            ConfigurationNode beforeCloseNode = announcements.getNode("beforeClose");
            beforeCloseMessage.put("exemptSoft", beforeCloseNode.getNode("exemptSoft").getString("The server will close for maintenance in %t, but as an exempt player you'll still be able to connect after this time."));
            beforeCloseMessage.put("exemptHard", beforeCloseNode.getNode("exemptHard").getString("The server will close for maintenance in %t, but as an exempt player you won't be kicked or prevented from joining."));
            beforeCloseMessage.put("notExemptSoft", beforeCloseNode.getNode("notExemptSoft").getString("The server will close for maintenance in %t. You won't be kicked, but you won't be able to re-connect after this time."));
            beforeCloseMessage.put("notExemptHard", beforeCloseNode.getNode("notExemptHard").getString("The server will close for maintenance in %t. You will be kicked and prevented from re-connecting until the server re-opens."));
            ConfigurationNode onCloseNode = announcements.getNode("onClose");
            onCloseMessage.put("exempt", onCloseNode.getNode("exempt").getString("The server is now closed for maintenance."));
            onCloseMessage.put("notExempt", onCloseNode.getNode("notExempt").getString("The server is now closed for maintenance. If you disconnect now, you won't be able to reconnect!"));
            beforeOpenMessage = announcements.getNode("beforeOpen").getString("The server will open in %t.");
            onOpenMessage = announcements.getNode("onOpen").getString("The server is now open.");

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
            messages.getNode("kick").setValue(kickMessage);
            messages.getNode("join").setValue(joinMessage);

            announcements.getNode("warnTime").setValue(warnTime);
            announcements.getNode("beforeOpen").setValue(beforeOpenMessage);
            announcements.getNode("onOpen").setValue(onOpenMessage);

            beforeCloseNode.getNode("exemptSoft").setValue(beforeCloseMessage.get("exemptSoft"));
            beforeCloseNode.getNode("exemptHard").setValue(beforeCloseMessage.get("exemptHard"));
            beforeCloseNode.getNode("notExemptSoft").setValue(beforeCloseMessage.get("notExemptSoft"));
            beforeCloseNode.getNode("notExemptHard").setValue(beforeCloseMessage.get("notExemptHard"));

            onCloseNode.getNode("exempt").setValue(onCloseMessage.get("exempt"));
            onCloseNode.getNode("notExempt").setValue(onCloseMessage.get("notExempt"));

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

                logger.info("Enabled " + (persistRestart && couldPersist ? "persistent " : "") + "maintenence.");
                game.getEventManager().register(this, PlayerJoinEvent.class, joinHandler);
                maintenance = true;

                if(!kickPlayers) {
                    Set<CommandSource> recipients = new HashSet<CommandSource>();
                    for(Player player : game.getServer().getOnlinePlayers()) {
                        if(!player.hasPermission("maintenance.exempt")) {
                            recipients.add(player);
                        }
                    }

                    MessageSinks.to(recipients).sendMessage(Texts.of(onCloseMessage.get("notExempt")));
                }

                MessageSinks.toPermission("maintenance.exempt").sendMessage(Texts.of(onCloseMessage.get("exempt")));


                return couldPersist == persistRestart;
            case SCHEDULED:
                // NYI
                return true;
            case OFF:
                boolean success = true;

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
}
