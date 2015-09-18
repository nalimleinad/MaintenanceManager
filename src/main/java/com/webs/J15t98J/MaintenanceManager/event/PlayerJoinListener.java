package com.webs.J15t98J.MaintenanceManager.event;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.text.Texts;

public class PlayerJoinListener implements EventListener<ClientConnectionEvent.Join> {

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
    public void handle(ClientConnectionEvent.Join event) throws Exception {
        Player player = (Player) event.getCause();
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
