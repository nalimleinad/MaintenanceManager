package com.webs.J15t98J.MaintenanceManager.event;

import org.spongepowered.api.entity.player.Player;
import org.spongepowered.api.event.EventHandler;
import org.spongepowered.api.event.entity.player.PlayerJoinEvent;
import org.spongepowered.api.text.Texts;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class PlayerJoinHandler implements EventHandler<PlayerJoinEvent> {

    private String joinMessageWithDuration = null;
    private String joinMessageIndefinite = null;
    private ZonedDateTime openingTime = null;

    public void setJoinMessageWithDuration(String message) {
        this.joinMessageWithDuration = message;
    }

    public void setJoinMessageIndefinite(String message) {
        this.joinMessageIndefinite = message;
    }

    public void setOpeningTime(ZonedDateTime time) {
        this.openingTime = time;
    }

    @Override
    public void handle(PlayerJoinEvent event) throws Exception {
        Player player = event.getUser();
        if(!player.hasPermission("maintenance.exempt")) {
            boolean opensToday = false;
            if(openingTime != null) {
                 opensToday = openingTime.truncatedTo(ChronoUnit.DAYS).isEqual(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS));
            }
            String message = openingTime != null? joinMessageWithDuration.replace("%o", openingTime.format(DateTimeFormatter.ofPattern(opensToday? "HH':'mm z" : "dd'/'MM'/'yyyy 'at' HH':'mm z"))) : joinMessageIndefinite;
            player.kick(Texts.of(message));
        }
    }
}
