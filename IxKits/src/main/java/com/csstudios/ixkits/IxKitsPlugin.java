package com.csstudios.ixkits;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class IxKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private File projectsDir, templatesDir, exportsDir;
    private ProjectService projectService;
    private CatalogClient catalogClient;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private static final int[] EQUIP_SLOTS = {19, 21, 23, 25, 31};
    private static final List<String> EDITABLE_ATTRIBUTES = List.of("max_health", "attack_damage", "attack_speed", "armor", "armor_toughness", "knockback_resistance", "movement_speed", "luck");
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
        projectService = new ProjectService(this, getDataFolder());
        catalogClient = new CatalogClient(this);
        initializeTemplates();
        Objects.requireNonNull(getCommand("ixkits")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("IxKits habilitado. Kit Creation Studio by CS Studios.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("IxKits: este comando requiere un jugador.", "IxKits: this command requires a player."));
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
        if (sub.equals("duplicar") && args.length >= 3) {
            String source = slug(args[1]);
            String targetName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            try {
                if (projectService.duplicate(source, targetName)) player.sendMessage(PREFIX + tr("Proyecto duplicado: §b", "Project duplicated: §b") + targetName);
                else player.sendMessage(PREFIX + tr("No se pudo duplicar. Comprueba el origen y que el nombre no exista.", "Could not duplicate. Check the source and make sure the name is unused."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if (sub.equals("renombrar") && args.length >= 3) {
            String source = slug(args[1]);
            String targetName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            try {
                if (projectService.rename(source, targetName)) player.sendMessage(PREFIX + tr("Proyecto renombrado: §b", "Project renamed: §b") + targetName);
                else player.sendMessage(PREFIX + tr("No se pudo renombrar el proyecto.", "Could not rename the project."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if (sub.equals("eliminar") && args.length >= 2) {
            try {
                if (projectService.moveToTrash(slug(args[1]))) player.sendMessage(PREFIX + tr("Proyecto movido a la papelera recuperable.", "Project moved to recoverable trash."));
                else player.sendMessage(PREFIX + tr("No existe ese proyecto.", "That project does not exist."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if ((sub.equals("deshacer") || sub.equals("restaurar")) && args.length >= 2) {
            try {
                if (projectService.undo(slug(args[1]))) player.sendMessage(PREFIX + tr("Se restauró la última copia disponible.", "The latest available backup was restored."));
                else player.sendMessage(PREFIX + tr("No hay copias disponibles para ese proyecto.", "No backups are available for that project."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if (sub.equals("validar") && args.length >= 2) {
            for (String line : projectService.validate(slug(args[1]))) player.sendMessage(PREFIX + "§7" + line);
            return true;
        }
        if (sub.equals("exportar-essentials") && args.length >= 2) {
            try {
                File output = projectService.exportEssentials(slug(args[1]));
                player.sendMessage(PREFIX + tr("Exportación EssentialsX creada: §b", "EssentialsX export created: §b") + output.getPath());
                player.sendMessage(PREFIX + tr("Aviso: el formato legible no conserva todas las propiedades avanzadas.", "Warning: the human-readable format cannot preserve all advanced metadata."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if (sub.equals("importar") && args.length >= 2) {
            String safeName = new File(args[1]).getName();
            File input = new File(projectService.importsDirectory(), safeName);
            String requestedName = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
            try {
                if (projectService.importNative(input, requestedName)) player.sendMessage(PREFIX + tr("Proyecto importado y validado desde la carpeta imports.", "Project imported and validated from the imports folder."));
                else player.sendMessage(PREFIX + tr("No se pudo importar. Usa un archivo IxKits YAML válido y un nombre nuevo.", "Import failed. Use a valid IxKits YAML file and a new project name."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if (sub.equals("importar-essentials") && args.length >= 4) {
            File input = new File(projectService.importsDirectory(), new File(args[1]).getName());
            String kit = args[2];
            String projectName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            try {
                if (projectService.importEssentials(input, kit, projectName)) player.sendMessage(PREFIX + tr("Importación EssentialsX completada. Revisa el kit con /ixkits validar.", "EssentialsX import completed. Review the kit with /ixkits validar."));
                else player.sendMessage(PREFIX + tr("No se pudo importar ese kit. Los kits NBT/base64 no se pueden convertir con este importador básico.", "Could not import that kit. NBT/base64 kits cannot be converted by this basic importer."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if (sub.equals("catalogo") || sub.equals("catálogo") || sub.equals("buscar")) {
            String query = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
            openCatalog(player, query);
            return true;
        }
        if (sub.equals("descargar") && args.length >= 2) {
            downloadCatalogTemplate(player, args[1]);
            return true;
        }
        if (sub.equals("publicar") && args.length >= 3) {
            publishProject(player, slug(args[1]), args[2],
                args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Created with IxKits by CS Studios");
            return true;
        }
        if (sub.equals("valorar") && args.length >= 3) {
            int rating;
            try { rating = Integer.parseInt(args[2]); } catch (NumberFormatException ex) {
                player.sendMessage(PREFIX + tr("La valoración debe ser un número del 1 al 5.", "Rating must be a number from 1 to 5.")); return true;
            }
            if (rating < 1 || rating > 5) {
                player.sendMessage(PREFIX + tr("La valoración debe ser del 1 al 5.", "Rating must be from 1 to 5.")); return true;
            }
            catalogClient.rate(args[1], player.getUniqueId().toString(), rating).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(this, () -> player.sendMessage(PREFIX + (error == null
                    ? tr("Valoración enviada.", "Rating submitted.")
                    : tr("No se pudo valorar: ", "Rating failed: ") + rootMessage(error)))));
            return true;
        }
        if (sub.equals("favorito") && args.length >= 2) {
            catalogClient.toggleFavorite(args[1], player.getUniqueId().toString()).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(this, () -> player.sendMessage(PREFIX + (error == null
                    ? tr("Favorito actualizado.", "Favorite updated.")
                    : tr("No se pudo actualizar: ", "Could not update favorite: ") + rootMessage(error)))));
            return true;
        }
        if (sub.equals("equipamiento") && args.length >= 2) {
            if (projectFile(slug(args[1])).exists()) openEquipment(player, slug(args[1]));
            else player.sendMessage(PREFIX + tr("No existe ese proyecto.", "That project does not exist."));
            return true;
        }
        if (sub.equals("recargar")) { reloadConfig(); initializeTemplates(); player.sendMessage(PREFIX + tr("Configuración y plantillas recargadas.", "Configuration and templates reloaded.")); return true; }
        player.sendMessage(tr("§bIxKits §7— comandos:", "§bIxKits §7— commands:"));
        player.sendMessage(tr("§f/ixkits §8| §7Abrir editor", "§f/ixkits §8| §7Open studio"));
        player.sendMessage(tr("§f/ixkits crear <nombre> §8| §7Crear proyecto", "§f/ixkits crear <name> §8| §7Create project"));
        player.sendMessage(tr("§f/ixkits editar <proyecto> §8| §7Editar proyecto", "§f/ixkits editar <project> §8| §7Edit project"));
        player.sendMessage(tr("§f/ixkits proyectos §8| §7Biblioteca local", "§f/ixkits proyectos §8| §7Project library"));
        player.sendMessage(tr("§f/ixkits plantillas §8| §7Plantillas locales", "§f/ixkits plantillas §8| §7Local templates"));
        player.sendMessage(tr("§f/ixkits idioma §8| §7Cambiar idioma", "§f/ixkits idioma §8| §7Change language"));
        player.sendMessage(tr("§f/ixkits exportar <proyecto> §8| §7Exportar proyecto IxKits", "§f/ixkits exportar <project> §8| §7Export IxKits project"));
        player.sendMessage("§f/ixkits exportar-essentials <proyecto>");
        player.sendMessage("§f/ixkits importar <archivo.yml> [nombre]");
        player.sendMessage("§f/ixkits importar-essentials <archivo.yml> <kit> <nombre>");
        player.sendMessage("§f/ixkits duplicar|renombrar <proyecto> <nuevo nombre>");
        player.sendMessage("§f/ixkits eliminar|deshacer|validar <proyecto>");
        player.sendMessage("§f/ixkits equipamiento <proyecto>");
        player.sendMessage("§f/ixkits catalogo [búsqueda] · /ixkits buscar <texto>");
        player.sendMessage("§f/ixkits publicar <proyecto> <categoría> [descripción]");
        player.sendMessage("§f/ixkits descargar <id> · valorar <id> <1-5> · favorito <id>");
        player.sendMessage(tr("§f/ixkits lore guardar|aplicar <nombre>", "§f/ixkits lore guardar|aplicar <name>"));
        player.sendMessage(tr("§f/ixkits paleta aplicar <nombre>", "§f/ixkits paleta aplicar <name>"));
        player.sendMessage(tr("§f/ixkits encantamientos guardar|aplicar <nombre>", "§f/ixkits encantamientos guardar|aplicar <name>"));
        return true;
    }

    private void openCatalog(Player p, String query) {
        if (!catalogClient.isEnabled()) {
            p.sendMessage(PREFIX + tr("El catálogo todavía no está configurado. Activa catalog.enabled y define catalog.base-url en config.yml.", "The catalog is not configured yet. Enable catalog.enabled and set catalog.base-url in config.yml."));
            return;
        }
        p.sendMessage(PREFIX + tr("Conectando con IxKits Community...", "Connecting to IxKits Community..."));
        catalogClient.search(query, 1, EDIT_SLOTS.length).whenComplete((items, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    p.sendMessage(PREFIX + tr("No se pudo cargar el catálogo: ", "Could not load catalog: ") + rootMessage(error));
                    return;
                }
                Inventory inv = menu("catalog", null, 0, tr("§3✦ §bIxKits §8• §0Community", "§3✦ §bIxKits §8• §0Community"));
                ((MenuHolder) inv.getHolder()).query = query;
                border(inv);
                MenuHolder holder = (MenuHolder) inv.getHolder();
                if (items.isEmpty()) inv.setItem(22, item(Material.PAPER, tr("§7No hay plantillas disponibles.", "§7No templates are available."), tr("§7Prueba otra búsqueda.", "§7Try another search.")));
                int i = 0;
                for (CatalogClient.TemplateSummary template : items) {
                    if (i >= EDIT_SLOTS.length) break;
                    int slot = EDIT_SLOTS[i++];
                    String rating = template.ratingCount() == 0 ? tr("Sin valoraciones", "Not rated")
                        : String.format(Locale.US, "%.1f/5 (%d)", template.averageRating(), template.ratingCount());
                    inv.setItem(slot, item(Material.ENDER_CHEST, "§d" + template.title(),
                        "§7" + tr("Autor: ", "Author: ") + template.author(),
                        "§7" + tr("Categoría: ", "Category: ") + template.category(),
                        "§7" + tr("Versión: ", "Version: ") + template.mcVersion(),
                        "§7" + tr("Descargas: ", "Downloads: ") + template.downloads(),
                        "§7" + tr("Valoración: ", "Rating: ") + rating,
                        tr("§eClic: descargar", "§eClick: download"),
                        tr("§eClic derecho: favorito", "§eRight-click: favorite")));
                    holder.entryBySlot.put(slot, template.id());
                }
                inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Menú principal", "§8Main menu")));
                inv.setItem(47, item(Material.COMPASS, tr("§bNueva búsqueda", "§bNew search"), tr("§7Usa /ixkits buscar <texto>", "§7Use /ixkits buscar <text>")));
                inv.setItem(49, item(Material.BOOK, tr("§fCómo descargar", "§fHow to download"), tr("§7Clic izquierdo en una plantilla.", "§7Left-click a template.")));
                inv.setItem(51, item(Material.CLOCK, tr("§bActualizar", "§bRefresh"), tr("§7Volver a cargar el catálogo.", "§7Reload the catalog.")));
                p.openInventory(inv);
            }));
    }

    private void downloadCatalogTemplate(Player p, String id) {
        if (!catalogClient.isEnabled()) {
            p.sendMessage(PREFIX + tr("El catálogo no está configurado.", "The catalog is not configured."));
            return;
        }
        catalogClient.download(id).whenComplete((download, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    p.sendMessage(PREFIX + tr("Descarga fallida: ", "Download failed: ") + rootMessage(error));
                    return;
                }
                try {
                    File file = new File(projectService.importsDirectory(), projectService.slug(download.id()) + ".ixkit.yml");
                    Files.writeString(file.toPath(), download.projectYaml(), java.nio.charset.StandardCharsets.UTF_8);
                    String name = download.title();
                    if (!projectService.importNative(file, name)) {
                        name = download.title() + " " + Long.toString(System.currentTimeMillis() % 100000);
                        if (!projectService.importNative(file, name)) {
                            p.sendMessage(PREFIX + tr("El archivo no pasó la validación o el nombre ya existe.", "The file failed validation or the project name already exists."));
                            return;
                        }
                    }
                    p.sendMessage(PREFIX + tr("Plantilla importada como proyecto: §b", "Template imported as project: §b") + name);
                    openProjects(p);
                } catch (IOException ex) {
                    p.sendMessage(PREFIX + "§c" + ex.getMessage());
                }
            }));
    }

    private void publishProject(Player p, String project, String category, String description) {
        if (!catalogClient.isEnabled()) {
            p.sendMessage(PREFIX + tr("Configura primero el catálogo online en config.yml.", "Configure the online catalog in config.yml first."));
            return;
        }
        File file = projectFile(project);
        if (!file.isFile()) {
            p.sendMessage(PREFIX + tr("No existe ese proyecto.", "Project not found."));
            return;
        }
        try {
            String yaml = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            YamlConfiguration projectYaml = YamlConfiguration.loadConfiguration(file);
            String title = projectYaml.getString("name", project);
            String version = getConfig().getString("catalog.minecraft-version", "1.21.11");
            String license = getConfig().getString("catalog.default-license", "all-rights-reserved");
            catalogClient.publish(title, p.getName(), category, description, version, license, yaml)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) p.sendMessage(PREFIX + tr("No se pudo publicar: ", "Publish failed: ") + rootMessage(error));
                    else p.sendMessage(PREFIX + tr("Plantilla publicada con ID §b", "Template published with ID §b") + result.id());
                }));
        } catch (IOException ex) {
            p.sendMessage(PREFIX + "§c" + ex.getMessage());
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void openMain(Player p) {
        Inventory inv = menu("main", null, 0, tr("§3✦ §bIxKits §3• §8Estudio", "§3✦ §bIxKits §3• §8Studio"));
        border(inv);
        inv.setItem(20, item(Material.CHEST, tr("§bMis proyectos", "§bMy projects"), tr("§7Crear, abrir y editar kits.", "§7Create, open and edit kits."), tr("§8Proyectos locales", "§8Local projects")));
        inv.setItem(22, item(Material.BOOKSHELF, tr("§bBiblioteca de plantillas", "§bTemplate library"), tr("§7Lore, paletas y encantamientos.", "§7Lore, palettes and enchantments."), tr("§8Plantillas incluidas", "§8Built-in templates")));
        inv.setItem(24, item(Material.WRITABLE_BOOK, tr("§bExportadores", "§bExport tools"), tr("§7Generar archivos editables.", "§7Generate editable files."), tr("§8Selecciona un proyecto", "§8Choose a project")));
        inv.setItem(30, item(Material.ENDER_CHEST, "§dIxKits Community", tr("§7Buscar y descargar plantillas.", "§7Search and download templates."), tr("§8Requiere catalog.enabled y base-url", "§8Requires catalog.enabled and base-url")));
        inv.setItem(32, item(Material.COMPASS, tr("§fAyuda y comandos", "§fHelp and commands"), tr("§7/ixkits crear <nombre>", "§7/ixkits crear <name>"), "§7/ixkits idioma"));
        inv.setItem(28, item(Material.IRON_CHESTPLATE, tr("§bHerramientas del proyecto", "§bProject tools"), tr("§7Copias, validación e importación.", "§7Backups, validation and import."), tr("§eAbre Mis proyectos", "§eOpen My projects")));
        inv.setItem(40, item(Material.WRITABLE_BOOK, tr("§eIdioma / Language", "§eLanguage / Idioma"), tr("§7Actual: " + (isEnglish() ? "English" : "Español"), "§7Current: " + (isEnglish() ? "English" : "Spanish")), tr("§eClic para cambiar", "§eClick to change")));
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
        inv.setItem(44, item(Material.IRON_CHESTPLATE, tr("§bEquipamiento", "§bEquipment"), tr("§7Casco, peto, pantalones, botas", "§7Helmet, chestplate, leggings, boots"), tr("§7y mano secundaria", "§7and offhand")));
        inv.setItem(53, item(Material.WRITABLE_BOOK, tr("§dExportar", "§dExport"), tr("§7Generar archivo .ixkit.yml", "§7Generate .ixkit.yml file")));
        p.openInventory(inv);
    }

    private void openProjectActions(Player p, String project) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(projectFile(project));
        Inventory inv = menu("project-actions", project, 0, tr("§3✦ §bIxKits §8• §0" + yaml.getString("name", project), "§3✦ §bIxKits §8• §0" + yaml.getString("name", project)));
        border(inv);
        inv.setItem(20, item(Material.CHEST, tr("§bDuplicar", "§bDuplicate"), tr("§7Crear una copia independiente.", "§7Create an independent copy."), tr("§eClic y escribe un nombre en el chat", "§eClick and type a name in chat")));
        inv.setItem(22, item(Material.NAME_TAG, tr("§bRenombrar", "§bRename"), tr("§7Cambiar el nombre del proyecto.", "§7Change the project name."), tr("§eClic y escribe un nombre en el chat", "§eClick and type a name in chat")));
        inv.setItem(24, item(Material.TNT, tr("§cEliminar", "§cDelete"), tr("§7Mover a la papelera recuperable.", "§7Move it to recoverable trash."), tr("§eSolicita confirmación", "§eRequires confirmation")));
        inv.setItem(30, item(Material.WRITABLE_BOOK, tr("§bValidar proyecto", "§bValidate project"), tr("§7Comprobar objetos y formato.", "§7Check items and project format.")));
        inv.setItem(32, item(Material.PAPER, tr("§aExportar EssentialsX", "§aExport to EssentialsX"), tr("§7Crea un kits.yml compatible.", "§7Create a compatible kits.yml."), tr("§cPuede perder metadatos avanzados", "§cAdvanced metadata may be lost")));
        inv.setItem(34, item(Material.ENDER_CHEST, tr("§dPublicar en Community", "§dPublish to Community"), tr("§7Publica una plantilla validada.", "§7Publish a validated template."), tr("§8Requiere token de publicación.", "§8Requires a publish token.")));
        inv.setItem(40, item(Material.CLOCK, tr("§eDeshacer último cambio", "§eUndo last change"), tr("§7Restaurar una copia anterior.", "§7Restore a previous backup.")));
        inv.setItem(49, item(Material.WRITABLE_BOOK, tr("§dExportar IxKits", "§dExport IxKits"), tr("§7Conserva los objetos serializados.", "§7Preserves serialized items.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Biblioteca de proyectos", "§8Project library")));
        p.openInventory(inv);
    }

    private void openDeleteConfirm(Player p, String project) {
        Inventory inv = menu("project-delete-confirm", project, 0, tr("§4Confirmar eliminación", "§4Confirm deletion"));
        border(inv);
        inv.setItem(21, item(Material.RED_CONCRETE, tr("§cSí, mover a papelera", "§cYes, move to trash"), tr("§7El archivo se conserva para recuperación.", "§7The file is kept for recovery.")));
        inv.setItem(23, item(Material.LIME_CONCRETE, tr("§aCancelar", "§aCancel"), tr("§7No se eliminará nada.", "§7Nothing will be deleted.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back")));
        p.openInventory(inv);
    }

    private ItemStack gearSlot(ItemStack stored, Material icon, String label) {
        if (stored != null && !stored.getType().isAir()) return stored.clone();
        return item(Material.GRAY_STAINED_GLASS_PANE, "§8IxKits empty slot", "§7" + label, tr("§8Coloca aquí un objeto", "§8Place an item here"));
    }

    private void openEquipment(Player p, String project) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(projectFile(project));
        Inventory inv = menu("equipment", project, 0, tr("§3✦ §bIxKits §8• §0Equipamiento", "§3✦ §bIxKits §8• §0Equipment"));
        border(inv);
        inv.setItem(19, gearSlot(yaml.getItemStack("equipment.helmet"), Material.LEATHER_HELMET, tr("Casco", "Helmet")));
        inv.setItem(21, gearSlot(yaml.getItemStack("equipment.chestplate"), Material.LEATHER_CHESTPLATE, tr("Peto", "Chestplate")));
        inv.setItem(23, gearSlot(yaml.getItemStack("equipment.leggings"), Material.LEATHER_LEGGINGS, tr("Pantalones", "Leggings")));
        inv.setItem(25, gearSlot(yaml.getItemStack("equipment.boots"), Material.LEATHER_BOOTS, tr("Botas", "Boots")));
        inv.setItem(31, gearSlot(yaml.getItemStack("equipment.offhand"), Material.SHIELD, tr("Mano secundaria", "Offhand")));
        inv.setItem(40, item(Material.PAPER, tr("§fCómo usarlo", "§fHow to use"), tr("§7Mueve objetos del inventario", "§7Move items from your inventory"), tr("§7a las cinco ranuras disponibles.", "§7into the five available slots."), tr("§7Se guarda al cerrar.", "§7Saved when closed.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al proyecto", "§7Back to project")));
        p.openInventory(inv);
    }

    private void openAttributes(Player p, String project, int page, int targetSlot) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(projectFile(project));
        ItemStack target = yaml.getItemStack("pages." + page + "." + targetSlot);
        if (target == null || target.getType().isAir()) { openEditor(p, project, page); return; }
        Inventory inv = menu("attributes", project, page, targetSlot, 0, tr("§3✦ §bIxKits §8• §0Atributos", "§3✦ §bIxKits §8• §0Attributes"));
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        inv.setItem(4, target.clone());
        for (int i = 0; i < EDITABLE_ATTRIBUTES.size(); i++) {
            String key = EDITABLE_ATTRIBUTES.get(i);
            int slot = EDIT_SLOTS[i];
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (attribute == null) continue;
            double amount = ownedAttributeAmount(target, key);
            List<String> lore = new ArrayList<>();
            lore.add(tr("§7Modificador IxKits: §b", "§7IxKits modifier: §b") + String.format(Locale.US, "%.2f", amount));
            lore.add("");
            lore.add(tr("§aClic izquierdo: +0.5", "§aLeft-click: +0.5"));
            lore.add(tr("§eClic derecho: -0.5", "§eRight-click: -0.5"));
            lore.add(tr("§cMayús + clic: quitar modificador propio", "§cShift-click: remove IxKits modifier"));
            inv.setItem(slot, itemWithLore(Material.AMETHYST_SHARD, "§b" + prettyKey(key), lore));
            holder.entryBySlot.put(slot, key);
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al objeto", "§7Back to item")));
        inv.setItem(49, item(Material.PAPER, tr("§fAyuda", "§fHelp"), tr("§7Solo modifica atributos de IxKits.", "§7Only IxKits attributes are changed.")));
        p.openInventory(inv);
    }

    private double ownedAttributeAmount(ItemStack stack, String key) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attribute == null || stack.getItemMeta() == null) return 0.0;
        NamespacedKey modifierKey = new NamespacedKey(this, "attr_" + key);
        Collection<AttributeModifier> modifiers = stack.getItemMeta().getAttributeModifiers(attribute);
        if (modifiers == null) return 0.0;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getKey().equals(modifierKey)) return modifier.getAmount();
        }
        return 0.0;
    }

    private void adjustAttribute(Player p, MenuHolder holder, String key, boolean decrease, boolean remove) {
        File file = projectFile(holder.project);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "pages." + holder.page + "." + holder.targetSlot;
        ItemStack target = yaml.getItemStack(path);
        if (target == null || target.getType().isAir()) { openEditor(p, holder.project, holder.page); return; }
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attribute == null) { p.sendMessage(PREFIX + tr("Atributo no disponible en esta versión.", "Attribute is unavailable in this version.")); return; }
        ItemMeta meta = target.getItemMeta();
        double previousAmount = ownedAttributeAmount(target, key);
        NamespacedKey modifierKey = new NamespacedKey(this, "attr_" + key);
        Collection<AttributeModifier> currentModifiers = meta.getAttributeModifiers(attribute);
        if (currentModifiers != null) {
            for (AttributeModifier modifier : new ArrayList<>(currentModifiers)) {
                if (modifier.getKey().equals(modifierKey)) meta.removeAttributeModifier(attribute, modifier);
            }
        }
        if (!remove) {
            double next = previousAmount + (decrease ? -0.5 : 0.5);
            if (Math.abs(next) > 0.001) {
                meta.addAttributeModifier(attribute, new AttributeModifier(modifierKey, next, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            }
        }
        target.setItemMeta(meta);
        yaml.set(path, target);
        projectService.backupBeforeChange(file);
        saveYaml(yaml, file);
        openAttributes(p, holder.project, holder.page, holder.targetSlot);
    }

    private void promptInput(Player p, InputType type, String project, int page, int slot, String promptEs, String promptEn) {
        pendingInputs.put(p.getUniqueId(), new PendingInput(type, project, page, slot));
        p.closeInventory();
        p.sendMessage(PREFIX + tr(promptEs, promptEn));
        p.sendMessage(PREFIX + tr("§7Escribe §c-cancelar §7para salir.", "§7Type §c-cancel §7to exit."));
    }

    @EventHandler public void onAsyncChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingInput pending = pendingInputs.remove(uuid);
        if (pending == null) return;
        event.setCancelled(true);
        String input = event.getMessage();
        Bukkit.getScheduler().runTask(this, () -> applyPendingInput(event.getPlayer(), pending, input));
    }

    private void applyPendingInput(Player player, PendingInput pending, String input) {
        if (input.equalsIgnoreCase("-cancelar") || input.equalsIgnoreCase("-cancel")) {
            player.sendMessage(PREFIX + tr("Edición cancelada.", "Edit cancelled."));
            if (pending.project != null && projectFile(pending.project).isFile()) openItemEditor(player, pending.project, pending.page, pending.slot);
            return;
        }
        if (pending.type == InputType.DUPLICATE_PROJECT || pending.type == InputType.RENAME_PROJECT) {
            try {
                boolean changed = pending.type == InputType.DUPLICATE_PROJECT
                    ? projectService.duplicate(pending.project, input)
                    : projectService.rename(pending.project, input);
                player.sendMessage(PREFIX + (changed ? tr("Operación completada.", "Operation completed.") : tr("No se pudo completar. Comprueba que el nombre no esté ocupado.", "Could not complete. Make sure the name is not already in use.")));
                openProjects(player);
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return;
        }
        File file = projectFile(pending.project);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String itemPath = "pages." + pending.page + "." + pending.slot;
        ItemStack target = yaml.getItemStack(itemPath);
        if (target == null || target.getType().isAir()) { player.sendMessage(PREFIX + tr("No se encontró el objeto.", "Item not found.")); openEditor(player, pending.project, pending.page); return; }
        ItemMeta meta = target.getItemMeta();
        try {
            switch (pending.type) {
                case ITEM_NAME -> {
                    if (input.equals("-")) meta.setDisplayName(null);
                    else meta.displayName(MiniMessage.miniMessage().deserialize(input));
                }
                case ITEM_LORE -> {
                    if (input.equals("-")) meta.lore(null);
                    else meta.lore(Arrays.stream(input.split("\\|", -1)).map(MiniMessage.miniMessage()::deserialize).toList());
                }
                case MODEL_DATA -> {
                    if (input.equals("-")) meta.setCustomModelData(null);
                    else meta.setCustomModelData(Integer.parseInt(input.trim()));
                }
                case LEATHER_COLOR -> {
                    if (!(meta instanceof LeatherArmorMeta leather)) {
                        player.sendMessage(PREFIX + tr("Este objeto no es una armadura de cuero.", "This item is not leather armor."));
                        openItemEditor(player, pending.project, pending.page, pending.slot);
                        return;
                    }
                    Color color;
                    if (input.startsWith("#") && input.length() == 7) color = Color.fromRGB(Integer.parseInt(input.substring(1), 16));
                    else {
                        String[] rgb = input.split(",");
                        if (rgb.length != 3) throw new IllegalArgumentException("Use #RRGGBB or R,G,B.");
                        color = Color.fromRGB(clampColor(Integer.parseInt(rgb[0].trim())), clampColor(Integer.parseInt(rgb[1].trim())), clampColor(Integer.parseInt(rgb[2].trim())));
                    }
                    leather.setColor(color);
                }
                default -> { }
            }
        } catch (IllegalArgumentException ex) {
            player.sendMessage(PREFIX + tr("Entrada inválida: ", "Invalid input: ") + ex.getMessage());
            promptInput(player, pending.type, pending.project, pending.page, pending.slot,
                "Vuelve a escribir el valor:", "Type the value again:");
            return;
        }
        target.setItemMeta(meta);
        projectService.backupBeforeChange(file);
        yaml.set(itemPath, target);
        yaml.set("format-version", 2);
        yaml.set("updated", System.currentTimeMillis());
        saveYaml(yaml, file);
        player.sendMessage(PREFIX + tr("Objeto actualizado.", "Item updated."));
        openItemEditor(player, pending.project, pending.page, pending.slot);
    }

    private int clampColor(int value) { return Math.max(0, Math.min(255, value)); }

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
        openLoreTemplates(p, page, null, 0, -1);
    }

    private void openLoreTemplates(Player p, int page, String project, int projectPage, int targetSlot) {
        YamlConfiguration lore = YamlConfiguration.loadConfiguration(new File(templatesDir, "lore.yml"));
        List<String> keys = new ArrayList<>();
        for (String key : lore.getKeys(false)) if (!lore.getStringList(key + ".lore").isEmpty()) keys.add(key);
        Collections.sort(keys);
        int pages = Math.max(1, (int) Math.ceil(keys.size() / (double) EDIT_SLOTS.length));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        String title = tr("§3✦ §bIxKits §8• §0Plantillas de lore", "§3✦ §bIxKits §8• §0Lore templates") + " §7[" + (safePage + 1) + "/" + pages + "]";
        Inventory inv = project == null
            ? menu("lore-templates", null, safePage, title)
            : menu("lore-templates", project, projectPage, targetSlot, safePage, title);
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
            preview.add(project == null
                ? tr("§eClic para aplicar al objeto en mano", "§eClick to apply to item in hand")
                : tr("§eClic para aplicar al objeto del kit", "§eClick to apply to kit item"));
            inv.setItem(slot, itemWithLore(Material.WRITABLE_BOOK, ChatColor.translateAlternateColorCodes('&', display), preview));
            holder.entryBySlot.put(slot, key);
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), project == null
            ? tr("§8Biblioteca de plantillas", "§8Template library")
            : tr("§8Volver al objeto", "§8Back to item")));
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
        inv.setItem(22, item(Material.WRITABLE_BOOK, tr("§bPlantillas de lore", "§bLore templates"),
            tr("§7Aplicar un diseño predeterminado.", "§7Apply a built-in style."),
            tr("§eClic para elegir", "§eClick to choose")));
        inv.setItem(31, item(Material.PAPER, tr("§fDetalles del objeto", "§fItem details"), enchantLore.toArray(new String[0])));
        inv.setItem(11, item(Material.NAME_TAG, tr("§bEditar nombre", "§bEdit name"), tr("§7Admite MiniMessage y RGB.", "§7Supports MiniMessage and RGB."), tr("§eClic para escribir", "§eClick to type")));
        inv.setItem(13, item(Material.WRITABLE_BOOK, tr("§bEditar lore", "§bEdit lore"), tr("§7Separa las líneas usando |.", "§7Separate lines using |."), tr("§7Admite MiniMessage y degradados.", "§7Supports MiniMessage and gradients."), tr("§eClic para escribir", "§eClick to type")));
        inv.setItem(24, item(Material.BARRIER, tr("§cQuitar todos los encantamientos", "§cRemove all enchantments"), tr("§7Solo afecta a este objeto del proyecto.", "§7Only affects this project item."), tr("§eClic para confirmar al instante", "§eClick to apply immediately")));
        inv.setItem(29, item(Material.IRON_BLOCK, tr("§bIrrompible", "§bUnbreakable"), tr("§7Alternar la propiedad del objeto.", "§7Toggle the item's property."), tr("§eClic para cambiar", "§eClick to toggle")));
        inv.setItem(33, item(Material.ANVIL, tr("§bDurabilidad", "§bDurability"), tr("§7Clic: +10 de daño", "§7Click: +10 damage"), tr("§7Derecho: -10; Mayús: reparar", "§7Right-click: -10; Shift: repair")));
        inv.setItem(35, item(Material.CARVED_PUMPKIN, tr("§bModelo personalizado", "§bCustom model data"), tr("§7Define o borra el número de modelo.", "§7Set or clear the model number."), tr("§eClic para escribir", "§eClick to type")));
        inv.setItem(37, item(Material.BRUSH, tr("§bAtributos", "§bAttributes"), tr("§7Salud, daño, armadura y más.", "§7Health, damage, armor and more."), tr("§eClic para abrir", "§eClick to open")));
        inv.setItem(39, item(Material.GLASS, tr("§bFlags del objeto", "§bItem flags"), tr("§7Alternar ocultación de atributos.", "§7Toggle hidden attributes."), tr("§eClic para cambiar", "§eClick to toggle")));
        inv.setItem(41, item(Material.LEATHER_CHESTPLATE, tr("§bColor de cuero", "§bLeather color"), tr("§7Para objetos de cuero.", "§7For leather items."), tr("§eEscribe #RRGGBB o R,G,B", "§eType #RRGGBB or R,G,B")));
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
            else if (slot == 44) { savePage(e.getView().getTopInventory(), h); openEquipment(p, h.project); }
            else if (slot == 53) { savePage(e.getView().getTopInventory(), h); exportProject(p, h.project); }
            return;
        }

        if (h.kind.equals("equipment")) {
            if (slot >= e.getView().getTopInventory().getSize()) {
                if (e.isShiftClick()) e.setCancelled(true);
                return;
            }
            if (!contains(EQUIP_SLOTS, slot)) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (h.kind.equals("main")) {
            if (slot == 20) openProjects(p);
            else if (slot == 22) openTemplates(p);
            else if (slot == 24) { p.sendMessage(PREFIX + tr("Abre Mis proyectos y pulsa Exportar.", "Open My projects and choose Export.")); openProjects(p); }
            else if (slot == 30) openCatalog(p, "");
            else if (slot == 28) openProjects(p);
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
            else if (h.entryBySlot.containsKey(slot)) {
                String project = h.entryBySlot.get(slot);
                if (e.isRightClick()) openProjectActions(p, project);
                else openEditor(p, project, 0);
            }
        } else if (h.kind.equals("project-actions")) {
            if (slot == 45) openProjects(p);
            else if (slot == 20) promptInput(p, InputType.DUPLICATE_PROJECT, h.project, 0, -1, "Escribe el nombre para la copia:", "Type a name for the copy:");
            else if (slot == 22) promptInput(p, InputType.RENAME_PROJECT, h.project, 0, -1, "Escribe el nuevo nombre:", "Type the new name:");
            else if (slot == 24) openDeleteConfirm(p, h.project);
            else if (slot == 30) {
                for (String line : projectService.validate(h.project)) p.sendMessage(PREFIX + "§7" + line);
                openProjectActions(p, h.project);
            } else if (slot == 32) {
                try {
                    File output = projectService.exportEssentials(h.project);
                    p.sendMessage(PREFIX + tr("Exportado: §b", "Exported: §b") + output.getPath());
                    p.sendMessage(PREFIX + tr("Revisa los avisos de metadatos avanzados.", "Review the advanced metadata warning."));
                } catch (IOException ex) { p.sendMessage(PREFIX + "§c" + ex.getMessage()); }
                openProjectActions(p, h.project);
            } else if (slot == 40) {
                try {
                    if (projectService.undo(h.project)) p.sendMessage(PREFIX + tr("Copia restaurada.", "Backup restored."));
                    else p.sendMessage(PREFIX + tr("No hay copias para restaurar.", "No backups are available."));
                } catch (IOException ex) { p.sendMessage(PREFIX + "§c" + ex.getMessage()); }
                openProjectActions(p, h.project);
            } else if (slot == 34) publishProject(p, h.project, "misc", "Created with IxKits by CS Studios");
            else if (slot == 49) { exportProject(p, h.project); openProjectActions(p, h.project); }
        } else if (h.kind.equals("catalog")) {
            if (slot == 45) openMain(p);
            else if (slot == 47 || slot == 51) openCatalog(p, h.query);
            else if (slot == 49) p.sendMessage(PREFIX + tr("Haz clic en una plantilla para descargarla.", "Click a template to download it."));
            else if (h.entryBySlot.containsKey(slot)) {
                String id = h.entryBySlot.get(slot);
                if (e.isRightClick()) {
                    catalogClient.toggleFavorite(id, p.getUniqueId().toString()).whenComplete((ignored, error) ->
                        Bukkit.getScheduler().runTask(this, () -> p.sendMessage(PREFIX + (error == null
                            ? tr("Favorito actualizado.", "Favorite updated.")
                            : tr("No se pudo actualizar el favorito: ", "Could not update favorite: ") + rootMessage(error)))));
                } else downloadCatalogTemplate(p, id);
            }
        } else if (h.kind.equals("project-delete-confirm")) {
            if (slot == 23 || slot == 45) openProjectActions(p, h.project);
            else if (slot == 21) {
                try {
                    if (projectService.moveToTrash(h.project)) p.sendMessage(PREFIX + tr("Proyecto movido a la papelera.", "Project moved to trash."));
                    else p.sendMessage(PREFIX + tr("No existe ese proyecto.", "Project not found."));
                    openProjects(p);
                } catch (IOException ex) { p.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            }
        } else if (h.kind.equals("templates")) {
            if (slot == 45) openMain(p);
            else if (slot == 20) openLoreTemplates(p, 0);
            else if (slot == 22) p.sendMessage(PREFIX + tr("Paletas: ocean, royal, inferno. Usa /ixkits paleta aplicar <nombre> sosteniendo un objeto.", "Palettes: ocean, royal, inferno. Hold an item and use /ixkits paleta aplicar <name>."));
            else if (slot == 24) p.sendMessage(PREFIX + tr("Para editar encantamientos de un kit: abre un proyecto y haz clic derecho sobre un objeto.", "To edit a kit's enchantments: open a project and right-click an item."));
            else if (slot == 30) p.sendMessage(PREFIX + tr("El catálogo online todavía no está conectado.", "The online catalog is not connected yet."));
        } else if (h.kind.equals("lore-templates")) {
            int listPage = h.project == null ? h.page : h.subPage;
            if (slot == 45) {
                if (h.project == null) openTemplates(p);
                else openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 47) openLoreTemplates(p, Math.max(0, listPage - 1), h.project, h.page, h.targetSlot);
            else if (slot == 51) openLoreTemplates(p, listPage + 1, h.project, h.page, h.targetSlot);
            else if (h.entryBySlot.containsKey(slot)) {
                if (h.project == null) applyLoreTemplateToHand(p, h.entryBySlot.get(slot));
                else applyLoreTemplateToProject(p, h.entryBySlot.get(slot), h.project, h.page, h.targetSlot);
            }
        } else if (h.kind.equals("item-editor")) {
            if (slot == 45) openEditor(p, h.project, h.page);
            else if (slot == 20) openEnchantments(p, h.project, h.page, h.targetSlot, 0);
            else if (slot == 22) openLoreTemplates(p, 0, h.project, h.page, h.targetSlot);
            else if (slot == 11) promptInput(p, InputType.ITEM_NAME, h.project, h.page, h.targetSlot, "Escribe el nombre MiniMessage del objeto:", "Type the item's MiniMessage name:");
            else if (slot == 13) promptInput(p, InputType.ITEM_LORE, h.project, h.page, h.targetSlot, "Escribe el lore; separa cada línea con |. Usa - para borrar:", "Type the lore; separate each line with |. Use - to clear:");
            else if (slot == 29) {
                ItemStack target = getProjectItem(h.project, h.page, h.targetSlot);
                if (target != null) { ItemMeta meta = target.getItemMeta(); meta.setUnbreakable(!meta.isUnbreakable()); target.setItemMeta(meta); saveProjectItem(h.project, h.page, h.targetSlot, target); }
                openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 33) {
                ItemStack target = getProjectItem(h.project, h.page, h.targetSlot);
                if (target != null && target.getItemMeta() instanceof Damageable damageable) {
                    ItemMeta meta = target.getItemMeta();
                    Damageable edit = (Damageable) meta;
                    int maximum = Math.max(0, target.getType().getMaxDurability());
                    int value = e.isShiftClick() ? 0 : edit.getDamage() + (e.isRightClick() ? -10 : 10);
                    edit.setDamage(Math.max(0, Math.min(maximum, value)));
                    target.setItemMeta(meta);
                    saveProjectItem(h.project, h.page, h.targetSlot, target);
                } else p.sendMessage(PREFIX + tr("Este objeto no tiene durabilidad.", "This item has no durability."));
                openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 35) promptInput(p, InputType.MODEL_DATA, h.project, h.page, h.targetSlot, "Escribe el custom model data numérico o - para borrarlo:", "Type the numeric custom model data or - to clear it:");
            else if (slot == 37) openAttributes(p, h.project, h.page, h.targetSlot);
            else if (slot == 39) {
                ItemStack target = getProjectItem(h.project, h.page, h.targetSlot);
                if (target != null) {
                    ItemMeta meta = target.getItemMeta();
                    if (meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                    else meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                    target.setItemMeta(meta);
                    saveProjectItem(h.project, h.page, h.targetSlot, target);
                }
                openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 41) {
                ItemStack target = getProjectItem(h.project, h.page, h.targetSlot);
                if (target != null && target.getItemMeta() instanceof LeatherArmorMeta) promptInput(p, InputType.LEATHER_COLOR, h.project, h.page, h.targetSlot, "Escribe el color como #RRGGBB o R,G,B:", "Type a color as #RRGGBB or R,G,B:");
                else p.sendMessage(PREFIX + tr("Solo disponible para armadura de cuero.", "Only available for leather armor."));
            } else if (slot == 24) {
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
        } else if (h.kind.equals("attributes")) {
            if (slot == 45) openItemEditor(p, h.project, h.page, h.targetSlot);
            else if (h.entryBySlot.containsKey(slot)) adjustAttribute(p, h, h.entryBySlot.get(slot), e.isRightClick(), e.isShiftClick());
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
        if (h.kind.equals("equipment")) {
            for (int slot : e.getRawSlots()) {
                if (slot < e.getView().getTopInventory().getSize() && !contains(EQUIP_SLOTS, slot)) { e.setCancelled(true); return; }
            }
            return;
        }
        if (!h.kind.equals("editor")) {
            e.setCancelled(true);
            return;
        }
        for (int slot : e.getRawSlots()) {
            if (slot < e.getView().getTopInventory().getSize() && !contains(EDIT_SLOTS, slot)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        if (h.kind.equals("editor")) savePage(e.getInventory(), h);
        else if (h.kind.equals("equipment")) saveEquipment(e.getInventory(), h.project);
    }

    private void savePage(Inventory inv, MenuHolder h) {
        File f = projectFile(h.project);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        boolean changed = false;
        for (int slot : EDIT_SLOTS) {
            if (!Objects.equals(y.getItemStack("pages." + h.page + "." + slot), inv.getItem(slot))) { changed = true; break; }
        }
        if (changed) projectService.backupBeforeChange(f);
        y.set("version", 1);
        y.set("format-version", 2);
        if (!y.contains("created")) y.set("created", System.currentTimeMillis());
        y.set("updated", System.currentTimeMillis());
        for (int slot : EDIT_SLOTS) y.set("pages." + h.page + "." + slot, inv.getItem(slot));
        saveYaml(y, f);
    }

    private void saveEquipment(Inventory inv, String project) {
        File file = projectFile(project);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String[] keys = {"helmet", "chestplate", "leggings", "boots", "offhand"};
        boolean changed = false;
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            ItemStack current = inv.getItem(EQUIP_SLOTS[i]);
            if (isEquipmentPlaceholder(current)) current = null;
            if (!Objects.equals(yaml.getItemStack("equipment." + keys[i]), current)) { changed = true; break; }
        }
        if (changed) projectService.backupBeforeChange(file);
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            ItemStack current = inv.getItem(EQUIP_SLOTS[i]);
            yaml.set("equipment." + keys[i], isEquipmentPlaceholder(current) ? null : current);
        }
        yaml.set("format-version", 2);
        yaml.set("updated", System.currentTimeMillis());
        saveYaml(yaml, file);
    }

    private boolean isEquipmentPlaceholder(ItemStack item) {
        return item != null && item.getType() == Material.GRAY_STAINED_GLASS_PANE
            && item.hasItemMeta() && Objects.equals(item.getItemMeta().getDisplayName(), "§8IxKits empty slot");
    }

    private ItemStack getProjectItem(String project, int page, int slot) {
        return YamlConfiguration.loadConfiguration(projectFile(project)).getItemStack("pages." + page + "." + slot);
    }

    private void saveProjectItem(String project, int page, int slot, ItemStack item) {
        File file = projectFile(project);
        projectService.backupBeforeChange(file);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("pages." + page + "." + slot, item);
        yaml.set("format-version", 2);
        yaml.set("updated", System.currentTimeMillis());
        saveYaml(yaml, file);
    }

    private void exportProject(Player p, String project) {
        File f = projectFile(project);
        if (!f.exists()) { p.sendMessage(PREFIX + tr("Proyecto no encontrado.", "Project not found.")); return; }
        File out = new File(exportsDir, project + ".ixkit.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("format-version", 2);
        y.set("ixkits-export.version", 2);
        y.set("ixkits-export.source", project);
        y.set("ixkits-export.created", System.currentTimeMillis());
        saveYaml(y, out);
        p.sendMessage(PREFIX + tr("Exportado a §bplugins/IxKits/exports/", "Exported to §bplugins/IxKits/exports/") + out.getName());
        p.sendMessage(PREFIX + tr("Formato nativo: conserva objetos y equipamiento serializados.", "Native format: preserves serialized items and equipment."));
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
        List<String> lore = y.getStringList(name + (isEnglish() ? ".lore_en" : ".lore"));
        if (lore.isEmpty()) lore = y.getStringList(name + ".lore");
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

    private void applyLoreTemplateToProject(Player p, String key, String project, int projectPage, int targetSlot) {
        File file = projectFile(project);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String itemPath = "pages." + projectPage + "." + targetSlot;
        ItemStack target = yaml.getItemStack(itemPath);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(PREFIX + tr("No se encontró el objeto del proyecto.", "Could not find the project item."));
            openEditor(p, project, projectPage);
            return;
        }
        List<String> lines = yamlListForLoreTemplate(key);
        if (lines.isEmpty()) {
            p.sendMessage(PREFIX + tr("La plantilla no contiene líneas de lore.", "That template contains no lore lines."));
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            p.sendMessage(PREFIX + tr("No se pudo editar ese objeto.", "Could not edit that item."));
            return;
        }
        meta.setLore(color(lines));
        target.setItemMeta(meta);
        yaml.set(itemPath, target);
        saveYaml(yaml, file);
        YamlConfiguration loreFile = YamlConfiguration.loadConfiguration(new File(templatesDir, "lore.yml"));
        String label = loreFile.getString(key + (isEnglish() ? ".display_en" : ".display"), loreFile.getString(key + ".display", prettyKey(key)));
        p.sendMessage(PREFIX + tr("Plantilla aplicada al objeto del kit: §b", "Template applied to kit item: §b") + label);
        openItemEditor(p, project, projectPage, targetSlot);
    }

    private List<String> yamlListForLoreTemplate(String key) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(templatesDir, "lore.yml"));
        List<String> lines = yaml.getStringList(key + (isEnglish() ? ".lore_en" : ".lore"));
        return lines.isEmpty() ? yaml.getStringList(key + ".lore") : lines;
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
    private File projectFile(String name) { return projectService == null ? new File(projectsDir, slug(name) + ".yml") : projectService.projectFile(name); }
    private void saveYaml(YamlConfiguration yaml, File file) {
        try { File parent=file.getParentFile(); if(parent!=null) parent.mkdirs(); yaml.save(file); }
        catch (IOException ex) { getLogger().severe("No se pudo guardar " + file.getName() + ": " + ex.getMessage()); }
    }

    private enum InputType {
        DUPLICATE_PROJECT, RENAME_PROJECT, ITEM_NAME, ITEM_LORE, MODEL_DATA, LEATHER_COLOR
    }

    private static final class PendingInput {
        final InputType type;
        final String project;
        final int page;
        final int slot;
        PendingInput(InputType type, String project, int page, int slot) {
            this.type = type;
            this.project = project;
            this.page = page;
            this.slot = slot;
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        final String kind, project;
        final int page, targetSlot, subPage;
        String query = "";
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