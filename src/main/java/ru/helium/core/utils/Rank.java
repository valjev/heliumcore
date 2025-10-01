package ru.helium.core.utils;

public enum Rank {
    PLAYER,
    MODERATOR,
    ADMIN;

    public String getPrefix() {
        return switch (this) {
            case ADMIN -> "§c«Админ» §f" ;
            case MODERATOR -> "§b«Страж» §f";
            default -> "§7«Игрок» §f";
        };
    }

    public String getRussianName() {
        return switch (this) {
            case ADMIN -> "админ";
            case MODERATOR -> "страж";
            default -> "игрок";
        };
    }
}