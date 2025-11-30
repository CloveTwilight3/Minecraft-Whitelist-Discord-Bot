/*
 * Copyright (c) 2025 Clove Twilight
 * Licensed under the MIT License
 * WingSync
 */

package uk.co.clovetwilight3.wingsync.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import uk.co.clovetwilight3.clovelib.events.PlayerBannedEvent;
import uk.co.clovetwilight3.wingsync.Main;

public class CloveLibListener implements Listener {

    private final Main plugin;

    public CloveLibListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerBanned(PlayerBannedEvent event) {
        String playerName = event.getPlayerName();

        plugin.getLogger().info("Received ban event for " + playerName + " - removing from whitelist...");

        // Remove from whitelist via console command
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + playerName);
        });

        // Remove from our database/storage
        try {
            plugin.removePlayerDataByName(playerName);
            plugin.getLogger().info("Successfully removed " + playerName + " from WingSync whitelist data.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove " + playerName + " from WingSync data: " + e.getMessage());
        }
    }
}