package com.webs.J15t98J.MaintenanceManager;

import java.time.Duration;
import java.time.LocalDateTime;

public class ScheduleObject {

    public LocalDateTime start;
    public Duration duration;
    public boolean kickPlayers;
    public boolean persistRestart;

    public ScheduleObject(LocalDateTime start, Duration duration, boolean kickPlayers, boolean persistRestart) {
        this.start = start;
        this.duration = duration;
        this.kickPlayers = kickPlayers;
        this.persistRestart = persistRestart;
    }
}
