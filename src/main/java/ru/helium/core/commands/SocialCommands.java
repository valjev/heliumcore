package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import ru.helium.core.database.HeliumDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class SocialCommands implements CommandExecutor {

    private final HeliumCore plugin;
    private final HeliumDatabase database;

    public SocialCommands(HeliumCore plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Эту команду может использовать только игрок.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(Component.text("Используй: /msg <игрок> <сообщение> или /msg enable/disable/volume", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("enable")) {
            database.setMessagesEnabled(player.getUniqueId(), true);
            player.sendMessage(Component.text("Вы включили личные сообщения.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("disable")) {
            database.setMessagesEnabled(player.getUniqueId(), false);
            player.sendMessage(Component.text("Вы отключили личные сообщения.", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("volume")) {
            if (args.length < 2) {
                float currentVolume = database.getMessageVolume(player.getUniqueId());
                int currentPercent = Math.round(currentVolume * 100);
                player.sendMessage(Component.text("Текущая громкость уведомлений: " + currentPercent + "%", NamedTextColor.GREEN));
                player.sendMessage(Component.text("Используйте: /msg volume <0-100>", NamedTextColor.GRAY));
                return true;
            }

            try {
                int percent = Integer.parseInt(args[1]);
                if (percent < 0 || percent > 100) {
                    player.sendMessage(Component.text("Громкость должна быть от 0 до 100", NamedTextColor.RED));
                    return true;
                }
                float volume = percent / 100.0f;
                database.setMessageVolume(player.getUniqueId(), volume);
                player.sendMessage(Component.text("Громкость уведомлений установлена на " + percent + "%", NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Неверное значение громкости. Используйте число от 0 до 100", NamedTextColor.RED));
            }
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Используйте: /msg <игрок> <сообщение>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Игрок не найден или оффлайн.", NamedTextColor.RED));
            return true;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            player.sendMessage(Component.text("Вы не можете отправить сообщение самому себе.", NamedTextColor.RED));
            return true;
        }

        if (!database.isMessagesEnabled(target.getUniqueId())) {
            player.sendMessage(Component.text("У этого игрока отключены личные сообщения.", NamedTextColor.RED));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Component formattedMessageToTarget = Component.text("[Сообщение] ", NamedTextColor.GREEN)
                .append(Component.text("От игрока ", NamedTextColor.GRAY))
                .append(Component.text(player.getName(), NamedTextColor.GREEN))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));

        Component formattedMessageToSender = Component.text("[Сообщение] ", NamedTextColor.GREEN)
                .append(Component.text("Игроку ", NamedTextColor.GRAY))
                .append(Component.text(target.getName(), NamedTextColor.GREEN))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));

        target.sendMessage(formattedMessageToTarget);
        float volume = database.getMessageVolume(target.getUniqueId());
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, volume, 1.0f);
        player.sendMessage(formattedMessageToSender);

        return true;
    }
}