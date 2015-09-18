package com.webs.J15t98J.MaintenanceManager.command;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.command.CommandException;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.CommandContext;
import org.spongepowered.api.util.command.spec.CommandExecutor;

import com.google.common.base.Optional;
import com.webs.J15t98J.MaintenanceManager.MaintenanceManager;
import com.webs.J15t98J.MaintenanceManager.ScheduleObject;
import com.webs.J15t98J.MaintenanceManager.Status;

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

        String durationString = null;
        if (args.getOne("duration").isPresent()) {
            durationString = args.getOne("duration").get().toString();
            if (durationString.matches(".+?d")) {
                durationString = "P" + durationString;
            } else if (durationString.matches(".+?d.+")) {
                durationString = "P" + durationString.split("d")[0] + "dT" + durationString.split("d")[1];
            } else {
                durationString = "PT" + durationString;
            }
        }

        try {
            Duration duration = durationString != null ? Duration.parse(durationString) : null;
            LocalDateTime startTime = args.getOne("time").isPresent() ? LocalDateTime.parse((args.getOne("date").isPresent() ? args.getOne("date").get().toString() : LocalDate.now().toString()) + "T" + args.getOne("time").get().toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;

            if (startTime != null) {
                if(startTime.isAfter(LocalDateTime.now())) {
                    parent.scheduleMaintenance(new ScheduleObject(startTime, duration, shouldKick, shouldPersist));

                    boolean startingSameDay = startTime.truncatedTo(ChronoUnit.DAYS).isEqual(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS));
                    src.sendMessage(Texts.builder(shouldPersist ? "Persistent " : "").color(TextColors.GOLD)
                            .append(Texts.builder((shouldPersist ? "m" : "M") + "aintenance scheduled to start " + (startingSameDay ? "at " : "")).color(TextColors.WHITE).build())
                            .append(Texts.builder((startingSameDay ? "" : startTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + " at ") + startTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_TIME)).color(TextColors.GOLD).build())
                            .append(Texts.builder(duration != null ? ", lasting until " : "").color(TextColors.WHITE).build())
                            .append(Texts.builder(duration != null ? ((startTime.truncatedTo(ChronoUnit.DAYS).isEqual(startTime.plus(duration).truncatedTo(ChronoUnit.DAYS)) ? "" : startTime.plus(duration).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + " at ") + startTime.plus(duration).format(DateTimeFormatter.ISO_LOCAL_TIME)) : "").build())
                            .append(Texts.builder(shouldKick ? "; all non-exempt players will be kicked." : ".").color(TextColors.WHITE).build())
                            .build());
                } else {
                    src.sendMessage(Texts.builder("The specified start point has already passed.").color(TextColors.RED).build());
                    return CommandResult.empty();
                }
            } else if(!parent.inMaintenance()) {
                LocalDateTime start = null;
                LocalDateTime end = null;
                if(duration != null) {
                    start = LocalDateTime.now();
                    end = LocalDateTime.now().plus(duration);
                }

                if(duration != null? parent.scheduleMaintenance(new ScheduleObject(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), duration, shouldKick, shouldPersist)) : parent.setMaintenance(Status.ON, shouldKick, shouldPersist)) {
                    src.sendMessage(Texts.builder(shouldPersist ? "Persistent " : "").color(TextColors.GOLD) // First two builders tell the user if it's persistent
                            .append(Texts.builder((shouldPersist ? "m" : "M") + "aintenance mode ").color(TextColors.WHITE).build())
                            .append(Texts.builder("enabled").color(TextColors.RED).build()) // Next one just says enabled
                            .append(Texts.builder(duration != null ? " until " : "").color(TextColors.WHITE).build()) // These two will tell the user when then maintenance will end, if they specified a duration
                            .append(Texts.builder(duration != null ? (start.truncatedTo(ChronoUnit.DAYS).isEqual(end.truncatedTo(ChronoUnit.DAYS)) ? "" : end.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + " at ") +
                                    end.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_TIME) : "").color(TextColors.GOLD).build())
                            .append(Texts.builder(shouldKick? "; all non-exempt players kicked!" : ".").color(TextColors.WHITE).build()) // Finally, this one informs the player if people will be kicked
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
        } catch(DateTimeParseException e) {
            src.sendMessage(Texts.builder("\"" + e.getParsedString().replaceAll("[PpTt]", "") + "\" is not correctly formatted.").color(TextColors.RED).build());
            src.sendMessage(Texts.builder("Format it like these: " + (e.getStackTrace()[0].toString().contains("Duration") ? "6d5h3m2s / 1h32s" : "12:05:30 / 5:04:06")).color(TextColors.RED).build());
            return CommandResult.empty();
        }
        return CommandResult.success();
    }
}
