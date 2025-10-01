package ru.helium.core.auth;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class AuthHttpServer {
    private final AuthManager authManager;
    private final Gson gson = new Gson();
    private HttpServer server;

    public AuthHttpServer(AuthManager authManager) {
        this.authManager = authManager;
    }

    public void start() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/auth", new AuthHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("[HeliumCore] Auth HTTP server started on port 8080");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("[HeliumCore] Auth HTTP server stopped");
        }
    }

    class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            
            try {
                JsonObject json = gson.fromJson(body, JsonObject.class);
                String nickname = json.get("nickname").getAsString();
                long chatId = json.get("chat_id").getAsLong();
                
                Player player = Bukkit.getPlayerExact(nickname);
                String response;
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(authManager.getPlugin(), () -> {
                        authManager.confirmAuth(player);
                        authManager.frozenPlayers.remove(player.getUniqueId());
                        player.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
                        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        authManager.clearChat(player);
                        authManager.showWelcome(player);
                    });
                    response = "OK";
                } else {
                    response = "Player not online";
                }
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                exchange.sendResponseHeaders(400, -1);
                e.printStackTrace();
            }
        }
    }
} 