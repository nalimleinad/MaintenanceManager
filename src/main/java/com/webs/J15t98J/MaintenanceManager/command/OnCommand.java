package com.webs.J15t98J.MaintenanceManager.command;

import com.google.common.base.Optional;
import com.webs.J15t98J.MaintenanceManager.MaintenanceManager;
import com.webs.J15t98J.MaintenanceManager.Status;
import org.spongepowered.api.entity.player.Player;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.command.CommandException;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.CommandContext;
import org.spongepowered.api.util.command.spec.CommandExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class OnCommand implements CommandExecutor {

    private MaintenanceManager parent;
    private String kickMessage;

    public OnCommand(MaintenanceManager parent) {
        this.parent = parent;
    }

    public void setKickMessage(String message) {
        this.kickMessage = message;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        boolean shouldKick = args.getOne("k").equals(Optional.of(true));
        boolean shouldPersist = args.getOne("p").equals(Optional.of(true));

        if(args.getOne("s").isPresent() || args.getOne("d").isPresent()) {
            LocalDateTime startTime = LocalDateTime.parse(args.getOne("s").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Duration duration = Duration.parse(args.getOne("d").toString());
            parent.setMaintenance(Status.SCHEDULED, shouldKick, shouldPersist, startTime, duration);
        } else if(!parent.inMaintenance()) {
            if(parent.setMaintenance(Status.ON, shouldKick, shouldPersist)) {
                src.sendMessage(Texts.builder(shouldPersist ? "Persistent " : "").color(TextColors.GOLD)
                        .append(Texts.builder((shouldPersist ? "m" : "M") + "aintenance mode ").color(TextColors.WHITE).build())
                        .append(Texts.builder("enabled").color(TextColors.RED).build())
                        .append(Texts.builder(shouldKick ? "; all non-exempt players kicked!" : ".").color(TextColors.WHITE).build())
                        .build());

                if(shouldKick) {
                    for (Player player : parent.game.getServer().getOnlinePlayers()) {
                        if (player != null && !player.hasPermission("maintenance.exempt")) {
                            player.kick(Texts.of(kickMessage));
                        }
                    }
                }
            } else {
                src.sendMessage(Texts.builder("Something went wrong! You should probably check the logs...").color(TextColors.RED).build());
            }

        } else {
            src.sendMessage(Texts.builder("The server is already in maintenance mode!").color(TextColors.RED).build());
        }

        return CommandResult.success();
    }
}
