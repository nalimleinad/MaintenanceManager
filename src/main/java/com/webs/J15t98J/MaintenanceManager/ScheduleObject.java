package com.webs.J15t98J.MaintenanceManager;

import java.time.Duration;
import java.time.LocalDateTime;

public class ScheduleObject {

    public Long id;
    public LocalDateTime start;
    public Duration duration;
    public boolean kickPlayers;
    public boolean persistRestart;

    public ScheduleObject(LocalDateTime start, Duration duration, boolean kickPlayers, boolean persistRestart, Long itemID) {
        this.id = itemID;
        this.start = start;
        this.duration = duration;
        this.kickPlayers = kickPlayers;
        this.persistRestart = persistRestart;
    }

    public ScheduleObject(LocalDateTime start, Duration duration, boolean kickPlayers, boolean persistRestart) {
        this.start = start;
        this.duration = duration;
        this.kickPlayers = kickPlayers;
        this.persistRestart = persistRestart;
    }
}
