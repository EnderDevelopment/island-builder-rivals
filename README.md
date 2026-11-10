# Minecraft Island Story Plugin

Island Builders: The Missing Rivals is a Minecraft Paper plugin that guides players through a cooperative island building adventure with a prologue, ten story chapters, tracked objectives, clues, landmarks, and rewards. Julian and Brian help Steve prepare for a construction contest, investigate delays and suspicions, and bring the rival builders together.

## Install

1. Run a Paper server compatible with Minecraft 1.21. The project targets Java 21 and Paper API 1.21.11.
2. Build the plugin JAR from the project using Maven: `mvn package`.
3. Copy `target/plugin.jar` into your server’s `plugins` folder.
4. Start or restart the server. The plugin creates its configuration at `plugins/IslandBuilders/config.yml`.

## Use

1. Join the server and run `/islandstory start` to begin.
2. Follow the prologue missions and use `/islandstory next` after completing each one.
3. Check your current chapter and objective with `/islandstory status`.
4. Use `/islandstory locations` for landmark coordinates and `/islandstory search` when a chapter asks you to find a clue. Talk to characters with `/islandstory talk <name>`.
5. Complete chapter objectives and run `/islandstory next` to advance. At the end, build the welcome garden and advance once more to finish the epilogue.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/da2145c1-c38a-4aa9-ac95-e81c10ef963f?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=da2145c1-c38a-4aa9-ac95-e81c10ef963f
