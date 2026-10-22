package com.csstudios.ixkits;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class IxKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private File projectsDir, templatesDir, exportsDir;
    private static final String PREFIX = "§8[§bIxKits§8] §7";
    private static final int[] EDIT_SLOTS = {
        10,11,12,13,14,15,16, 19,20,21,22,23,24,25,
        28,29,30,31,32,33,34, 37,38,39,40,41,42,43
    };

    @Override public void onEnable() {
        projectsDir = new File(getDataFolder(), "projects");
        templatesDir = new File(getDataFolder(), "templates");
        exportsDir = new File(getDataFolder(), "exports");
        projectsDir.mkdirs(); templatesDir.mkdirs(); exportsDir.mkdirs();
        initializeTemplates();
        Objects.requireNonNull(getCommand("ixkits")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("IxKits habilitado. Kit Creation Studio by CS Studios.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("IxKits: este comando requiere un jugador.");
            return true;
        }
        if (!player.hasPermission("ixkits.admin")) {
            player.sendMessage(PREFIX + "No tienes permiso para administrar IxKits.");
            return true;
        }
        if (args.length == 0) { openMain(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("crear") && args.length >= 2) {
            String name = slug(String.join("-", Arrays.copyOfRange(args, 1, args.length)));
            if (name.isBlank()) { player.sendMessage(PREFIX + "Nombre no válido."); return true; }
            File f = projectFile(name);
            if (f.exists()) { player.sendMessage(PREFIX + "Ya existe un proyecto con ese nombre."); return true; }
            YamlConfiguration y = new YamlConfiguration();
            y.set("name", String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            y.set("version", 1);
            saveYaml(y, f);
            player.sendMessage(PREFIX + "Proyecto creado: §b" + name);
            openEditor(player, name, 0);
            return true;
        }
        if (sub.equals("editar") && args.length >= 2) {
            String name = slug(args[1]);
            if (!projectFile(name).exists()) player.sendMessage(PREFIX + "No existe ese proyecto.");
            else openEditor(player, name, 0);
            return true;
        }
        if (sub.equals("proyectos")) { openProjects(player); return true; }
        if (sub.equals("plantillas")) { openTemplates(player); return true; }
        if (sub.equals("exportar") && args.length >= 2) { exportProject(player, slug(args[1])); return true; }
        if (sub.equals("lore") && args.length >= 3) { handleLore(player, args[1], args[2]); return true; }
        if (sub.equals("paleta") && args.length >= 3 && args[1].equalsIgnoreCase("aplicar")) { applyPalette(player, args[2]); return true; }
        if (sub.equals("encantamientos") && args.length >= 3) { handleEnchantments(player, args[1], args[2]); return true; }
        if (sub.equals("recargar")) { initializeTemplates(); player.sendMessage(PREFIX + "Plantillas predeterminadas comprobadas."); return true; }
        player.sendMessage("§bIxKits §7— comandos:");
        player.sendMessage("§f/ixkits §8| §7Abrir editor");
        player.sendMessage("§f/ixkits crear <nombre> §8| §7Crear proyecto");
        player.sendMessage("§f/ixkits editar <proyecto> §8| §7Editar proyecto");
        player.sendMessage("§f/ixkits proyectos §8| §7Biblioteca local");
        player.sendMessage("§f/ixkits plantillas §8| §7Plantillas locales");
        player.sendMessage("§f/ixkits exportar <proyecto> §8| §7Exportar proyecto IxKits");
        player.sendMessage("§f/ixkits lore guardar|aplicar <nombre>");
        player.sendMessage("§f/ixkits paleta aplicar <nombre>");
        player.sendMessage("§f/ixkits encantamientos guardar|aplicar <nombre>");
        return true;
    }

    private void openMain(Player p) {
        Inventory inv = menu("main", null, 0, "§3✦ §bIxKits §3• §8Studio");
        border(inv);
        inv.setItem(20, item(Material.CHEST, "§bMis proyectos", "§7Crear, abrir y editar kits.", "§8Proyectos locales"));
        inv.setItem(22, item(Material.BOOKSHELF, "§bBiblioteca de plantillas", "§7Lore, paletas y encantamientos.", "§8/ixkits plantillas"));
        inv.setItem(24, item(Material.WRITABLE_BOOK, "§bExportadores", "§7Generar archivos editables.", "§8Selecciona un proyecto"));
        inv.setItem(30, item(Material.ENDER_CHEST, "§dIxKits Community", "§7Catálogo comunitario online.", "§cRequiere el servicio web de IxKits"));
        inv.setItem(32, item(Material.COMPASS, "§fAyuda y comandos", "§7/ixkits crear <nombre>", "§7/ixkits exportar <proyecto>"));
        p.openInventory(inv);
    }

    private void openProjects(Player p) {
        Inventory inv = menu("projects", null, 0, "§3✦ §bIxKits §8• §0Mis proyectos");
        border(inv);
        File[] files = projectsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
            for (int i=0; i<Math.min(files.length, slots.length); i++) {
                String id = files[i].getName().replaceFirst("\\.yml$", "");
                YamlConfiguration y = YamlConfiguration.loadConfiguration(files[i]);
                inv.setItem(slots[i], item(Material.CHEST, "§b" + y.getString("name", id),
                    "§7Proyecto editable", "§8ID: " + id, "§eClic para abrir"));
            }
        }
        inv.setItem(45, item(Material.ARROW, "§7Volver", "§8Menú principal"));
        inv.setItem(49, item(Material.PAPER, "§aCrear proyecto", "§7Usa §f/ixkits crear <nombre>"));
        p.openInventory(inv);
    }

    private void openTemplates(Player p) {
        Inventory inv = menu("templates", null, 0, "§3✦ §bIxKits §8• §0Biblioteca");
        border(inv);
        inv.setItem(20, item(Material.WRITABLE_BOOK, "§bPlantillas de lore", "§7Legendario, épico, minería y más.", "§e/ixkits lore aplicar <nombre>"));
        inv.setItem(22, item(Material.PAINTING, "§bPaletas de colores", "§7Ocean, royal y inferno.", "§e/ixkits paleta aplicar <nombre>"));
        inv.setItem(24, item(Material.ENCHANTED_BOOK, "§bPresets de encantamientos", "§7PvP, minería y protección.", "§e/ixkits encantamientos aplicar <nombre>"));
        inv.setItem(30, item(Material.ENDER_CHEST, "§dCommunity", "§7Catálogo online en desarrollo.", "§cNo realiza descargas en esta versión"));
        inv.setItem(45, item(Material.ARROW, "§7Volver", "§8Menú principal"));
        p.openInventory(inv);
    }

    private void openEditor(Player p, String project, int page) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(project));
        String display = y.getString("name", project);
        Inventory inv = menu("editor", project, page, "§3✦ §bIxKits §8• §0" + display + " §7[" + (page+1) + "/2]");
        border(inv);
        for (int slot : EDIT_SLOTS) {
            ItemStack stored = y.getItemStack("pages." + page + "." + slot);
            if (stored != null) inv.setItem(slot, stored.clone());
        }
        inv.setItem(45, item(Material.ARROW, "§7Proyectos", "§8Volver a la biblioteca"));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, "§bPágina anterior", "§7Página " + Math.max(1, page)));
        inv.setItem(49, item(Material.LIME_CONCRETE, "§aGuardar y salir", "§7Guardar los cambios del proyecto"));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, "§bPágina siguiente", "§7Página " + Math.min(2, page+2)));
        inv.setItem(53, item(Material.WRITABLE_BOOK, "§dExportar", "§7Generar archivo .ixkit.yml"));
        p.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
        int slot = e.getRawSlot();
        if (h.kind.equals("editor")) {
            if (slot >= e.getView().getTopInventory().getSize()) {
                if (e.isShiftClick()) e.setCancelled(true);
                return;
            }
            if (contains(EDIT_SLOTS, slot)) return;
            e.setCancelled(true);
            if (slot == 45) { savePage(e.getView().getTopInventory(), h); openProjects(p); }
            else if (slot == 47) { savePage(e.getView().getTopInventory(), h); openEditor(p, h.project, Math.max(0, h.page-1)); }
            else if (slot == 51) { savePage(e.getView().getTopInventory(), h); openEditor(p, h.project, Math.min(1, h.page+1)); }
            else if (slot == 49) { savePage(e.getView().getTopInventory(), h); p.closeInventory(); p.sendMessage(PREFIX + "Proyecto guardado."); }
            else if (slot == 53) { savePage(e.getView().getTopInventory(), h); exportProject(p, h.project); }
            return;
        }
        e.setCancelled(true);
        if (h.kind.equals("main")) {
            if (slot == 20) openProjects(p);
            else if (slot == 22) openTemplates(p);
            else if (slot == 24) { p.sendMessage(PREFIX + "Abre Mis proyectos y pulsa Exportar."); openProjects(p); }
            else if (slot == 30) p.sendMessage(PREFIX + "El catálogo comunitario necesita el servicio web, aún no conectado.");
        } else if (h.kind.equals("projects")) {
            if (slot == 45) openMain(p);
            else if (slot == 49) p.sendMessage(PREFIX + "Crea un proyecto con §f/ixkits crear <nombre>§7.");
            else if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST) {
                String title = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                File[] files = projectsDir.listFiles((d,n)->n.endsWith(".yml"));
                if (files != null) for (File f : files) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                    if (ChatColor.stripColor(y.getString("name", f.getName())).equalsIgnoreCase(title)) {
                        openEditor(p, f.getName().replaceFirst("\\.yml$", ""), 0); break;
                    }
                }
            }
        } else if (h.kind.equals("templates")) {
            if (slot == 45) openMain(p);
            else if (slot == 20) p.sendMessage(PREFIX + "Lore: legendario, epico, mineria, armadura.");
            else if (slot == 22) p.sendMessage(PREFIX + "Paletas: ocean, royal, inferno.");
            else if (slot == 24) p.sendMessage(PREFIX + "Encantamientos: pvp_sword, mining_pickaxe, protection.");
            else if (slot == 30) p.sendMessage(PREFIX + "El catálogo online todavía no está conectado.");
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h) || !h.kind.equals("editor")) return;
        for (int slot : e.getRawSlots()) {
            if (slot < e.getView().getTopInventory().getSize() && !contains(EDIT_SLOTS, slot)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MenuHolder h && h.kind.equals("editor")) savePage(e.getInventory(), h);
    }

    private void savePage(Inventory inv, MenuHolder h) {
        File f = projectFile(h.project);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("version", 1);
        y.set("updated", System.currentTimeMillis());
        for (int slot : EDIT_SLOTS) y.set("pages." + h.page + "." + slot, inv.getItem(slot));
        saveYaml(y, f);
    }

    private void exportProject(Player p, String project) {
        File f = projectFile(project);
        if (!f.exists()) { p.sendMessage(PREFIX + "Proyecto no encontrado."); return; }
        File out = new File(exportsDir, project + ".ixkit.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("ixkits-export.version", 1);
        y.set("ixkits-export.source", project);
        saveYaml(y, out);
        p.sendMessage(PREFIX + "Exportado a §bplugins/IxKits/exports/" + out.getName());
        p.sendMessage(PREFIX + "Formato nativo IxKits; los exportadores para plugins externos se ampliarán en futuras versiones.");
    }

    private void handleLore(Player p, String action, String name) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) { p.sendMessage(PREFIX + "Sostén primero el objeto que quieres editar."); return; }
        File file = new File(templatesDir, "lore.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (action.equalsIgnoreCase("guardar")) {
            List<String> lore = item.getItemMeta().getLore();
            if (lore == null) { p.sendMessage(PREFIX + "El objeto no tiene lore."); return; }
            y.set(name + ".lore", lore); saveYaml(y, file);
            p.sendMessage(PREFIX + "Lore guardado como §b" + name); return;
        }
        List<String> lore = y.getStringList(name + ".lore");
        if (lore.isEmpty()) { p.sendMessage(PREFIX + "No existe esa plantilla de lore."); return; }
        ItemMeta meta = item.getItemMeta(); meta.setLore(color(lore)); item.setItemMeta(meta);
        p.sendMessage(PREFIX + "Plantilla aplicada: §b" + name);
    }

    private void applyPalette(Player p, String name) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) { p.sendMessage(PREFIX + "Sostén un objeto primero."); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(templatesDir, "palettes.yml"));
        String color = y.getString(name + ".color");
        if (color == null) { p.sendMessage(PREFIX + "No existe esa paleta."); return; }
        ItemMeta meta = item.getItemMeta();
        String display = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', color + display.replaceAll("§[0-9a-fk-or]", "")));
        if (meta.hasLore()) meta.setLore(color(meta.getLore()).stream().map(line -> ChatColor.translateAlternateColorCodes('&', color + ChatColor.stripColor(line))).toList());
        item.setItemMeta(meta);
        p.sendMessage(PREFIX + "Paleta aplicada: §b" + name);
    }

    private void handleEnchantments(Player p, String action, String name) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) { p.sendMessage(PREFIX + "Sostén un objeto primero."); return; }
        File file = new File(templatesDir, "enchantments.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (action.equalsIgnoreCase("guardar")) {
            Map<String,Integer> enchants = new TreeMap<>();
            item.getEnchantments().forEach((ench, level) -> enchants.put(ench.getKey().getKey(), level));
            y.set(name, enchants); saveYaml(y, file);
            p.sendMessage(PREFIX + "Preset de encantamientos guardado: §b" + name); return;
        }
        ConfigurationSection section = y.getConfigurationSection(name);
        if (section == null) { p.sendMessage(PREFIX + "No existe ese preset."); return; }
        for (String key : section.getKeys(false)) {
            Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (ench != null) item.addUnsafeEnchantment(ench, Math.max(1, section.getInt(key, 1)));
        }
        p.sendMessage(PREFIX + "Encantamientos aplicados: §b" + name);
    }

    private void initializeTemplates() {
        File loreFile = new File(templatesDir, "lore.yml");
        if (!loreFile.exists()) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("legendario.lore", List.of("&6✦ &eObjeto legendario", "&8&m----------------", "&7Una pieza de poder ancestral.", "&eRareza: &6LEGENDARIA", "&8&m----------------"));
            y.set("epico.lore", List.of("&5✦ &dObjeto épico", "&8&m----------------", "&7Forjado para aventureros.", "&dRareza: &5ÉPICA"));
            y.set("mineria.lore", List.of("&b⛏ Herramienta minera", "&8&m----------------", "&7Eficiencia mejorada.", "&bUso: &fMinería"));
            y.set("armadura.lore", List.of("&3✦ &bArmadura protectora", "&8&m----------------", "&7Diseñada para resistir.", "&bTipo: &3Defensa"));
            saveYaml(y, loreFile);
        }
        File paletteFile = new File(templatesDir, "palettes.yml");
        if (!paletteFile.exists()) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("ocean.color", "&b"); y.set("royal.color", "&5"); y.set("inferno.color", "&c"); y.set("gold.color", "&6");
            saveYaml(y, paletteFile);
        }
        File enchantFile = new File(templatesDir, "enchantments.yml");
        if (!enchantFile.exists()) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("pvp_sword.sharpness", 5); y.set("pvp_sword.unbreaking", 3); y.set("pvp_sword.fire_aspect", 2);
            y.set("mining_pickaxe.efficiency", 5); y.set("mining_pickaxe.unbreaking", 3); y.set("mining_pickaxe.fortune", 3);
            y.set("protection.protection", 4); y.set("protection.unbreaking", 3);
            saveYaml(y, enchantFile);
        }
    }

    private Inventory menu(String kind, String project, int page, String title) {
        MenuHolder h = new MenuHolder(kind, project, page);
        Inventory inv = Bukkit.createInventory(h, 54, title);
        h.inventory = inv; return inv;
    }
    private void border(Inventory inv) {
        ItemStack glass = item(Material.CYAN_STAINED_GLASS_PANE, "§3 ", "§8IxKits Studio");
        for (int i=0;i<54;i++) if (i<9 || i>=45 || i%9==0 || i%9==8) inv.setItem(i, glass);
    }
    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta); return stack;
    }
    private boolean contains(int[] arr, int value) { for (int n : arr) if (n == value) return true; return false; }
    private List<String> color(List<String> lines) { return lines.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).toList(); }
    private String slug(String in) { return in.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-").replaceAll("-+", "-"); }
    private File projectFile(String name) { return new File(projectsDir, slug(name) + ".yml"); }
    private void saveYaml(YamlConfiguration yaml, File file) {
        try { File parent=file.getParentFile(); if(parent!=null) parent.mkdirs(); yaml.save(file); }
        catch (IOException ex) { getLogger().severe("No se pudo guardar " + file.getName() + ": " + ex.getMessage()); }
    }

    private static final class MenuHolder implements InventoryHolder {
        final String kind, project; final int page; Inventory inventory;
        MenuHolder(String kind, String project, int page) { this.kind=kind; this.project=project; this.page=page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}