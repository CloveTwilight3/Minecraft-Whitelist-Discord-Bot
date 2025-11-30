/*
 * Copyright (c) 2025 Clove Twilight
 * Licensed under the MIT License
 * WingSync
 */

package uk.co.clovetwilight3.wingsync;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import uk.co.clovetwilight3.wingsync.listeners.CloveLibListener;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class Main extends JavaPlugin {

    private Connection connection;
    private JDA jda;
    private boolean useMysql;
    private File dataFile;
    private Map<String, PlayerData> playerDataMap = new HashMap<>();
    private Gson gson = new Gson();

    // Inner class to store player data
    public static class PlayerData {
        public String uuid;
        public String username;
        public String discordId;
        public String discordUsername;
        public long linkedAt;

        public PlayerData(String uuid, String username, String discordId, String discordUsername) {
            this.uuid = uuid;
            this.username = username;
            this.discordId = discordId;
            this.discordUsername = discordUsername;
            this.linkedAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Check if MySQL is enabled
        useMysql = getConfig().getBoolean("mysql.enabled", false);

        if (useMysql) {
            setupDatabase();
            getLogger().info("MySQL database enabled and configured.");
        } else {
            setupFileStorage();
            getLogger().info("File-based storage enabled. MySQL disabled.");
        }

        getLogger().info("WingSync Enabling...");

        // Register CloveLib listener for ban events
        if (Bukkit.getPluginManager().getPlugin("CloveLib") != null) {
            getServer().getPluginManager().registerEvents(new CloveLibListener(this), this);
            getLogger().info("CloveLib integration enabled - bans will auto-remove from whitelist.");
        } else {
            getLogger().warning("CloveLib not found! Ban integration will not work.");
        }

        String token = getConfig().getString("discord.token");
        if (token == null || token.isEmpty()) {
            getLogger().severe("Discord token is not set in config.yml!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new DiscordSlashCommandListener())
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
        if (useMysql) {
            closeDatabaseConnection();
        } else {
            saveFileData();
        }

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
        getLogger().info("WingSync Disabled");
    }

    private void setupFileStorage() {
        dataFile = new File(getDataFolder(), "playerdata.json");
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        loadFileData();
    }

    private void loadFileData() {
        if (!dataFile.exists()) {
            return;
        }

        try {
            String json = Files.readString(dataFile.toPath());
            Type type = new TypeToken<Map<String, PlayerData>>(){}.getType();
            Map<String, PlayerData> loadedData = gson.fromJson(json, type);
            if (loadedData != null) {
                playerDataMap = loadedData;
            }
            getLogger().info("Loaded " + playerDataMap.size() + " player records from file.");
        } catch (IOException e) {
            getLogger().warning("Failed to load player data from file: " + e.getMessage());
        }
    }

    private void saveFileData() {
        try {
            String json = gson.toJson(playerDataMap);
            try (FileWriter writer = new FileWriter(dataFile)) {
                writer.write(json);
            }
            getLogger().info("Saved " + playerDataMap.size() + " player records to file.");
        } catch (IOException e) {
            getLogger().warning("Failed to save player data to file: " + e.getMessage());
        }
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
            getLogger().info("Falling back to file-based storage...");
            useMysql = false;
            setupFileStorage();
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
        if (useMysql && !isConnected()) {
            setupDatabase();
        }
        return connection;
    }

    // Data access methods that work with both storage types
    private void storePlayerData(String uuid, String username, String discordId, String discordUsername) {
        if (useMysql) {
            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "REPLACE INTO discord_whitelist (uuid, discord_id, discord_username, username) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, uuid);
                stmt.setString(2, discordId);
                stmt.setString(3, discordUsername);
                stmt.setString(4, username);
                stmt.executeUpdate();
            } catch (SQLException e) {
                getLogger().warning("MySQL Error: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        } else {
            playerDataMap.put(uuid, new PlayerData(uuid, username, discordId, discordUsername));
            saveFileData();
        }
    }

    private void removePlayerData(String uuid) {
        if (useMysql) {
            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "DELETE FROM discord_whitelist WHERE uuid = ?")) {
                stmt.setString(1, uuid);
                stmt.executeUpdate();
            } catch (SQLException e) {
                getLogger().warning("MySQL Error: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        } else {
            playerDataMap.remove(uuid);
            saveFileData();
        }
    }

    /**
     * Remove player data by username (used by CloveLib ban integration)
     * @param username The Minecraft username to remove
     */
    public void removePlayerDataByName(String username) {
        if (useMysql) {
            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "DELETE FROM discord_whitelist WHERE username = ?")) {
                stmt.setString(1, username);
                stmt.executeUpdate();
            } catch (SQLException e) {
                getLogger().warning("MySQL Error: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        } else {
            // Find and remove by username
            String uuidToRemove = null;
            for (Map.Entry<String, PlayerData> entry : playerDataMap.entrySet()) {
                if (entry.getValue().username.equalsIgnoreCase(username)) {
                    uuidToRemove = entry.getKey();
                    break;
                }
            }
            if (uuidToRemove != null) {
                playerDataMap.remove(uuidToRemove);
                saveFileData();
            }
        }
    }

    private List<String> getUsernamesByDiscordId(String discordId) {
        List<String> usernames = new ArrayList<>();

        if (useMysql) {
            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "SELECT username FROM discord_whitelist WHERE discord_id = ?")) {
                stmt.setString(1, discordId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    usernames.add(rs.getString("username"));
                }
            } catch (SQLException e) {
                getLogger().warning("MySQL Error: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        } else {
            for (PlayerData data : playerDataMap.values()) {
                if (data.discordId.equals(discordId)) {
                    usernames.add(data.username);
                }
            }
        }

        return usernames;
    }

    private String getDiscordUsernameByMinecraftUsername(String username) {
        if (useMysql) {
            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "SELECT discord_username FROM discord_whitelist WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("discord_username");
                }
            } catch (SQLException e) {
                getLogger().warning("MySQL Error: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        } else {
            for (PlayerData data : playerDataMap.values()) {
                if (data.username.equalsIgnoreCase(username)) {
                    return data.discordUsername;
                }
            }
        }

        return null;
    }

    private String getDiscordIdByUuid(String uuid) {
        if (useMysql) {
            try (PreparedStatement stmt = getDatabaseConnection().prepareStatement(
                    "SELECT discord_id FROM discord_whitelist WHERE uuid = ?")) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("discord_id");
                }
            } catch (SQLException e) {
                getLogger().warning("MySQL Error: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        } else {
            PlayerData data = playerDataMap.get(uuid);
            return data != null ? data.discordId : null;
        }

        return null;
    }

    public class DiscordSlashCommandListener extends ListenerAdapter {

        @Override
        public void onReady(ReadyEvent event) {
            // Register slash commands when bot is ready
            event.getJDA().updateCommands().addCommands(
                    Commands.slash("register", "WingSync: Add a player to the Minecraft server whitelist")
                            .addOption(OptionType.STRING, "player", "The Minecraft username to add to whitelist", true),

                    Commands.slash("remove", "WingSync: Remove a player from the Minecraft server whitelist")
                            .addOption(OptionType.STRING, "player", "The Minecraft username to remove from whitelist", true),

                    Commands.slash("listwhitelist", "WingSync: Display all players currently on the whitelist"),

                    Commands.slash("whois", "WingSync: Find which Minecraft accounts are linked to a Discord user")
                            .addOption(OptionType.USER, "user", "The Discord user to check", true),

                    Commands.slash("whomc", "WingSync: Find which Discord user is linked to a Minecraft username")
                            .addOption(OptionType.STRING, "username", "The Minecraft username to check", true),

                    Commands.slash("storage", "WingSync: Check the current storage method being used")
            ).queue(success -> {
                getLogger().info("Successfully registered WingSync slash commands!");
            }, error -> {
                getLogger().severe("Failed to register WingSync slash commands: " + error.getMessage());
            });
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            switch (event.getName()) {
                case "whois":
                    handleWhoisCommand(event);
                    break;
                case "whomc":
                    handleWhomcCommand(event);
                    break;
                case "register":
                    handleWhitelistCommand(event);
                    break;
                case "remove":
                    handleUnwhitelistCommand(event);
                    break;
                case "listwhitelist":
                    handleListWhitelistCommand(event);
                    break;
                case "storage":
                    handleStorageCommand(event);
                    break;
            }
        }

        private void handleStorageCommand(SlashCommandInteractionEvent event) {
            String storageType = useMysql ? "MySQL Database" : "File-based Storage";
            String details = useMysql ?
                    "Connected to: " + getConfig().getString("mysql.host") + ":" + getConfig().getInt("mysql.port") :
                    "Data file: " + dataFile.getName() + " (" + playerDataMap.size() + " records)";

            event.reply("**Storage Information**\n" +
                    "Type: " + storageType + "\n" +
                    "Details: " + details).queue();
        }

        private void handleWhoisCommand(SlashCommandInteractionEvent event) {
            event.deferReply().queue();

            String discordId = event.getOption("user").getAsUser().getId();

            try {
                List<String> usernames = getUsernamesByDiscordId(discordId);
                StringBuilder response = new StringBuilder("Minecraft accounts linked to <@" + discordId + ">: ");

                if (!usernames.isEmpty()) {
                    response.append(String.join(", ", usernames));
                } else {
                    response.append("None");
                }

                event.getHook().sendMessage(response.toString()).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Failed to fetch data. Please try again later.").queue();
                getLogger().severe("Error in whois command: " + e.getMessage());
            }
        }

        private void handleWhomcCommand(SlashCommandInteractionEvent event) {
            event.deferReply().queue();

            String username = event.getOption("username").getAsString();

            try {
                String discordUsername = getDiscordUsernameByMinecraftUsername(username);

                if (discordUsername != null) {
                    event.getHook().sendMessage("**" + discordUsername + "** is linked to Minecraft username **" + username + "**").queue();
                } else {
                    event.getHook().sendMessage("❌ No Discord user is linked to Minecraft username **" + username + "**").queue();
                }
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Failed to fetch data. Please try again later.").queue();
                getLogger().severe("Error in whomc command: " + e.getMessage());
            }
        }

        private void handleWhitelistCommand(SlashCommandInteractionEvent event) {
            event.deferReply().queue();

            String playerName = event.getOption("player").getAsString();
            String discordId = event.getUser().getId();
            String discordUsername = event.getUser().getAsTag();

            Bukkit.getScheduler().runTask(Main.this, () -> {
                try {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
                    UUID uuid = player.getUniqueId();

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + playerName);

                    storePlayerData(uuid.toString(), playerName, discordId, discordUsername);

                    event.getHook().sendMessage("✅ Player **" + playerName + "** has been added to the whitelist!").queue();
                } catch (Exception e) {
                    getLogger().warning("Error adding player: " + e.getMessage());
                    event.getHook().sendMessage("❌ Failed to add player to whitelist.").queue();
                }
            });
        }

        private void handleUnwhitelistCommand(SlashCommandInteractionEvent event) {
            event.deferReply().queue();

            String playerName = event.getOption("player").getAsString();
            String discordId = event.getUser().getId();
            String adminDiscordId = getConfig().getString("discord.admin_id");

            Bukkit.getScheduler().runTask(Main.this, () -> {
                try {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
                    UUID uuid = player.getUniqueId();

                    String playerDiscordId = getDiscordIdByUuid(uuid.toString());

                    if (playerDiscordId != null && (playerDiscordId.equals(discordId) || discordId.equals(adminDiscordId))) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + playerName);
                        removePlayerData(uuid.toString());
                        event.getHook().sendMessage("✅ Player **" + playerName + "** has been removed from the whitelist.").queue();
                    } else {
                        event.getHook().sendMessage("❌ You do not have permission to unwhitelist this player.").queue();
                    }
                } catch (Exception e) {
                    getLogger().warning("Error removing player: " + e.getMessage());
                    event.getHook().sendMessage("❌ Failed to remove player from whitelist.").queue();
                }
            });
        }

        private void handleListWhitelistCommand(SlashCommandInteractionEvent event) {
            event.deferReply().queue();

            Bukkit.getScheduler().runTask(Main.this, () -> {
                StringBuilder response = new StringBuilder("**Whitelisted Players:**\n```\n");
                for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
                    response.append("• ").append(player.getName()).append("\n");
                }

                if (response.toString().equals("**Whitelisted Players:**\n```\n")) {
                    response.append("No players are currently whitelisted.");
                }

                response.append("```");

                event.getHook().sendMessage(response.toString()).queue();
            });
        }
    }
}