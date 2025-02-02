/*
 * Copyright (c) 2025 Mazey-Jessica Emily Twilight
 * Copyright (c) 2025 UnifiedGaming Systems Ltd (Company Number: 16108983)
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.example.discordwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DiscordWhitelistPlugin extends JavaPlugin {

    private Connection connection;
    private JDA jda;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDatabase();
        getLogger().info("WhitelistPlugin Enabling...");

        String token = getConfig().getString("discord.token");
        if (token == null || token.isEmpty()) {
            getLogger().severe("Discord token is not set in config.yml!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(new DiscordListener())
                    .setAutoReconnect(true)
                    .build();

            jda.awaitReady();
            getLogger().info("Discord bot connected successfully!");
        } catch (InterruptedException e) {
            getLogger().severe("Failed to connect Discord bot: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        closeDatabaseConnection();
        if (jda != null) {
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                getLogger().warning("Discord bot shutdown interrupted: " + e.getMessage());
                jda.shutdownNow();
            }
        }
        getLogger().info("WhitelistPlugin Disabled");
    }

    private void setupDatabase() {
        String host = getConfig().getString("mysql.host");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");
        int port = getConfig().getInt("mysql.port");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
        try {
            connection = DriverManager.getConnection(url, username, password);
            getLogger().info("Connected to MySQL database!");
            initializeTables();
        } catch (SQLException e) {
            getLogger().severe("Could not connect to MySQL database: " + e.getMessage());
        }
    }

    private void initializeTables() {
        String sql = "CREATE TABLE IF NOT EXISTS discord_whitelist ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "username VARCHAR(50) NOT NULL,"
                + "discord_id VARCHAR(20) NOT NULL,"
                + "discord_username VARCHAR(50) NOT NULL,"
                + "linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.execute();
            getLogger().info("Whitelist table initialized.");
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize tables: " + e.getMessage());
        }
    }

    private void closeDatabaseConnection() {
        if (connection != null) {
            try {
                connection.close();
                getLogger().info("Database connection closed.");
            } catch (SQLException e) {
                getLogger().warning("Failed to close database connection: " + e.getMessage());
            }
        }
    }

    private boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public Connection getDatabaseConnection() {
        if (!isConnected()) {
            setupDatabase();
        }
        return connection;
    }

    public class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;

            Message message = event.getMessage();
            String content = message.getContentRaw();

            if (content.startsWith("!whois")) {
                handleWhoisCommand(event, content);
            } else if (content.startsWith("!whomc")) {
                handleWhomcCommand(event, content);
            } else if (content.startsWith("!whitelist")) {
                handleWhitelistCommand(event, content);
            } else if (content.startsWith("!unwhitelist")) {
                handleUnwhitelistCommand(event, content);
            } else if (content.startsWith("!listwhitelist")) {
                handleListWhitelistCommand(event);
            }
        }

        private void handleWhoisCommand(MessageReceivedEvent event, String content) {
            String[] args = content.split(" ");
            if (args.length != 2) {
                event.getChannel().sendMessage("Usage: !whois @DiscordUser").queue();
                return;
            }

            String discordId = args[1].replaceAll("[^0-9]", "");

            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "SELECT username FROM discord_whitelist WHERE discord_id = ?")) {
                stmt.setString(1, discordId);
                ResultSet rs = stmt.executeQuery();
                StringBuilder response = new StringBuilder("Minecraft accounts linked to <@" + discordId + ">: ");

                while (rs.next()) {
                    response.append(rs.getString("username")).append(", ");
                }

                if (response.toString().endsWith(", ")) {
                    response.setLength(response.length() - 2);
                } else {
                    response.append("None");
                }

                event.getChannel().sendMessage(response.toString()).queue();
            } catch (SQLException e) {
                event.getChannel().sendMessage("Failed to fetch data. Please try again later.").queue();
                getLogger().severe("MySQL Error: " + e.getMessage());
            }
        }

        private void handleWhomcCommand(MessageReceivedEvent event, String content) {
            String[] args = content.split(" ");
            if (args.length != 2) {
                event.getChannel().sendMessage("Usage: !whomc <MinecraftUsername>").queue();
                return;
            }

            String username = args[1];

            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "SELECT discord_username FROM discord_whitelist WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String discordUsername = rs.getString("discord_username");
                    event.getChannel().sendMessage("\"" + discordUsername + " is linked to Minecraft username " + username).queue();
                } else {
                    event.getChannel().sendMessage("No Discord user is linked to Minecraft username " + username).queue();
                }
            } catch (SQLException e) {
                event.getChannel().sendMessage("Failed to fetch data. Please try again later.").queue();
                getLogger().severe("MySQL Error: " + e.getMessage());
            }
        }

        private void handleWhitelistCommand(MessageReceivedEvent event, String content) {
            String[] args = content.split(" ");
            if (args.length != 2) {
                event.getChannel().sendMessage("Usage: !whitelist <playerName>").queue();
                return;
            }

            String playerName = args[1];
            String discordId = event.getAuthor().getId();
            String discordUsername = event.getAuthor().getAsTag();

            Bukkit.getScheduler().runTask(DiscordWhitelistPlugin.this, () -> {
                try {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
                    UUID uuid = player.getUniqueId();

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + playerName);

                    try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                            "REPLACE INTO discord_whitelist (uuid, discord_id, discord_username, username) VALUES (?, ?, ?, ?)") ) {
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, discordId);
                        stmt.setString(3, discordUsername);
                        stmt.setString(4, playerName);
                        stmt.executeUpdate();
                    }

                    event.getChannel().sendMessage("Player " + playerName + " has been added to the whitelist!").queue();
                } catch (SQLException e) {
                    getLogger().warning("MySQL Error: " + e.getMessage());
                    event.getChannel().sendMessage("Failed to add player to whitelist.").queue();
                }
            });
        }

        private void handleUnwhitelistCommand(MessageReceivedEvent event, String content) {
            String[] args = content.split(" ");
            if (args.length != 2) {
                event.getChannel().sendMessage("Usage: !unwhitelist <playerName>").queue();
                return;
            }

            String playerName = args[1];
            String discordId = event.getAuthor().getId();
            String adminDiscordId = getConfig().getString("discord.admin_id");

            Bukkit.getScheduler().runTask(DiscordWhitelistPlugin.this, () -> {
                try {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
                    UUID uuid = player.getUniqueId();

                    try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                            "SELECT discord_id FROM discord_whitelist WHERE uuid = ?")) {
                        stmt.setString(1, uuid.toString());
                        ResultSet rs = stmt.executeQuery();

                        if (rs.next() && (rs.getString("discord_id").equals(discordId) || discordId.equals(adminDiscordId))) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + playerName);
                            event.getChannel().sendMessage("Player \"" + playerName + " has been removed from the whitelist.").queue();
                        } else {
                            event.getChannel().sendMessage("You do not have permission to unwhitelist this player.").queue();
                        }
                    }
                } catch (SQLException e) {
                    getLogger().warning("Error removing player: " + e.getMessage());
                }
            });
        }

        private void handleListWhitelistCommand(MessageReceivedEvent event) {
            Bukkit.getScheduler().runTask(DiscordWhitelistPlugin.this, () -> {
                StringBuilder response = new StringBuilder("Whitelisted players:\n\"");
                for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
                    response.append(player.getName()).append("\n");
                }

                if (response.toString().equals("Whitelisted players:\n")) {
                    response.append("No players are currently whitelisted.");
                }

                event.getChannel().sendMessage(response.toString()).queue();
            });
        }
    }
}
