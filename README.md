# Ezrique - Tags

Basic bot made for storing and sending messages via easy-to-remember, configurable commands.

---

## Setup

1. Add the bot to your Discord server, here's a link to [add the bot](https://discord.com/oauth2/authorize?client_id=1267647052037361718&permissions=274877910016&integration_type=0&scope=bot+applications.commands).
2. Use `/tag create` to make a new tag, or alternatively `/tag copy`/`/tag move` to move a tag from another server to yours (you need administrator permissions in both servers to do this). You can also import a tag in it's JSON form using `/tag import`.
3. Use `/tag list` to see all the tags in the server.
4. Trigger your tag using `/tag trigger <name>` or `/<name>`.

## Commands

- `/tag list`: List all tags in the server.
- `/tag create <copyable>`: Create a new tag.
- `/tag edit <copyable>`: Edit an existing tag.
- `/tag export <name>`: Export a tag in JSON format.
- `/tag exportall`: Export all tags in JSON format.
- `/tag import <json>`: Import a tag from JSON format.
- `/tag importbulk <json>`: Import multiple tags from JSON format.
- `/tag delete <name>`: Delete a tag.
- `/tag clear`: Delete all tags.
- `/tag copy <name> <guild>`: Copy a tag **to** another server using it's server ID.
- `/tag copyall <guild>`: Copy all tags **to** another server using it's server ID.
- `/tag move <name> <guild>`: Move a tag **to** another server using it's server ID.
- `/tag moveall <guild>`: Move all tags **to** another server using it's server ID.
- `/tag info <name>`: Get information about a tag.
- `/tag trigger <name>`: Trigger a tag.
- `/<name>`: Trigger a tag.

## Permissions

All of the commands under `/tag` require the Manage Server permission at least. The exceptions are,

- `/tag trigger` which requires no extra permissions. (though it's not visible to users without the Manage Server permission by default)
- `/tag clear` which requires the Administrator permission.
- `/tag copy`, `/tag copyall`, `/tag move` and `/tag moveall` which require the Administrator permission in both servers.

## Support

If you need help with the bot, have any suggestions or found a bug, feel free to join the [support server](https://s.deftu.dev/discord) or contact me directly, `@deftu`.

## Privacy

The bot does not store any personal data, messages or any other information. The only data stored is the tags you create, which are stored in a database. The bot does not have access to any other information in your server.

---

**This project is licensed under [LGPL-3.0][lgpl]**\
**&copy; 2024 Deftu**

[lgpl]: https://www.gnu.org/licenses/lgpl-3.0.html
