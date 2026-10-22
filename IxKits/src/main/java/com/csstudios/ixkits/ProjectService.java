package com.csstudios.ixkits;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * File and project operations kept separate from the inventory UI.
 * Project files are data only; imported YAML is never executed.
 */
public final class ProjectService {
    private final JavaPlugin plugin;
    private final File projectsDir;
    private final File backupsDir;
    private final File trashDir;
    private final File importsDir;
    private final File exportsDir;

    public ProjectService(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.projectsDir = new File(dataFolder, "projects");
        this.backupsDir = new File(dataFolder, "backups");
        this.trashDir = new File(dataFolder, "trash");
        this.importsDir = new File(dataFolder, "imports");
        this.exportsDir = new File(dataFolder, "exports");
        projectsDir.mkdirs();
        backupsDir.mkdirs();
        trashDir.mkdirs();
        importsDir.mkdirs();
        exportsDir.mkdirs();
    }

    /**
     * Saves YAML through a sibling temporary file, replacing the destination
     * atomically when supported to reduce the risk of truncated project files.
     */
    private void saveAtomically(YamlConfiguration yaml, File destination) throws IOException {
        Path target = destination.toPath().toAbsolutePath();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, destination.getName() + ".", ".tmp");
        try {
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public File projectFile(String id) {
        String safe = slug(id);
        return new File(projectsDir, safe + ".yml");
    }

    public File importsDirectory() { return importsDir; }
    public File exportsDirectory() { return exportsDir; }

    public String slug(String input) {
        if (input == null) return "";
        return input.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]", "-")
            .replaceAll("[-_]{2,}", "-")
            .replaceAll("^[._-]+|[._-]+$", "");
    }

    public void backupBeforeChange(File file) {
        if (file == null || !file.isFile()) return;
        String id = file.getName().replaceFirst("\\.yml$", "");
        File directory = new File(backupsDir, slug(id));
        directory.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        File backup = new File(directory, stamp + ".yml");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            trimBackups(directory, 30);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not back up project " + id + ": " + exception.getMessage());
        }
    }

    private void trimBackups(File directory, int keep) {
        File[] all = directory.listFiles((d, name) -> name.endsWith(".yml"));
        if (all == null || all.length <= keep) return;
        Arrays.sort(all, Comparator.comparing(File::getName).reversed());
        for (int i = keep; i < all.length; i++) {
            try { Files.deleteIfExists(all[i].toPath()); }
            catch (IOException ignored) { }
        }
    }

    public boolean duplicate(String sourceId, String targetName) throws IOException {
        File source = projectFile(sourceId);
        String targetId = slug(targetName);
        File target = projectFile(targetId);
        if (!source.isFile() || targetId.isBlank() || target.exists()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        yaml.set("name", targetName.trim());
        yaml.set("id", targetId);
        yaml.set("created", System.currentTimeMillis());
        yaml.set("updated", System.currentTimeMillis());
        yaml.set("format-version", Math.max(2, yaml.getInt("format-version", 1)));
        saveAtomically(yaml, target);
        return true;
    }

    public boolean rename(String sourceId, String targetName) throws IOException {
        File source = projectFile(sourceId);
        String targetId = slug(targetName);
        File target = projectFile(targetId);
        if (!source.isFile() || targetId.isBlank() || target.exists()) return false;
        backupBeforeChange(source);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        yaml.set("name", targetName.trim());
        yaml.set("id", targetId);
        yaml.set("updated", System.currentTimeMillis());
        saveAtomically(yaml, target);
        Files.deleteIfExists(source.toPath());
        return true;
    }

    public boolean moveToTrash(String id) throws IOException {
        File source = projectFile(id);
        if (!source.isFile()) return false;
        backupBeforeChange(source);
        File directory = new File(trashDir, slug(id));
        directory.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        Files.move(source.toPath(), new File(directory, stamp + ".yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public List<String> listTrash() {
        File[] directories = trashDir.listFiles(File::isDirectory);
        if (directories == null) return new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (File directory : directories) {
            File[] files = directory.listFiles((d, name) -> name.endsWith(".yml"));
            if (files != null && files.length > 0) ids.add(directory.getName());
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    public String trashedProjectName(String id) {
        File directory = new File(trashDir, slug(id));
        File[] snapshots = directory.listFiles((d, name) -> name.endsWith(".yml"));
        if (snapshots == null || snapshots.length == 0) return id;
        Arrays.sort(snapshots, Comparator.comparing(File::getName).reversed());
        return YamlConfiguration.loadConfiguration(snapshots[0]).getString("name", id);
    }

    public boolean restoreFromTrash(String id) throws IOException {
        File directory = new File(trashDir, slug(id));
        File target = projectFile(id);
        if (target.exists()) return false;
        File[] snapshots = directory.listFiles((d, name) -> name.endsWith(".yml"));
        if (snapshots == null || snapshots.length == 0) return false;
        Arrays.sort(snapshots, Comparator.comparing(File::getName).reversed());
        File latest = snapshots[0];
        Files.copy(latest.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        yaml.set("id", slug(id));
        yaml.set("format-version", Math.max(2, yaml.getInt("format-version", 1)));
        yaml.set("updated", System.currentTimeMillis());
        saveAtomically(yaml, target);
        Files.deleteIfExists(latest.toPath());
        File[] remaining = directory.listFiles();
        if (remaining != null && remaining.length == 0) Files.deleteIfExists(directory.toPath());
        return true;
    }

    public boolean undo(String id) throws IOException {
        File current = projectFile(id);
        File directory = new File(backupsDir, slug(id));
        File[] backups = directory.listFiles((d, name) -> name.endsWith(".yml") && !name.startsWith("before-undo-"));
        if (backups == null || backups.length == 0) return false;
        Arrays.sort(backups, Comparator.comparing(File::getName).reversed());
        File latest = backups[0];
        if (current.isFile()) {
            File undoArchive = new File(directory, "before-undo-" +
                new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date()) + ".yml");
            Files.copy(current.toPath(), undoArchive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(latest.toPath(), current.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(latest.toPath());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(current);
        yaml.set("updated", System.currentTimeMillis());
        saveAtomically(yaml, current);
        return true;
    }

    public boolean redo(String id) throws IOException {
        File current = projectFile(id);
        File directory = new File(backupsDir, slug(id));
        File[] undoSnapshots = directory.listFiles((d, name) -> name.startsWith("before-undo-") && name.endsWith(".yml"));
        if (undoSnapshots == null || undoSnapshots.length == 0 || !current.isFile()) return false;
        Arrays.sort(undoSnapshots, Comparator.comparing(File::getName).reversed());
        File snapshot = undoSnapshots[0];
        backupBeforeChange(current);
        Files.copy(snapshot.toPath(), current.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(snapshot.toPath());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(current);
        yaml.set("updated", System.currentTimeMillis());
        saveAtomically(yaml, current);
        return true;
    }

    public boolean importNative(File input, String requestedName) throws IOException {
        if (input == null || !input.isFile()) return false;
        String fileName = input.getName().toLowerCase(Locale.ROOT);
        if (!(fileName.endsWith(".yml") || fileName.endsWith(".ixkit.yml"))) return false;
        YamlConfiguration source = YamlConfiguration.loadConfiguration(input);
        if (source.getConfigurationSection("pages") == null) return false;
        int format = source.getInt("format-version", source.getInt("version", 1));
        if (format < 1 || format > 2) return false;
        String name = requestedName == null || requestedName.isBlank()
            ? source.getString("name", input.getName().replaceFirst("\\.yml$", ""))
            : requestedName.trim();
        String id = slug(name);
        File target = projectFile(id);
        if (id.isBlank() || target.exists()) return false;
        YamlConfiguration imported = new YamlConfiguration();
        for (String key : source.getKeys(true)) {
            if (key.startsWith("ixkits-export.")) continue;
            if (key.equals("name") || key.equals("id") || key.equals("version") ||
                key.equals("format-version") || key.equals("created") || key.equals("updated") ||
                key.startsWith("pages.") || key.startsWith("equipment.")) {
                imported.set(key, source.get(key));
            }
        }
        imported.set("name", name);
        imported.set("id", id);
        imported.set("version", 1);
        imported.set("format-version", 2);
        imported.set("created", System.currentTimeMillis());
        imported.set("updated", System.currentTimeMillis());
        saveAtomically(imported, target);
        return true;
    }

    public List<String> validate(String id) {
        List<String> report = new ArrayList<>();
        File file = projectFile(id);
        if (!file.isFile()) {
            report.add("ERROR: project file does not exist.");
            return report;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getString("name", "").isBlank()) report.add("WARNING: project name is missing.");
        int format = yaml.getInt("format-version", yaml.getInt("version", 1));
        if (format > 2) report.add("ERROR: unsupported project format version " + format + ".");
        if (yaml.getConfigurationSection("pages") == null) report.add("ERROR: pages section is missing.");
        int itemCount = 0;
        ConfigurationSection pages = yaml.getConfigurationSection("pages");
        if (pages != null) {
            for (String page : pages.getKeys(false)) {
                ConfigurationSection pageSection = pages.getConfigurationSection(page);
                if (pageSection == null) continue;
                for (String slot : pageSection.getKeys(false)) {
                    Object raw = pageSection.get(slot);
                    if (!(raw instanceof ItemStack item)) continue;
                    if (item.getType().isAir()) {
                        report.add("WARNING: AIR item in pages." + page + "." + slot + ".");
                        continue;
                    }
                    itemCount++;
                    if (item.getAmount() < 1 || item.getAmount() > item.getMaxStackSize()) {
                        report.add("WARNING: unusual stack amount at pages." + page + "." + slot + ".");
                    }
                    ItemMeta meta = item.getItemMeta();
                    if (meta instanceof Damageable damageable && damageable.hasDamage() &&
                        damageable.getDamage() > item.getType().getMaxDurability()) {
                        report.add("WARNING: durability exceeds material limit at pages." + page + "." + slot + ".");
                    }
                }
            }
        }
        report.add("OK: " + itemCount + " inventory item(s) found.");
        report.add("OK: equipment slots will be included in native saves.");
        if (report.size() == 1) report.add("OK: no basic validation issues found.");
        return report;
    }

    public File exportEssentials(String id) throws IOException {
        File source = projectFile(id);
        if (!source.isFile()) throw new IOException("Project not found.");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        String kitName = slug(yaml.getString("name", id));
        YamlConfiguration out = new YamlConfiguration();
        out.set("kits." + kitName + ".delay", 0);
        List<String> itemLines = new ArrayList<>();
        ConfigurationSection pages = yaml.getConfigurationSection("pages");
        if (pages != null) {
            for (String page : pages.getKeys(false)) {
                ConfigurationSection section = pages.getConfigurationSection(page);
                if (section == null) continue;
                List<String> slots = new ArrayList<>(section.getKeys(false));
                slots.sort(Comparator.comparingInt(ProjectService::numericOrMax));
                for (String slot : slots) {
                    Object raw = section.get(slot);
                    if (raw instanceof ItemStack stack && !stack.getType().isAir()) itemLines.add(toEssentialsLine(stack));
                }
            }
        }
        ConfigurationSection equipment = yaml.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String key : List.of("helmet", "chestplate", "leggings", "boots", "offhand")) {
                Object raw = equipment.get(key);
                if (raw instanceof ItemStack stack && !stack.getType().isAir()) itemLines.add(toEssentialsLine(stack));
            }
        }
        out.set("kits." + kitName + ".items", itemLines);
        File destination = new File(exportsDir, kitName + ".essentials.yml");
        saveAtomically(out, destination);
        return destination;
    }

    private String toEssentialsLine(ItemStack stack) {
        StringBuilder line = new StringBuilder(stack.getType().name().toLowerCase(Locale.ROOT))
            .append(' ').append(stack.getAmount());
        for (Map.Entry<Enchantment, Integer> enchant : stack.getEnchantments().entrySet()) {
            line.append(' ').append(enchant.getKey().getKey().getKey()).append(':').append(enchant.getValue());
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) line.append(" name:").append(essentialsText(meta.getDisplayName()));
            if (meta.hasLore()) line.append(" lore:").append(String.join("|", meta.getLore().stream().map(this::essentialsText).toList()));
        }
        return line.toString();
    }

    private String essentialsText(String input) {
        if (input == null) return "";
        return input.replace('§', '&').trim().replaceAll("\\s+", "_").replace(":", "\\:");
    }

    public boolean importEssentials(File input, String kitName, String projectName) throws IOException {
        if (input == null || !input.isFile() || !input.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) return false;
        YamlConfiguration source = YamlConfiguration.loadConfiguration(input);
        ConfigurationSection section = source.getConfigurationSection("kits." + kitName);
        if (section == null) return false;
        List<?> rawItems = section.getList("items", Collections.emptyList());
        if (rawItems.isEmpty()) return false;
        String id = slug(projectName);
        File target = projectFile(id);
        if (id.isBlank() || target.exists()) return false;
        YamlConfiguration project = new YamlConfiguration();
        project.set("name", projectName);
        project.set("id", id);
        project.set("version", 1);
        project.set("format-version", 2);
        project.set("created", System.currentTimeMillis());
        int page = 0;
        int index = 0;
        for (Object entry : rawItems) {
            if (!(entry instanceof String line) || line.isBlank() || line.startsWith("/")) continue;
            ItemStack item = parseEssentialsLine(line);
            if (item == null) continue;
            if (index >= 56) { page++; index = 0; }
            int slot = slotForIndex(index++);
            project.set("pages." + page + "." + slot, item);
        }
        if (project.getConfigurationSection("pages") == null) return false;
        saveAtomically(project, target);
        return true;
    }

    private ItemStack parseEssentialsLine(String line) {
        if (line.startsWith("@")) return null; // NBT/base64 entries need EssentialsX's own serializer.
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length < 2) return null;
        Material material = Material.matchMaterial(tokens[0].replaceFirst(":\\d+$", ""));
        if (material == null || material.isAir()) return null;
        int amount;
        try { amount = Integer.parseInt(tokens[1]); } catch (NumberFormatException ex) { return null; }
        if (amount < 1) return null;
        ItemStack stack = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        for (int i = 2; i < tokens.length; i++) {
            String token = tokens[i];
            int colon = token.indexOf(':');
            if (colon <= 0 || colon == token.length() - 1) continue;
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            if (key.equals("name")) {
                meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', value.replace('_', ' ')));
            } else if (key.equals("lore")) {
                List<String> lore = Arrays.stream(value.split("\\|", -1))
                    .map(part -> org.bukkit.ChatColor.translateAlternateColorCodes('&', part.replace('_', ' '))).toList();
                meta.setLore(lore);
            } else {
                Enchantment enchantment = findEnchantment(key);
                if (enchantment != null) {
                    try { stack.addUnsafeEnchantment(enchantment, Math.max(1, Integer.parseInt(value))); }
                    catch (NumberFormatException ignored) { }
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private Enchantment findEnchantment(String key) {
        String normalized = key.replace("_", "").replace("-", "");
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            String id = enchantment.getKey().getKey();
            if (id.replace("_", "").equals(normalized)) return enchantment;
        }
        Map<String, String> aliases = Map.of(
            "digspeed", "efficiency",
            "durability", "unbreaking",
            "damageall", "sharpness",
            "fireaspect", "fire_aspect",
            "knockback", "knockback"
        );
        String mapped = aliases.get(normalized);
        if (mapped != null) return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(mapped));
        return null;
    }

    private static int numericOrMax(String input) {
        try { return Integer.parseInt(input); } catch (NumberFormatException ex) { return Integer.MAX_VALUE; }
    }

    private static int slotForIndex(int index) {
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        return slots[index % slots.length];
    }
}