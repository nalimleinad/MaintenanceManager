package com.webs.J15t98J.MaintenanceManager.event;

import org.spongepowered.api.entity.player.Player;
import org.spongepowered.api.event.EventHandler;
import org.spongepowered.api.event.entity.player.PlayerJoinEvent;
import org.spongepowered.api.text.Texts;

public class PlayerJoinHandler implements EventHandler<PlayerJoinEvent> {

    private String joinMessage = null;

    public void setJoinMessage(String message) {
        this.joinMessage = message;
    }

    @Override
    public void handle(PlayerJoinEvent event) throws Exception {
        Player player = event.getUser();
        if(!player.hasPermission("maintenance.exempt")) {
            player.kick(Texts.of(joinMessage));
        }
    }
}
