# Minecraft-Whitelist-Discord-Bot

Minecraft-Whitelist-Discord-Bot is a simple and efficient Discord bot for managing the Minecraft server whitelist. The bot integrates with a MySQL database to securely store whitelist data and sync it with the Minecraft server.

---

## Features

- **Whitelist Management**: Add, remove, and view players on the Minecraft server whitelist directly from Discord.
- **MySQL Integration**: Store and manage whitelist data in a MySQL database.
- **Simple Commands**:
  - `!whitelist <MinecraftUsername>`: Adds a player to the whitelist.
  - `!unwhitelist <MinecraftUsername>`: Removes a player from the whitelist.
  - `!listwhitelist`: Displays all players currently whitelisted.

---

## Installation

1. Download the latest version of the bot from the [Releases](https://github.com/CloveTwilight3/Minecraft-Whitelist-Discord-Bot/releases) page.
2. Place the `.jar` file in your Minecraft server's `plugins` directory.
3. Restart your server.
4. Configure the plugin by editing the `config.yml` file in the `plugins/Minecraft-Whitelist-Discord-Bot` folder:
   - Add your MySQL database credentials.
   - Specify your bot token and allowed Discord roles.
5. Reload the server to apply changes.

---

## Usage

- Use `!whitelist <MinecraftUsername>` in Discord to add a player to the Minecraft whitelist.
- Use `!unwhitelist <MinecraftUsername>` in Discord to remove a player from the whitelist.
- Use `!listwhitelist` in Discord to display all players currently whitelisted.

---

## License

This project is licensed under the Apache 2.0 License. See the [LICENSE](https://github.com/CloveTwilight3/Minecraft-Whitelist-Discord-Bot/blob/main/LICENSE) file for details.

---

## Acknowledgements

- Thanks to the Discord.js and PaperMC communities for their tools and support.
- Special thanks to all contributors for making this project possible.

---

# Contributors
<a href="https://github.com/CloveTwilight3/clovetwilight3/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=CloveTwilight3/Minecraft-Whitelist-Discord-Bot" />
</a>
