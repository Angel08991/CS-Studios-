package com.csstudios.ixkits;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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
        saveDefaultConfig();
        reloadConfig();
        if (!getConfig().contains("language")) { getConfig().set("language", "es"); saveConfig(); }
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
            player.sendMessage(PREFIX + tr("No tienes permiso para administrar IxKits.", "You do not have permission to manage IxKits."));
            return true;
        }
        if (args.length == 0) { openMain(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("idioma") || sub.equals("language")) {
            if (args.length >= 2) setLanguage(player, args[1]);
            else openLanguages(player);
            return true;
        }
        if (sub.equals("crear") && args.length >= 2) {
            String name = slug(String.join("-", Arrays.copyOfRange(args, 1, args.length)));
            if (name.isBlank()) { player.sendMessage(PREFIX + tr("Nombre no válido.", "Invalid project name.")); return true; }
            File f = projectFile(name);
            if (f.exists()) { player.sendMessage(PREFIX + tr("Ya existe un proyecto con ese nombre.", "A project with that name already exists.")); return true; }
            YamlConfiguration y = new YamlConfiguration();
            y.set("name", String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            y.set("version", 1);
            saveYaml(y, f);
            player.sendMessage(PREFIX + tr("Proyecto creado: §b", "Project created: §b") + name);
            openEditor(player, name, 0);
            return true;
        }
        if (sub.equals("editar") && args.length >= 2) {
            String name = slug(args[1]);
            if (!projectFile(name).exists()) player.sendMessage(PREFIX + tr("No existe ese proyecto.", "That project does not exist."));
            else openEditor(player, name, 0);
            return true;
        }
        if (sub.equals("proyectos")) { openProjects(player); return true; }
        if (sub.equals("plantillas")) { openTemplates(player); return true; }
        if (sub.equals("exportar") && args.length >= 2) { exportProject(player, slug(args[1])); return true; }
        if (sub.equals("lore") && args.length >= 3) { handleLore(player, args[1], args[2]); return true; }
        if (sub.equals("paleta") && args.length >= 3 && args[1].equalsIgnoreCase("aplicar")) { applyPalette(player, args[2]); return true; }
        if (sub.equals("encantamientos") && args.length >= 3) { handleEnchantments(player, args[1], args[2]); return true; }
        if (sub.equals("recargar")) { reloadConfig(); initializeTemplates(); player.sendMessage(PREFIX + tr("Configuración y plantillas recargadas.", "Configuration and templates reloaded.")); return true; }
        player.sendMessage(tr("§bIxKits §7— comandos:", "§bIxKits §7— commands:"));
        player.sendMessage(tr("§f/ixkits §8| §7Abrir editor", "§f/ixkits §8| §7Open studio"));
        player.sendMessage(tr("§f/ixkits crear <nombre> §8| §7Crear proyecto", "§f/ixkits crear <name> §8| §7Create project"));
        player.sendMessage(tr("§f/ixkits editar <proyecto> §8| §7Editar proyecto", "§f/ixkits editar <project> §8| §7Edit project"));
        player.sendMessage(tr("§f/ixkits proyectos §8| §7Biblioteca local", "§f/ixkits proyectos §8| §7Project library"));
        player.sendMessage(tr("§f/ixkits plantillas §8| §7Plantillas locales", "§f/ixkits plantillas §8| §7Local templates"));
        player.sendMessage(tr("§f/ixkits idioma §8| §7Cambiar idioma", "§f/ixkits idioma §8| §7Change language"));
        player.sendMessage(tr("§f/ixkits exportar <proyecto> §8| §7Exportar proyecto IxKits", "§f/ixkits exportar <project> §8| §7Export IxKits project"));
        player.sendMessage(tr("§f/ixkits lore guardar|aplicar <nombre>", "§f/ixkits lore guardar|aplicar <name>"));
        player.sendMessage(tr("§f/ixkits paleta aplicar <nombre>", "§f/ixkits paleta aplicar <name>"));
        player.sendMessage(tr("§f/ixkits encantamientos guardar|aplicar <nombre>", "§f/ixkits encantamientos guardar|aplicar <name>"));
        return true;
    }

    private void openMain(Player p) {
        Inventory inv = menu("main", null, 0, tr("§3✦ §bIxKits §3• §8Estudio", "§3✦ §bIxKits §3• §8Studio"));
        border(inv);
        inv.setItem(20, item(Material.CHEST, tr("§bMis proyectos", "§bMy projects"), tr("§7Crear, abrir y editar kits.", "§7Create, open and edit kits."), tr("§8Proyectos locales", "§8Local projects")));
        inv.setItem(22, item(Material.BOOKSHELF, tr("§bBiblioteca de plantillas", "§bTemplate library"), tr("§7Lore, paletas y encantamientos.", "§7Lore, palettes and enchantments."), tr("§8Plantillas incluidas", "§8Built-in templates")));
        inv.setItem(24, item(Material.WRITABLE_BOOK, tr("§bExportadores", "§bExport tools"), tr("§7Generar archivos editables.", "§7Generate editable files."), tr("§8Selecciona un proyecto", "§8Choose a project")));
        inv.setItem(30, item(Material.ENDER_CHEST, "§dIxKits Community", tr("§7Catálogo comunitario online.", "§7Online community catalog."), tr("§cServicio web aún no conectado", "§cWeb service not connected yet")));
        inv.setItem(32, item(Material.COMPASS, tr("§fAyuda y comandos", "§fHelp and commands"), "§7/ixkits crear <nombre>", "§7/ixkits idioma"));
        inv.setItem(40, item(Material.WRITABLE_BOOK, tr("§eIdioma / Language", "§eLanguage / Idioma"), tr("§7Actual: Español", "§7Current: English"), tr("§eClic para cambiar", "§eClick to change")));
        p.openInventory(inv);
    }

    private void openProjects(Player p) {
        Inventory inv = menu("projects", null, 0, tr("§3✦ §bIxKits §8• §0Mis proyectos", "§3✦ §bIxKits §8• §0My projects"));
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        File[] files = projectsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            int[] slots = EDIT_SLOTS;
            for (int i=0; i<Math.min(files.length, slots.length); i++) {
                String id = files[i].getName().replaceFirst("\\.yml$", "");
                YamlConfiguration y = YamlConfiguration.loadConfiguration(files[i]);
                inv.setItem(slots[i], item(Material.CHEST, "§b" + y.getString("name", id),
                    tr("§7Proyecto editable", "§7Editable project"), "§8ID: " + id, tr("§eClic para abrir", "§eClick to open")));
                holder.entryBySlot.put(slots[i], id);
            }
        }
        if (files == null || files.length == 0) {
            inv.setItem(22, item(Material.PAPER, tr("§7Todavía no hay proyectos", "§7No projects yet"), tr("§eUsa /ixkits crear <nombre>", "§eUse /ixkits crear <name>")));
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Menú principal", "§8Main menu")));
        inv.setItem(49, item(Material.LIME_CONCRETE, tr("§aCrear proyecto", "§aCreate project"), tr("§7Usa /ixkits crear <nombre>", "§7Use /ixkits crear <name>")));
        p.openInventory(inv);
    }

    private void openTemplates(Player p) {
        Inventory inv = menu("templates", null, 0, tr("§3✦ §bIxKits §8• §0Biblioteca", "§3✦ §bIxKits §8• §0Library"));
        border(inv);
        inv.setItem(20, item(Material.WRITABLE_BOOK, tr("§bPlantillas de lore", "§bLore templates"), tr("§7Tres diseños profesionales incluidos.", "§7Three professional designs included."), tr("§eClic para elegir una plantilla", "§eClick to choose a template")));
        inv.setItem(22, item(Material.PAINTING, tr("§bPaletas de colores", "§bColor palettes"), tr("§7Ocean, royal e inferno.", "§7Ocean, royal and inferno."), tr("§8/ixkits paleta aplicar <nombre>", "§8/ixkits paleta aplicar <name>")));
        inv.setItem(24, item(Material.ENCHANTED_BOOK, tr("§bPresets de encantamientos", "§bEnchantment presets"), tr("§7Guarda y reutiliza combinaciones.", "§7Save and reuse combinations."), tr("§ePara kits: clic derecho a un objeto", "§eFor kits: right-click an item")));
        inv.setItem(30, item(Material.ENDER_CHEST, "§dCommunity", tr("§7Catálogo online en desarrollo.", "§7Online catalog is in development."), tr("§cLas descargas aún no están activas", "§cDownloads are not available yet")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Menú principal", "§8Main menu")));
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
        inv.setItem(45, item(Material.ARROW, tr("§7Proyectos", "§7Projects"), tr("§8Volver a la biblioteca", "§8Back to library")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bPágina anterior", "§bPrevious page"), tr("§7Página ", "§7Page ") + Math.max(1, page)));
        inv.setItem(49, item(Material.LIME_CONCRETE, tr("§aGuardar y salir", "§aSave and exit"), tr("§7Guardar los cambios del proyecto", "§7Save project changes")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bPágina siguiente", "§bNext page"), tr("§7Página ", "§7Page ") + Math.min(2, page+2)));
        inv.setItem(46, item(Material.ENCHANTED_BOOK, tr("§bEditar objeto", "§bEdit item"), tr("§7Clic derecho sobre cualquier objeto", "§7Right-click any item"), tr("§7para abrir su editor.", "§7to open its editor.")));
        inv.setItem(53, item(Material.WRITABLE_BOOK, tr("§dExportar", "§dExport"), tr("§7Generar archivo .ixkit.yml", "§7Generate .ixkit.yml file")));
        p.openInventory(inv);
    }

    private void openLanguages(Player p) {
        Inventory inv = menu("language", null, 0, tr("§3✦ §bIxKits §8• §0Idioma", "§3✦ §bIxKits §8• §0Language"));
        border(inv);
        inv.setItem(21, item(Material.WRITABLE_BOOK, "§bEnglish", tr("§7Idioma inglés", "§7English language"), isEnglish() ? "§a✓ " + tr("Seleccionado", "Selected") : "§e" + tr("Clic para elegir", "Click to select")));
        inv.setItem(23, item(Material.WRITABLE_BOOK, "§eEspañol", tr("§7Idioma español", "§7Spanish language"), !isEnglish() ? "§a✓ " + tr("Seleccionado", "Selected") : "§e" + tr("Clic para elegir", "Click to select")));
        inv.setItem(31, item(Material.MAP, tr("§fConfiguración guardada", "§fSaved setting"), tr("§7Se aplica a todo el plugin.", "§7Applies to the whole plugin."), tr("§7También puedes editar config.yml", "§7You can also edit config.yml"), "§b language: en / es"));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Menú principal", "§8Main menu")));
        p.openInventory(inv);
    }

    private void openLoreTemplates(Player p, int page) {
        YamlConfiguration lore = YamlConfiguration.loadConfiguration(new File(templatesDir, "lore.yml"));
        List<String> keys = new ArrayList<>();
        for (String key : lore.getKeys(false)) if (!lore.getStringList(key + ".lore").isEmpty()) keys.add(key);
        Collections.sort(keys);
        int pages = Math.max(1, (int) Math.ceil(keys.size() / (double) EDIT_SLOTS.length));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = menu("lore-templates", null, safePage, tr("§3✦ §bIxKits §8• §0Plantillas de lore", "§3✦ §bIxKits §8• §0Lore templates") + " §7[" + (safePage + 1) + "/" + pages + "]");
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        for (int i = safePage * EDIT_SLOTS.length; i < Math.min(keys.size(), (safePage + 1) * EDIT_SLOTS.length); i++) {
            String key = keys.get(i);
            int slot = EDIT_SLOTS[i - safePage * EDIT_SLOTS.length];
            String display = lore.getString(key + (isEnglish() ? ".display_en" : ".display"), lore.getString(key + ".display", prettyKey(key)));
            List<String> preview = new ArrayList<>();
            List<String> configured = lore.getStringList(key + (isEnglish() ? ".lore_en" : ".lore"));
            if (configured.isEmpty()) configured = lore.getStringList(key + ".lore");
            for (String line : configured.stream().limit(4).toList()) preview.add(ChatColor.translateAlternateColorCodes('&', line));
            preview.add("");
            preview.add(tr("§eClic para aplicar al objeto en mano", "§eClick to apply to item in hand"));
            inv.setItem(slot, itemWithLore(Material.WRITABLE_BOOK, ChatColor.translateAlternateColorCodes('&', display), preview));
            holder.entryBySlot.put(slot, key);
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Biblioteca de plantillas", "§8Template library")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious"), tr("§7Página anterior", "§7Previous page")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext"), tr("§7Página siguiente", "§7Next page")));
        p.openInventory(inv);
    }

    private void openItemEditor(Player p, String project, int page, int targetSlot) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(project));
        ItemStack target = y.getItemStack("pages." + page + "." + targetSlot);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(PREFIX + tr("Ese espacio no contiene ningún objeto.", "That slot does not contain an item."));
            openEditor(p, project, page);
            return;
        }
        ItemMeta targetMeta = target.getItemMeta();
        String displayName = targetMeta != null && targetMeta.hasDisplayName() ? ChatColor.stripColor(targetMeta.getDisplayName()) : prettyKey(target.getType().name());
        Inventory inv = menu("item-editor", project, page, targetSlot, 0, tr("§3✦ §bIxKits §8• §0Editor de objeto", "§3✦ §bIxKits §8• §0Item editor"));
        border(inv);
        inv.setItem(4, target.clone());
        inv.setItem(20, item(Material.ENCHANTED_BOOK, tr("§bEncantamientos", "§bEnchantments"),
            tr("§7Añadir, subir o quitar niveles.", "§7Add, raise or lower levels."),
            tr("§eClic para abrir el editor", "§eClick to open the editor")));
        List<String> enchantLore = new ArrayList<>();
        enchantLore.add(tr("§7Objeto: §f", "§7Item: §f") + displayName);
        if (target.getEnchantments().isEmpty()) enchantLore.add(tr("§8Sin encantamientos", "§8No enchantments"));
        else target.getEnchantments().entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString())).forEach(entry ->
            enchantLore.add("§b• §f" + prettyKey(entry.getKey().getKey().getKey()) + " " + romanLevel(entry.getValue())));
        inv.setItem(22, item(Material.PAPER, tr("§fDetalles del objeto", "§fItem details"), enchantLore.toArray(new String[0])));
        inv.setItem(24, item(Material.BARRIER, tr("§cQuitar todos los encantamientos", "§cRemove all enchantments"), tr("§7Solo afecta a este objeto del proyecto.", "§7Only affects this project item."), tr("§eClic para confirmar al instante", "§eClick to apply immediately")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al proyecto", "§7Back to project")));
        p.openInventory(inv);
    }

    private void openEnchantments(Player p, String project, int projectPage, int targetSlot, int enchantPage) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(project));
        ItemStack target = y.getItemStack("pages." + projectPage + "." + targetSlot);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(PREFIX + tr("No se encontró el objeto del proyecto.", "Could not find the project item."));
            openEditor(p, project, projectPage);
            return;
        }
        List<Enchantment> enchantments = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) enchantments.add(enchantment);
        enchantments.sort(Comparator.comparing(enchantment -> enchantment.getKey().toString()));
        int pages = Math.max(1, (int) Math.ceil(enchantments.size() / (double) EDIT_SLOTS.length));
        int safePage = Math.max(0, Math.min(enchantPage, pages - 1));
        Inventory inv = menu("enchantments", project, projectPage, targetSlot, safePage,
            tr("§3✦ §bIxKits §8• §0Encantamientos", "§3✦ §bIxKits §8• §0Enchantments") + " §7[" + (safePage + 1) + "/" + pages + "]");
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        for (int i = safePage * EDIT_SLOTS.length; i < Math.min(enchantments.size(), (safePage + 1) * EDIT_SLOTS.length); i++) {
            Enchantment enchantment = enchantments.get(i);
            String key = enchantment.getKey().toString();
            int slot = EDIT_SLOTS[i - safePage * EDIT_SLOTS.length];
            int level = target.getEnchantmentLevel(enchantment);
            List<String> lines = new ArrayList<>();
            lines.add(tr("§7Nivel: ", "§7Level: ") + (level <= 0 ? tr("§cNo aplicado", "§cNot applied") : "§b" + level) + " §8/ §7" + enchantment.getMaxLevel());
            lines.add("");
            lines.add(tr("§aClic izquierdo: subir nivel", "§aLeft-click: increase level"));
            lines.add(tr("§eClic derecho: bajar nivel", "§eRight-click: decrease level"));
            lines.add(tr("§cMayús + clic: eliminar", "§cShift-click: remove"));
            inv.setItem(slot, itemWithLore(Material.ENCHANTED_BOOK, "§b" + prettyKey(enchantment.getKey().getKey()), lines));
            holder.entryBySlot.put(slot, key);
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al objeto", "§7Back to item")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious"), tr("§7Página anterior", "§7Previous page")));
        inv.setItem(49, item(Material.BARRIER, tr("§cQuitar todos", "§cRemove all"), tr("§7Eliminar todos los encantamientos", "§7Remove all enchantments")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext"), tr("§7Página siguiente", "§7Next page")));
        inv.setItem(53, item(Material.CHEST, tr("§aVolver al editor", "§aBack to editor"), tr("§7Vista del objeto", "§7Item overview")));
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
            if (slot >= 0 && contains(EDIT_SLOTS, slot)) {
                ItemStack clicked = e.getCurrentItem();
                if (e.isRightClick() && clicked != null && !clicked.getType().isAir()) {
                    e.setCancelled(true);
                    savePage(e.getView().getTopInventory(), h);
                    openItemEditor(p, h.project, h.page, slot);
                }
                return;
            }
            e.setCancelled(true);
            if (slot == 45) { savePage(e.getView().getTopInventory(), h); openProjects(p); }
            else if (slot == 47) { savePage(e.getView().getTopInventory(), h); openEditor(p, h.project, Math.max(0, h.page-1)); }
            else if (slot == 51) { savePage(e.getView().getTopInventory(), h); openEditor(p, h.project, Math.min(1, h.page+1)); }
            else if (slot == 49) { savePage(e.getView().getTopInventory(), h); p.closeInventory(); p.sendMessage(PREFIX + tr("Proyecto guardado.", "Project saved.")); }
            else if (slot == 53) { savePage(e.getView().getTopInventory(), h); exportProject(p, h.project); }
            return;
        }

        e.setCancelled(true);
        if (h.kind.equals("main")) {
            if (slot == 20) openProjects(p);
            else if (slot == 22) openTemplates(p);
            else if (slot == 24) { p.sendMessage(PREFIX + tr("Abre Mis proyectos y pulsa Exportar.", "Open My projects and choose Export.")); openProjects(p); }
            else if (slot == 30) p.sendMessage(PREFIX + tr("El catálogo comunitario necesita el servicio web, aún no conectado.", "The community catalog needs its web service, which is not connected yet."));
            else if (slot == 32) {
                p.sendMessage(tr("§bIxKits §7— /ixkits crear <nombre> · /ixkits proyectos · /ixkits idioma", "§bIxKits §7— /ixkits crear <name> · /ixkits proyectos · /ixkits idioma"));
            } else if (slot == 40) openLanguages(p);
        } else if (h.kind.equals("language")) {
            if (slot == 45) openMain(p);
            else if (slot == 21) setLanguage(p, "en");
            else if (slot == 23) setLanguage(p, "es");
        } else if (h.kind.equals("projects")) {
            if (slot == 45) openMain(p);
            else if (slot == 49) p.sendMessage(PREFIX + tr("Crea un proyecto con §f/ixkits crear <nombre>§7.", "Create a project with §f/ixkits crear <name>§7."));
            else if (h.entryBySlot.containsKey(slot)) openEditor(p, h.entryBySlot.get(slot), 0);
        } else if (h.kind.equals("templates")) {
            if (slot == 45) openMain(p);
            else if (slot == 20) openLoreTemplates(p, 0);
            else if (slot == 22) p.sendMessage(PREFIX + tr("Paletas: ocean, royal, inferno. Usa /ixkits paleta aplicar <nombre> sosteniendo un objeto.", "Palettes: ocean, royal, inferno. Hold an item and use /ixkits paleta aplicar <name>."));
            else if (slot == 24) p.sendMessage(PREFIX + tr("Para editar encantamientos de un kit: abre un proyecto y haz clic derecho sobre un objeto.", "To edit a kit's enchantments: open a project and right-click an item."));
            else if (slot == 30) p.sendMessage(PREFIX + tr("El catálogo online todavía no está conectado.", "The online catalog is not connected yet."));
        } else if (h.kind.equals("lore-templates")) {
            if (slot == 45) openTemplates(p);
            else if (slot == 47) openLoreTemplates(p, Math.max(0, h.page - 1));
            else if (slot == 51) openLoreTemplates(p, h.page + 1);
            else if (h.entryBySlot.containsKey(slot)) applyLoreTemplateToHand(p, h.entryBySlot.get(slot));
        } else if (h.kind.equals("item-editor")) {
            if (slot == 45) openEditor(p, h.project, h.page);
            else if (slot == 20) openEnchantments(p, h.project, h.page, h.targetSlot, 0);
            else if (slot == 24) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(h.project));
                ItemStack target = y.getItemStack("pages." + h.page + "." + h.targetSlot);
                if (target != null) {
                    for (Enchantment enchantment : new ArrayList<>(target.getEnchantments().keySet())) target.removeEnchantment(enchantment);
                    y.set("pages." + h.page + "." + h.targetSlot, target);
                    saveYaml(y, projectFile(h.project));
                    p.sendMessage(PREFIX + tr("Se quitaron todos los encantamientos del objeto.", "All enchantments were removed from the item."));
                }
                openItemEditor(p, h.project, h.page, h.targetSlot);
            }
        } else if (h.kind.equals("enchantments")) {
            if (slot == 45) openItemEditor(p, h.project, h.page, h.targetSlot);
            else if (slot == 47) openEnchantments(p, h.project, h.page, h.targetSlot, Math.max(0, h.subPage - 1));
            else if (slot == 51) openEnchantments(p, h.project, h.page, h.targetSlot, h.subPage + 1);
            else if (slot == 53) openEditor(p, h.project, h.page);
            else if (slot == 49) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(h.project));
                ItemStack target = y.getItemStack("pages." + h.page + "." + h.targetSlot);
                if (target != null) {
                    for (Enchantment enchantment : new ArrayList<>(target.getEnchantments().keySet())) target.removeEnchantment(enchantment);
                    y.set("pages." + h.page + "." + h.targetSlot, target);
                    saveYaml(y, projectFile(h.project));
                    p.sendMessage(PREFIX + tr("Se quitaron todos los encantamientos del objeto.", "All enchantments were removed from the item."));
                }
                openEnchantments(p, h.project, h.page, h.targetSlot, h.subPage);
            } else if (h.entryBySlot.containsKey(slot)) {
                String rawKey = h.entryBySlot.get(slot);
                NamespacedKey namespacedKey = NamespacedKey.fromString(rawKey);
                Enchantment enchantment = namespacedKey == null ? null : Registry.ENCHANTMENT.get(namespacedKey);
                if (enchantment == null) {
                    p.sendMessage(PREFIX + tr("No se pudo cargar ese encantamiento.", "Could not load that enchantment."));
                    return;
                }
                YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(h.project));
                String itemPath = "pages." + h.page + "." + h.targetSlot;
                ItemStack target = y.getItemStack(itemPath);
                if (target == null || target.getType().isAir()) {
                    p.sendMessage(PREFIX + tr("El objeto del proyecto ya no existe.", "The project item no longer exists."));
                    openEditor(p, h.project, h.page);
                    return;
                }
                int current = target.getEnchantmentLevel(enchantment);
                if (e.isShiftClick()) target.removeEnchantment(enchantment);
                else if (e.isRightClick()) {
                    if (current <= 1) target.removeEnchantment(enchantment);
                    else target.addUnsafeEnchantment(enchantment, current - 1);
                } else {
                    int next = current <= 0 || current >= enchantment.getMaxLevel() ? 1 : current + 1;
                    target.addUnsafeEnchantment(enchantment, next);
                }
                y.set(itemPath, target);
                saveYaml(y, projectFile(h.project));
                openEnchantments(p, h.project, h.page, h.targetSlot, h.subPage);
            }
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
        if (!h.kind.equals("editor")) {
            e.setCancelled(true);
            return;
        }
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
        if (item.getType().isAir()) { p.sendMessage(PREFIX + tr("Sostén primero el objeto que quieres editar.", "Hold the item you want to edit first.")); return; }
        File file = new File(templatesDir, "lore.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (action.equalsIgnoreCase("guardar")) {
            List<String> lore = item.getItemMeta().getLore();
            if (lore == null) { p.sendMessage(PREFIX + tr("El objeto no tiene lore.", "The item has no lore.")); return; }
            y.set(name + ".lore", lore); saveYaml(y, file);
            p.sendMessage(PREFIX + tr("Lore guardado como §b", "Lore saved as §b") + name); return;
        }
        List<String> lore = y.getStringList(name + ".lore");
        if (lore.isEmpty()) { p.sendMessage(PREFIX + tr("No existe esa plantilla de lore.", "That lore template does not exist.")); return; }
        ItemMeta meta = item.getItemMeta(); meta.setLore(color(lore)); item.setItemMeta(meta);
        p.sendMessage(PREFIX + tr("Plantilla aplicada: §b", "Template applied: §b") + name);
    }

    private void applyPalette(Player p, String name) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) { p.sendMessage(PREFIX + tr("Sostén un objeto primero.", "Hold an item first.")); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(templatesDir, "palettes.yml"));
        String color = y.getString(name + ".color");
        if (color == null) { p.sendMessage(PREFIX + tr("No existe esa paleta.", "That palette does not exist.")); return; }
        ItemMeta meta = item.getItemMeta();
        String display = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', color + display.replaceAll("§[0-9a-fk-or]", "")));
        if (meta.hasLore()) meta.setLore(color(meta.getLore()).stream().map(line -> ChatColor.translateAlternateColorCodes('&', color + ChatColor.stripColor(line))).toList());
        item.setItemMeta(meta);
        p.sendMessage(PREFIX + tr("Paleta aplicada: §b", "Palette applied: §b") + name);
    }

    private void handleEnchantments(Player p, String action, String name) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) { p.sendMessage(PREFIX + tr("Sostén un objeto primero.", "Hold an item first.")); return; }
        File file = new File(templatesDir, "enchantments.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (action.equalsIgnoreCase("guardar")) {
            Map<String,Integer> enchants = new TreeMap<>();
            item.getEnchantments().forEach((ench, level) -> enchants.put(ench.getKey().getKey(), level));
            y.set(name, enchants); saveYaml(y, file);
            p.sendMessage(PREFIX + tr("Preset de encantamientos guardado: §b", "Enchantment preset saved: §b") + name); return;
        }
        ConfigurationSection section = y.getConfigurationSection(name);
        if (section == null) { p.sendMessage(PREFIX + tr("No existe ese preset.", "That preset does not exist.")); return; }
        for (String key : section.getKeys(false)) {
            Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (ench != null) item.addUnsafeEnchantment(ench, Math.max(1, section.getInt(key, 1)));
        }
        p.sendMessage(PREFIX + tr("Encantamientos aplicados: §b", "Enchantments applied: §b") + name);
    }

    private void initializeTemplates() {
        File loreFile = new File(templatesDir, "lore.yml");
        YamlConfiguration lore = YamlConfiguration.loadConfiguration(loreFile);
        ensureLoreTemplate(lore, "clean_pvp", "&b✦ &lPvP limpio", "&b✦ &lClean PvP",
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&b✦ &lEQUIPO DE COMBATE", "&7Diseñado para combates competitivos.", "",
                    "&8▸ &7Clase: &bCombate", "&8▸ &7Rareza: &fRara", "",
                    "&b➤ &fListo para la próxima batalla", "&8&m━━━━━━━━━━━━━━━━━━━━"),
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&b✦ &lTACTICAL GEAR", "&7Built for competitive battles.", "",
                    "&8▸ &7Class: &bCombat", "&8▸ &7Rarity: &fRare", "",
                    "&b➤ &fReady for the next fight", "&8&m━━━━━━━━━━━━━━━━━━━━"));
        ensureLoreTemplate(lore, "legendary", "&6✦ &lLegendario", "&6✦ &lLegendary",
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&6✦ &lLEGENDARIO", "&7Un artefacto digno de una leyenda.", "",
                    "&6◆ &7Rareza: &6Legendario", "&6◆ &7Calidad: &fMítica", "",
                    "&e➤ &fLibera todo su potencial", "&8&m━━━━━━━━━━━━━━━━━━━━"),
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&6✦ &lLEGENDARY", "&7An artifact worthy of a legend.", "",
                    "&6◆ &7Rarity: &6Legendary", "&6◆ &7Quality: &fMythic", "",
                    "&e➤ &fUnleash its full potential", "&8&m━━━━━━━━━━━━━━━━━━━━"));
        ensureLoreTemplate(lore, "miner", "&b⛏ &lMinero", "&b⛏ &lMiner",
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&3⛏ &lHERRAMIENTA MINERA", "&7Creada para expediciones profundas.", "",
                    "&3▸ &7Categoría: &bMinería", "&3▸ &7Uso: &fRecolección de recursos", "",
                    "&b➤ &fExcava más profundo. Encuentra más.", "&8&m━━━━━━━━━━━━━━━━━━━━"),
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&3⛏ &lMINER'S TOOL", "&7Crafted for deep expeditions.", "",
                    "&3▸ &7Category: &bMining", "&3▸ &7Utility: &fResource gathering", "",
                    "&b➤ &fDig deeper. Find more.", "&8&m━━━━━━━━━━━━━━━━━━━━"));
        saveYaml(lore, loreFile);

        File paletteFile = new File(templatesDir, "palettes.yml");
        YamlConfiguration palettes = YamlConfiguration.loadConfiguration(paletteFile);
        if (!palettes.contains("ocean.color")) palettes.set("ocean.color", "&b");
        if (!palettes.contains("royal.color")) palettes.set("royal.color", "&5");
        if (!palettes.contains("inferno.color")) palettes.set("inferno.color", "&c");
        if (!palettes.contains("gold.color")) palettes.set("gold.color", "&6");
        saveYaml(palettes, paletteFile);

        File enchantFile = new File(templatesDir, "enchantments.yml");
        YamlConfiguration enchants = YamlConfiguration.loadConfiguration(enchantFile);
        if (!enchants.contains("pvp_sword.sharpness")) enchants.set("pvp_sword.sharpness", 5);
        if (!enchants.contains("pvp_sword.unbreaking")) enchants.set("pvp_sword.unbreaking", 3);
        if (!enchants.contains("pvp_sword.fire_aspect")) enchants.set("pvp_sword.fire_aspect", 2);
        if (!enchants.contains("mining_pickaxe.efficiency")) enchants.set("mining_pickaxe.efficiency", 5);
        if (!enchants.contains("mining_pickaxe.unbreaking")) enchants.set("mining_pickaxe.unbreaking", 3);
        if (!enchants.contains("mining_pickaxe.fortune")) enchants.set("mining_pickaxe.fortune", 3);
        if (!enchants.contains("protection.protection")) enchants.set("protection.protection", 4);
        if (!enchants.contains("protection.unbreaking")) enchants.set("protection.unbreaking", 3);
        saveYaml(enchants, enchantFile);
    }

    private void ensureLoreTemplate(YamlConfiguration yaml, String key, String displayEs, String displayEn, List<String> loreEs, List<String> loreEn) {
        if (!yaml.contains(key + ".lore")) yaml.set(key + ".lore", loreEs);
        if (!yaml.contains(key + ".lore_en")) yaml.set(key + ".lore_en", loreEn);
        if (!yaml.contains(key + ".display")) yaml.set(key + ".display", displayEs);
        if (!yaml.contains(key + ".display_en")) yaml.set(key + ".display_en", displayEn);
    }

    private void applyLoreTemplateToHand(Player p, String key) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            p.sendMessage(PREFIX + tr("Sostén el objeto al que quieras aplicar el lore.", "Hold the item you want to apply lore to."));
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(templatesDir, "lore.yml"));
        List<String> lines = yaml.getStringList(key + (isEnglish() ? ".lore_en" : ".lore"));
        if (lines.isEmpty()) lines = yaml.getStringList(key + ".lore");
        if (lines.isEmpty()) {
            p.sendMessage(PREFIX + tr("La plantilla no contiene líneas de lore.", "That template contains no lore lines."));
            return;
        }
        ItemMeta meta = held.getItemMeta();
        if (meta == null) {
            p.sendMessage(PREFIX + tr("No se pudo editar ese objeto.", "Could not edit that item."));
            return;
        }
        meta.setLore(color(lines));
        held.setItemMeta(meta);
        p.sendMessage(PREFIX + tr("Lore aplicado: §b", "Lore applied: §b") + yaml.getString(key + (isEnglish() ? ".display_en" : ".display"), yaml.getString(key + ".display", prettyKey(key))));
    }

    private void setLanguage(Player p, String requested) {
        String language = requested.toLowerCase(Locale.ROOT);
        if (!language.equals("es") && !language.equals("en")) {
            p.sendMessage(PREFIX + tr("Idiomas disponibles: §bes§7 y §ben§7.", "Available languages: §bes§7 and §ben§7."));
            openLanguages(p);
            return;
        }
        getConfig().set("language", language);
        saveConfig();
        p.sendMessage(PREFIX + tr("Idioma guardado: Español.", "Language saved: English."));
        openMain(p);
    }

    private boolean isEnglish() {
        return getConfig().getString("language", "es").equalsIgnoreCase("en");
    }

    private String tr(String spanish, String english) {
        return isEnglish() ? english : spanish;
    }

    private String prettyKey(String key) {
        String value = key == null ? "" : key;
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        value = value.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        for (String part : value.split("\\s+")) {
            if (part.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String romanLevel(int level) {
        if (level <= 0) return "";
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level < numerals.length ? numerals[level] : Integer.toString(level);
    }

    private ItemStack itemWithLore(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private Inventory menu(String kind, String project, int page, String title) {
        return menu(kind, project, page, -1, 0, title);
    }

    private Inventory menu(String kind, String project, int page, int targetSlot, int subPage, String title) {
        MenuHolder h = new MenuHolder(kind, project, page, targetSlot, subPage);
        Inventory inv = Bukkit.createInventory(h, 54, title);
        h.inventory = inv;
        return inv;
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
        final String kind, project;
        final int page, targetSlot, subPage;
        final Map<Integer, String> entryBySlot = new HashMap<>();
        Inventory inventory;
        MenuHolder(String kind, String project, int page, int targetSlot, int subPage) {
            this.kind = kind;
            this.project = project;
            this.page = page;
            this.targetSlot = targetSlot;
            this.subPage = subPage;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}