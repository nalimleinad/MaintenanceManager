package com.webs.J15t98J.MaintenanceManager.command;

import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.command.CommandException;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.CommandContext;
import org.spongepowered.api.util.command.spec.CommandExecutor;

import com.google.common.base.Optional;
import com.webs.J15t98J.MaintenanceManager.MaintenanceManager;
import com.webs.J15t98J.MaintenanceManager.Status;

public class OffCommand implements CommandExecutor {

    private MaintenanceManager parent;

    public OffCommand(MaintenanceManager parent) {
        this.parent = parent;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        if(args.getOne("s").equals(Optional.of(true))) {
            // SCHEDULE NYI
            src.sendMessage(Texts.builder("Scheduling system NYI.").color(TextColors.RED).build());
        } else if(parent.inMaintenance()) {
            src.sendMessage(Texts.builder("Maintenance mode ").color(TextColors.WHITE)
                    .append(Texts.builder("disabled").color(TextColors.GREEN).build())
                    .append(Texts.builder(".").color(TextColors.WHITE).build())
                    .build());

            parent.setMaintenance(Status.OFF);
        } else {
            src.sendMessage(Texts.builder("The server isn't in maintenance mode!").color(TextColors.RED).build());
        }
        return CommandResult.success();
    }
}
