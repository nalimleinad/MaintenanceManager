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
        if(args.getOne("s").equals(Optional.of(true))) {
            // SCHEDULE NYI
            src.sendMessage(Texts.builder("Scheduling system NYI.").color(TextColors.RED).build());
        } else if(!parent.inMaintenance()) {
            boolean shouldKick = args.getOne("k").equals(Optional.of(true));
            boolean shouldPersist = args.getOne("p").equals(Optional.of(true));

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
