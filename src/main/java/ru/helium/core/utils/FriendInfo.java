package ru.helium.core.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public class FriendInfo {

    private final UUID uuid;
    private final long addedAt;

    public FriendInfo(UUID uuid) {
        this.uuid = uuid;
        this.addedAt = System.currentTimeMillis();
    }

    public FriendInfo(UUID uuid, long addedAt) {
        this.uuid = uuid;
        this.addedAt = addedAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public String getAddedAtMoscow() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        return sdf.format(new Date(addedAt)) + " МСК";
    }
}