package com.enderdevelopment.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener {
    private static final int CHAPTERS = 10;
    private static final String[] TITLES = {"A Message in a Bottle", "The Island Welcome", "A Missing Bridge", "The First Late Arrival", "Clues in the Supply Shed", "The Harbor in Trouble", "The Accusation", "A Fair Test", "The Builder's Truth", "The Island Build-Off"};
    private static final String[][] STORY = {
            {"Julian finds a bottle on the shore. Inside is a note from Steve, an island builder.", "Brian helps Julian read the map. Steve needs a partner before the contest begins."},
            {"Steve welcomes Julian and Brian. Five builders are expected for the contest.", "The team prepares the island and makes sure everyone gets a fair chance."},
            {"The dock path is damaged. Someone removed bridge planks before the contestants arrive.", "Steve needs oak planks to make the crossing safe."},
            {"Mira arrives alone and reports that the others are delayed.", "She saw someone near the harbor, but nobody should be blamed without proof."},
            {"The supply shed has been rummaged through. A redstone trail points toward the signal tower.", "Search carefully for the changed sign that confused the builders."},
            {"The harbor beacon is dark, so boats cannot find the safe landing.", "Restore its light and help the delayed competitors find the island."},
            {"A tool chest appears near Julian and Brian's camp. The builders begin to suspect them.", "Steve asks Julian to stay calm and find evidence before answering."},
            {"The group compares the altered sign, the redstone trail, and a torn badge.", "Listen to the builders and compare clues before making an accusation."},
            {"The overlooked competitor admits to delaying the arrivals because they feared losing.", "Julian suggests repairing the harbor together. The competitor agrees to make things right."},
            {"All five builders arrive. Julian and Brian help Steve finish the island pavilion.", "The judges reward teamwork, creativity, and honesty. Steve wins the construction contest."}
    };
    private static final String[] OBJECTIVES = {"Search near the island shore for the bottle clue.", "Collect 4 oak planks.", "Place 8 oak planks near the dock.", "Talk with Mira near the dock.", "Search near the supply shed for the altered-sign clue.", "Place 4 torches near the harbor beacon.", "Search near camp for the planted tool-chest clue.", "Search near the signal tower for the torn badge clue.", "Hear Rowan's confession by talking with Rowan.", "Place 12 oak planks to finish the pavilion."};
    private static final String[] LANDMARKS = {"shore", "dock", "bridge", "Mira's dock", "supply shed", "harbor beacon", "camp", "signal tower", "contest pavilion", "build-off pavilion"};
    private static final String[] CLUE_NAMES = {"Bottle and map", "Welcome sign", "Missing bridge planks", "Mira's harbor report", "Altered shed sign", "Harbor beacon", "Planted tool chest", "Torn competitor badge", "Rowan's confession", "Build-off plans"};
    private static final Map<String, int[]> DEFAULT_OFFSETS = Map.of(
            "shore", new int[]{-24, 0}, "dock", new int[]{-12, 8}, "bridge", new int[]{-6, 12},
            "shed", new int[]{8, 8}, "beacon", new int[]{20, 8}, "camp", new int[]{0, -10}, "tower", new int[]{12, -8}, "pavilion", new int[]{0, 16});
    private static final Material[] REWARDS = {Material.BREAD, Material.TORCH, Material.OAK_PLANKS, Material.COOKED_BEEF, Material.LANTERN, Material.GOLD_INGOT, Material.CAKE, Material.EMERALD, Material.DIAMOND, Material.DIAMOND_BLOCK};
    private static final int[] REWARD_AMOUNTS = {4, 8, 16, 4, 4, 3, 1, 5, 2, 1};
    private final Map<UUID, Integer> chapter = new HashMap<>(), objective = new HashMap<>(), tutorial = new HashMap<>(), movements = new HashMap<>(), brokenBlocks = new HashMap<>(), finalBuild = new HashMap<>();
    private final Map<UUID, Boolean> crafted = new HashMap<>(), eaten = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        setupLandmarks();
        ConfigurationSection players = getConfig().getConfigurationSection("players");
        if (players != null) for (String key : players.getKeys(false)) try {
            UUID id = UUID.fromString(key);
            ConfigurationSection data = players.getConfigurationSection(key);
            if (data == null) continue;
            chapter.put(id, Math.max(0, Math.min(CHAPTERS + 1, data.getInt("chapter", 0))));
            objective.put(id, Math.max(0, data.getInt("objective", 0)));
            tutorial.put(id, Math.max(0, Math.min(5, data.getInt("tutorial", 0))));
        } catch (IllegalArgumentException ignored) { getLogger().warning("Ignoring invalid player UUID: " + key); }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::showClueMarkers, 40L, 100L);
    }

    private void setupLandmarks() {
        for (Map.Entry<String, int[]> entry : DEFAULT_OFFSETS.entrySet()) {
            String path = "landmarks." + entry.getKey();
            if (!getConfig().contains(path + ".x")) getConfig().set(path + ".x", entry.getValue()[0]);
            if (!getConfig().contains(path + ".z")) getConfig().set(path + ".z", entry.getValue()[1]);
        }
        saveConfig();
    }

    @Override public void onDisable() { saveProgress(); }
    private void saveProgress() {
        getConfig().set("players", null);
        for (UUID id : chapter.keySet()) {
            String path = "players." + id;
            getConfig().set(path + ".chapter", chapter.get(id));
            getConfig().set(path + ".objective", objective.getOrDefault(id, 0));
            getConfig().set(path + ".tutorial", tutorial.getOrDefault(id, 0));
        }
        saveConfig();
    }

    @EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (chapter.containsKey(id)) showStatus(event.getPlayer());
        else event.getPlayer().sendMessage(Component.text("Welcome! Begin Julian and Brian's island story with /islandstory start.", NamedTextColor.GOLD));
    }

    @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This story command is for players in the world."); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (action) {
            case "start" -> { chapter.putIfAbsent(player.getUniqueId(), 0); objective.putIfAbsent(player.getUniqueId(), 0); tutorial.putIfAbsent(player.getUniqueId(), 0); saveProgress(); showStatus(player); }
            case "status" -> showStatus(player);
            case "next" -> advance(player);
            case "reset" -> { chapter.remove(player.getUniqueId()); objective.remove(player.getUniqueId()); tutorial.remove(player.getUniqueId()); saveProgress(); player.sendMessage(Component.text("Your story progress has been reset.", NamedTextColor.GREEN)); }
            case "search" -> search(player);
            case "locations" -> showLocations(player);
            case "talk" -> talk(player, args);
            default -> player.sendMessage(Component.text("Use /islandstory start, status, next, search, locations, talk <name>, or reset.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void showStatus(Player player) {
        UUID id = player.getUniqueId(); Integer current = chapter.get(id);
        if (current == null) { player.sendMessage(Component.text("Start with /islandstory start.", NamedTextColor.YELLOW)); return; }
        if (current == CHAPTERS + 1) { player.sendMessage(Component.text("The prologue, ten chapters, and epilogue are complete. Thanks for building together!", NamedTextColor.GOLD)); return; }
        if (current == CHAPTERS) { player.sendMessage(Component.text("Epilogue: The Island After the Contest", NamedTextColor.GOLD)); player.sendMessage(Component.text("Place 8 blocks of any kind near camp to build a welcome garden. Progress: " + finalBuild.getOrDefault(id, 0) + "/8. Then use /islandstory next.", NamedTextColor.YELLOW)); return; }
        if (current == 0 && tutorial.getOrDefault(id, 0) < 5) { showTutorial(player); return; }
        player.sendMessage(Component.text("Chapter " + (current + 1) + "/10: " + TITLES[current], NamedTextColor.GOLD));
        for (String line : STORY[current]) player.sendMessage(Component.text(line, NamedTextColor.WHITE));
        player.sendMessage(Component.text("Objective: " + OBJECTIVES[current], NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Progress: " + objective.getOrDefault(id, 0) + ". Use /islandstory next when complete.", NamedTextColor.GREEN));
        if (isSearchChapter(current)) player.sendMessage(Component.text("Clue site: " + LANDMARKS[current] + " (use /islandstory locations for directions).", NamedTextColor.AQUA));
    }

    private void showTutorial(Player player) {
        int step = tutorial.getOrDefault(player.getUniqueId(), 0);
        String[] missions = {"Move 10 blocks to practice walking and looking around.", "Break and collect 3 blocks, such as logs or dirt.", "Craft any item using your inventory or a crafting table.", "Place 3 blocks to make a small shelter or path.", "Eat one food item to restore hunger."};
        player.sendMessage(Component.text("Prologue: Builder Training (mission " + (step + 1) + "/5)", NamedTextColor.GOLD));
        player.sendMessage(Component.text(missions[Math.min(step, 4)], NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Finish each mission and use /islandstory next. Julian and Brian can work together.", NamedTextColor.GREEN));
    }

    private void advance(Player player) {
        UUID id = player.getUniqueId(); Integer current = chapter.get(id);
        if (current == null) { player.sendMessage(Component.text("Begin with /islandstory start.", NamedTextColor.YELLOW)); return; }
        if (current == 0 && tutorial.getOrDefault(id, 0) < 5) {
            int step = tutorial.getOrDefault(id, 0);
            boolean done = switch (step) { case 0 -> movements.getOrDefault(id, 0) >= 10; case 1 -> brokenBlocks.getOrDefault(id, 0) >= 3; case 2 -> crafted.getOrDefault(id, false); case 3 -> objective.getOrDefault(id, 0) >= 3; case 4 -> eaten.getOrDefault(id, false); default -> false; };
            if (!done) { player.sendMessage(Component.text("Complete the current prologue mission first.", NamedTextColor.RED)); showTutorial(player); return; }
            tutorial.put(id, step + 1); objective.put(id, 0); saveProgress();
            if (step < 4) { showTutorial(player); return; }
            player.sendMessage(Component.text("Prologue complete! Steve's island story begins now.", NamedTextColor.GOLD)); showStatus(player); return;
        }
        if (current == CHAPTERS) { if (finalBuild.getOrDefault(id, 0) < 8) { player.sendMessage(Component.text("Complete the welcome garden first. Check /islandstory status.", NamedTextColor.RED)); return; } chapter.put(id, CHAPTERS + 1); saveProgress(); showStatus(player); return; }
        if (current > CHAPTERS) { showStatus(player); return; }
        int count = objective.getOrDefault(id, 0);
        boolean complete = switch (current) { case 0, 3, 4, 6, 7, 8 -> count >= 1; case 1 -> count >= 4; case 2 -> count >= 8; case 5 -> count >= 4; case 9 -> count >= 12; default -> false; };
        if (!complete) { player.sendMessage(Component.text("Objective not complete yet. Check /islandstory status.", NamedTextColor.RED)); return; }
        giveSharedReward(player, current); chapter.put(id, current + 1); objective.put(id, 0); saveProgress(); showStatus(player);
    }

    private void giveSharedReward(Player player, int current) {
        for (Player participant : Bukkit.getOnlinePlayers()) if (chapter.containsKey(participant.getUniqueId())) {
            participant.getInventory().addItem(new org.bukkit.inventory.ItemStack(REWARDS[current], REWARD_AMOUNTS[current]));
            participant.sendMessage(Component.text("Shared chapter reward: " + REWARD_AMOUNTS[current] + " " + REWARDS[current].name().toLowerCase().replace('_', ' ') + (REWARD_AMOUNTS[current] == 1 ? "!" : "s!"), NamedTextColor.GOLD));
        }
        if (!chapter.containsKey(player.getUniqueId())) player.getInventory().addItem(new org.bukkit.inventory.ItemStack(REWARDS[current], REWARD_AMOUNTS[current]));
    }

    private boolean isSearchChapter(int current) { return current == 0 || current == 4 || current == 6 || current == 7; }
    private String landmarkFor(int current) { return switch (current) { case 0 -> "shore"; case 4 -> "shed"; case 6 -> "camp"; case 7 -> "tower"; default -> "shore"; }; }
    private Location landmark(Player player, String key) {
        World world = player.getWorld(); Location spawn = world.getSpawnLocation();
        int x = getConfig().getInt("landmarks." + key + ".x", 0), z = getConfig().getInt("landmarks." + key + ".z", 0);
        return new Location(world, spawn.getBlockX() + x + 0.5, spawn.getY(), spawn.getBlockZ() + z + 0.5);
    }

    private void showLocations(Player player) {
        String[] keys = {"shore", "dock", "bridge", "shed", "beacon", "camp", "tower", "pavilion"};
        String[] names = {"Bottle shore", "Contest dock", "Broken bridge", "Supply shed", "Harbor beacon", "Camp", "Signal tower", "Build-off pavilion"};
        player.sendMessage(Component.text("Island story landmarks (coordinates in your current world):", NamedTextColor.GOLD));
        for (int i = 0; i < keys.length; i++) {
            Location spot = landmark(player, keys[i]);
            player.sendMessage(Component.text(names[i] + ": X " + spot.getBlockX() + ", Y " + spot.getBlockY() + ", Z " + spot.getBlockZ() + " (" + (int) player.getLocation().distance(spot) + " blocks away)", NamedTextColor.YELLOW));
        }
        player.sendMessage(Component.text("Glowing particle columns mark clue sites. Adjust landmark offsets in plugins/IslandBuilders/config.yml; X/Z are relative to world spawn.", NamedTextColor.GRAY));
    }

    private void showClueMarkers() {
        String[] keys = {"shore", "shed", "camp", "tower"};
        for (Player player : Bukkit.getOnlinePlayers()) {
            int current = chapter.getOrDefault(player.getUniqueId(), -1);
            if (!isSearchChapter(current)) continue;
            Location marker = landmark(player, landmarkFor(current));
            if (player.getWorld() != marker.getWorld() || player.getLocation().distanceSquared(marker) > 96 * 96) continue;
            for (double y = 0.2; y <= 2.2; y += 0.5) player.spawnParticle(Particle.HAPPY_VILLAGER, marker.clone().add(0, y, 0), 2, 0.18, 0.08, 0.18, 0);
        }
    }

    private void search(Player player) {
        Integer current = chapter.get(player.getUniqueId());
        if (current == null || !isSearchChapter(current)) { player.sendMessage(Component.text("There is nothing to search for right now.", NamedTextColor.YELLOW)); return; }
        Location site = landmark(player, landmarkFor(current));
        if (player.getWorld() != site.getWorld() || player.getLocation().distanceSquared(site) > 64) {
            player.sendMessage(Component.text("You are not at the clue site yet. Follow the particle marker or use /islandstory locations.", NamedTextColor.YELLOW)); return;
        }
        objective.put(player.getUniqueId(), 1); saveProgress();
        String[] clues = {"Clue: Steve's bottle map points to the island camp.", "Clue: The shed sign was turned around; a redstone trail leads to the tower.", "Clue: This chest has no owner's mark. It may have been planted to frame you.", "Clue: The badge is torn and matches a builder's contest uniform."};
        int index = current == 0 ? 0 : current == 4 ? 1 : current == 6 ? 2 : 3;
        player.sendMessage(Component.text(CLUE_NAMES[current] + " found. " + clues[index], NamedTextColor.AQUA));
    }

    private void talk(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(Component.text("Talk to Steve, Mira, or Rowan by using /islandstory talk <name>.", NamedTextColor.YELLOW)); return; }
        String name = args[1].toLowerCase(); Integer current = chapter.get(player.getUniqueId());
        if (current == null) { player.sendMessage(Component.text("Begin with /islandstory start.", NamedTextColor.YELLOW)); return; }
        if (name.equals("steve")) { if (current == 1) objective.put(player.getUniqueId(), 4); player.sendMessage(Component.text("Steve: Welcome, Julian and Brian! Help me make sure all five builders get a fair chance.", NamedTextColor.GREEN)); }
        else if (name.equals("mira") && current == 3) { objective.put(player.getUniqueId(), 1); player.sendMessage(Component.text("Mira: I came early and saw someone near the harbor, but I didn't see their face.", NamedTextColor.AQUA)); }
        else if (name.equals("rowan") && current == 8) { objective.put(player.getUniqueId(), 1); player.sendMessage(Component.text("Rowan: I changed the sign and beacon because I feared being overlooked. I'm sorry, and I'll help repair them.", NamedTextColor.AQUA)); }
        else player.sendMessage(Component.text("That character has nothing new to say in this part of the story.", NamedTextColor.GRAY));
        saveProgress();
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        UUID id = event.getPlayer().getUniqueId();
        if (chapter.getOrDefault(id, -1) == 0 && tutorial.getOrDefault(id, 0) == 0) movements.merge(id, 1, Integer::sum);
    }
    @EventHandler public void onBreak(BlockBreakEvent event) { UUID id = event.getPlayer().getUniqueId(); if (chapter.getOrDefault(id, -1) == 0 && tutorial.getOrDefault(id, 0) == 1) brokenBlocks.merge(id, 1, Integer::sum); }
    @EventHandler public void onCraft(CraftItemEvent event) { if (event.getWhoClicked() instanceof Player player && chapter.getOrDefault(player.getUniqueId(), -1) == 0 && tutorial.getOrDefault(player.getUniqueId(), 0) == 2) crafted.put(player.getUniqueId(), true); }
    @EventHandler public void onConsume(PlayerItemConsumeEvent event) { UUID id = event.getPlayer().getUniqueId(); if (chapter.getOrDefault(id, -1) == 0 && tutorial.getOrDefault(id, 0) == 4) eaten.put(id, true); }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        UUID id = event.getPlayer().getUniqueId(); int current = chapter.getOrDefault(id, -1);
        if (current == 0 && tutorial.getOrDefault(id, 0) == 3) objective.merge(id, 1, Integer::sum);
        else if (current == 2 && event.getBlockPlaced().getType() == Material.OAK_PLANKS) objective.merge(id, 1, Integer::sum);
        else if (current == 5 && event.getBlockPlaced().getType() == Material.TORCH) objective.merge(id, 1, Integer::sum);
        else if (current == 9 && event.getBlockPlaced().getType() == Material.OAK_PLANKS) objective.merge(id, 1, Integer::sum);
        else if (current == CHAPTERS) finalBuild.merge(id, 1, Integer::sum);
        saveProgress();
    }
}
