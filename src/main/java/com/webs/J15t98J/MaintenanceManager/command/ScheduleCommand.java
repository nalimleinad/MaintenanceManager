package com.webs.J15t98J.MaintenanceManager.command;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.command.CommandException;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.CommandContext;
import org.spongepowered.api.util.command.spec.CommandExecutor;

import com.webs.J15t98J.MaintenanceManager.MaintenanceManager;
import com.webs.J15t98J.MaintenanceManager.ScheduleObject;
import com.webs.J15t98J.MaintenanceManager.Status;

public class ScheduleCommand implements CommandExecutor {

    private MaintenanceManager parent;

    public ScheduleCommand(MaintenanceManager parent) {
        this.parent = parent;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        if (args.getOne("operation").isPresent()) {
            if (args.getOne("all").isPresent()) {
                try {
                    parent.clearSchedule();
                    src.sendMessage(Texts.builder("All maintenance periods deleted.").color(TextColors.GREEN).build());
                } catch (SQLException e) {
                    src.sendMessage(Texts.builder("Something went wrong! You should probably check the logs...").color(TextColors.RED).build());
                    parent.logger.error("Could not delete all maintenance periods:", e);
                }
            } else {
                int itemID = (int) args.getOne("ID").get();
                try {
                    parent.removeFromSchedule(itemID);
                    if(parent.currentScheduleID == itemID) {
	                    parent.currentScheduleID = null;
	                    parent.setMaintenance(Status.OFF);
                    }
                    src.sendMessage(Texts.builder("Maintenance period #" + itemID + " deleted.").color(TextColors.GREEN).build());
                } catch (SQLException e) {
                    src.sendMessage(Texts.builder("Something went wrong! You should probably check the logs...").color(TextColors.RED).build());
                    parent.logger.error("Could not delete maintenance period #" + itemID + ":", e);
                }
            }
        } else {
            List<Text> content = new ArrayList<>();
            Text placeholder = Texts.builder("No scheduled maintenance.").color(TextColors.GRAY).build();
            try {
                for (ScheduleObject item : parent.getSchedule(true)) {
                    String duration = "; no duration set";
                    if(item.duration != null && item.duration.compareTo(Duration.ofSeconds(0)) == 1) {
                        Long days = item.duration.toDays();
                        Long hours = item.duration.toHours() % 24;
                        Long minutes = item.duration.toMinutes() % 60;
                        Long seconds = item.duration.getSeconds() % 60;
                        duration = ", for " + (days > 0 ? days + "d" : "") + (hours > 0 ? hours + "h" : "") + (minutes > 0 ? minutes + "m" : "") + (seconds > 0 ? seconds + "s" : "");
                    }
                    content.add(Texts.builder("#" + item.id + ": " + item.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + " @ " + item.start.format(DateTimeFormatter.ISO_LOCAL_TIME) +
                            duration).color(item.start.isBefore(LocalDateTime.now()) && item.start.plus(item.duration).isAfter(LocalDateTime.now()) ? TextColors.GOLD : TextColors.WHITE).build());
                }
                if(content.isEmpty()) {
                    content.add(placeholder);
                }

                parent.game.getServiceManager().provide(PaginationService.class).get().builder()
                        .title(Texts.builder("Scheduled maintenance periods").color(TextColors.DARK_GREEN).build())
                        .paddingString("#")
                        .header(Texts.builder("Number of scheduled periods: " + (content.get(0).equals(placeholder)? 0 : content.size())).color(TextColors.GREEN).build())
                        .contents(content)
                        .sendTo(src);
            } catch(SQLException e) {
                src.sendMessage(Texts.builder("Could not access database!").color(TextColors.RED).build());
                parent.logger.error("Error with database:", e);
            }
        }

        return CommandResult.success();
    }
}
