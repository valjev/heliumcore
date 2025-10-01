package ru.helium.core.utils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TelegramSender {

    public static void confirmAuth(String nickname, long telegramId) {
        sendPost("http://127.0.0.1:5001/confirm",
                String.format("{\"nickname\":\"%s\",\"telegram_id\":%d}", escape(nickname), telegramId));
    }

    public static void sendSup(String sender, String message) {
        sendPost("http://127.0.0.1:5001/sup",
                String.format("{\"sender\":\"%s\",\"message\":\"%s\"}", escape(sender), escape(message)));
    }

    private static void sendPost(String urlStr, String json) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            conn.getInputStream().close();
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[HeliumCore] Не удалось отправить: " + e.getMessage());
        }
    }

    private static String escape(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}