package com.csstudios.ixkits;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class IxKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private File projectsDir, templatesDir, exportsDir;
    private ProjectService projectService;
    private CatalogClient catalogClient;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> itemClipboard = new ConcurrentHashMap<>();
    private final Map<String, Long> abilityCooldowns = new ConcurrentHashMap<>();
    private NamespacedKey tntOnHitKey;
    private NamespacedKey lightningOnHitKey;
    private NamespacedKey lifeStealOnHitKey;
    private NamespacedKey tntCooldownKey;
    private NamespacedKey lightningCooldownKey;
    private NamespacedKey lifeStealCooldownKey;
    private static final int[] EQUIP_SLOTS = {19, 21, 23, 25, 31};
    private static final String[] EQUIPMENT_KEYS = {"helmet", "chestplate", "leggings", "boots", "offhand"};
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
        tntOnHitKey = new NamespacedKey(this, "ability_tnt_on_hit");
        lightningOnHitKey = new NamespacedKey(this, "ability_lightning_on_hit");
        lifeStealOnHitKey = new NamespacedKey(this, "ability_lifesteal_on_hit");
        tntCooldownKey = new NamespacedKey(this, "ability_tnt_cooldown_ms");
        lightningCooldownKey = new NamespacedKey(this, "ability_lightning_cooldown_ms");
        lifeStealCooldownKey = new NamespacedKey(this, "ability_lifesteal_cooldown_ms");
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
        if (args.length == 0) {
            if (!player.hasPermission("ixkits.admin")) {
                player.sendMessage(PREFIX + tr("Para usar el editor necesitas ixkits.admin. Para la comunidad usa /ixkits comunidad.", "The editor requires ixkits.admin. To browse Community, use /ixkits comunidad."));
            } else openMain(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean communityCommand = Set.of("comunidad", "community", "catalogo", "catálogo", "buscar", "descargar", "valorar", "favorito", "versiones", "versions").contains(sub);
        if (!player.hasPermission("ixkits.admin") && !communityCommand) {
            player.sendMessage(PREFIX + tr("No tienes permiso para administrar IxKits. Puedes navegar la comunidad con /ixkits comunidad.", "You do not have permission to administer IxKits. You can browse Community with /ixkits comunidad."));
            return true;
        }
        if (sub.equals("cancelar") || sub.equals("cancel")) { cancelPendingInput(player); return true; }
        if (sub.equals("comunidad") || sub.equals("community")) { openCommunityHub(player); return true; }
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
        if (sub.equals("rehacer") && args.length >= 2) {
            try {
                if (projectService.redo(slug(args[1]))) player.sendMessage(PREFIX + tr("Se rehizo el último cambio deshecho.", "The last undone change was redone."));
                else player.sendMessage(PREFIX + tr("No hay una acción para rehacer.", "There is no action to redo."));
            } catch (IOException ex) { player.sendMessage(PREFIX + "§c" + ex.getMessage()); }
            return true;
        }
        if (sub.equals("papelera")) { openTrash(player); return true; }
        if (sub.equals("restaurar-eliminado") && args.length >= 2) {
            try {
                if (projectService.restoreFromTrash(slug(args[1]))) player.sendMessage(PREFIX + tr("Proyecto restaurado desde la papelera.", "Project restored from trash."));
                else player.sendMessage(PREFIX + tr("No se pudo restaurar: no existe en la papelera o el ID ya está ocupado.", "Restore failed: item is not in trash or the project ID is already in use."));
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
        if (sub.equals("actualizar-publicacion") && args.length >= 3) {
            publishProjectRevision(player, args[1], slug(args[2]),
                args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Updated from IxKits");
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
        if (sub.equals("importar-mano") && args.length >= 4) {
            String project = slug(args[1]);
            int pageNumber;
            int slot;
            try {
                pageNumber = Integer.parseInt(args[2]);
                slot = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                player.sendMessage(PREFIX + tr("Uso: /ixkits importar-mano <proyecto> <página 1-2> <ranura GUI>.", "Usage: /ixkits importar-mano <project> <page 1-2> <GUI slot>."));
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!projectFile(project).isFile()) {
                player.sendMessage(PREFIX + tr("No existe ese proyecto.", "That project does not exist."));
                return true;
            }
            if (pageNumber < 1 || pageNumber > 2 || !contains(EDIT_SLOTS, slot)) {
                player.sendMessage(PREFIX + tr("Página o ranura inválida. Usa página 1-2 y una ranura editable del inventario.", "Invalid page or slot. Use page 1-2 and an editable inventory slot."));
                return true;
            }
            if (held == null || held.getType().isAir()) {
                player.sendMessage(PREFIX + tr("Sostén primero el objeto que quieras importar.", "Hold the item you want to import first."));
                return true;
            }
            File targetFile = projectFile(project);
            projectService.backupBeforeChange(targetFile);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(targetFile);
            yaml.set("format-version", 2);
            yaml.set("updated", System.currentTimeMillis());
            yaml.set("pages." + (pageNumber - 1) + "." + slot, held.clone());
            saveYaml(yaml, targetFile);
            player.sendMessage(PREFIX + tr("Objeto importado conservando sus metadatos en página ", "Item imported with its metadata preserved on page ") + pageNumber + tr(", ranura ", ", slot ") + slot + ".");
            openEditor(player, project, pageNumber - 1);
            return true;
        }
        if ((sub.equals("versiones") || sub.equals("versions")) && args.length >= 2) {
            String templateId = args[1];
            catalogClient.versions(templateId).whenComplete((versions, error) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        player.sendMessage(PREFIX + tr("No se pudo cargar el historial: ", "Could not load version history: ") + rootMessage(error));
                        return;
                    }
                    if (versions.isEmpty()) {
                        player.sendMessage(PREFIX + tr("No hay versiones disponibles para esa plantilla.", "No versions are available for that template."));
                        return;
                    }
                    player.sendMessage(PREFIX + tr("Historial de versiones de §d", "Version history for §d") + templateId + "§7:");
                    for (Map<String, Object> version : versions) {
                        Object revision = version.getOrDefault("revision", "?");
                        Object mcVersion = version.getOrDefault("mcVersion", "unknown");
                        Object author = version.getOrDefault("author", "unknown");
                        Object changelog = version.getOrDefault("changelog", "");
                        Object publishedAt = version.getOrDefault("publishedAt", "");
                        player.sendMessage("§8• §b#" + revision + " §7Minecraft §f" + mcVersion + " §8| §7" + author);
                        player.sendMessage("  §7" + String.valueOf(changelog) + " §8(" + publishedAt + ")");
                    }
                }));
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
        player.sendMessage("§f/ixkits eliminar|deshacer|rehacer|validar <proyecto>");
        player.sendMessage("§f/ixkits papelera · /ixkits restaurar-eliminado <proyecto>");
        player.sendMessage("§f/ixkits equipamiento <proyecto>");
        player.sendMessage("§f/ixkits importar-mano <proyecto> <página 1-2> <ranura GUI>");
        player.sendMessage("§f/ixkits catalogo [búsqueda] · /ixkits buscar <texto>");
        player.sendMessage("§f/ixkits publicar <proyecto> <categoría> [descripción]");
        player.sendMessage("§f/ixkits actualizar-publicacion <id> <proyecto> [cambios]");
        player.sendMessage("§f/ixkits descargar <id> · valorar <id> <1-5> · favorito <id>");
        player.sendMessage("§f/ixkits versiones <id>");
        player.sendMessage(tr("§f/ixkits lore guardar|aplicar <nombre>", "§f/ixkits lore guardar|aplicar <name>"));
        player.sendMessage(tr("§f/ixkits paleta aplicar <nombre>", "§f/ixkits paleta aplicar <name>"));
        player.sendMessage(tr("§f/ixkits encantamientos guardar|aplicar <nombre>", "§f/ixkits encantamientos guardar|aplicar <name>"));
        return true;
    }

    private void openCommunityHub(Player p) {
        Inventory inv = menu("community-hub", null, 0,
            tr("§3✦ §bIxKits §8• §0Community Hub", "§3✦ §bIxKits §8• §0Community Hub"));
        border(inv);
        inv.setItem(4, item(Material.NETHER_STAR, "§d§lIXKITS COMMUNITY",
            tr("§7Comparte creaciones con otros servidores.", "§7Share creations with other servers."),
            tr("§7Descarga kits, lores, colores y presets.", "§7Download kits, lore, colors and presets."),
            "§8CS Studios"));
        inv.setItem(20, item(Material.ENDER_CHEST, tr("§dKits y proyectos", "§dKits and projects"),
            tr("§7Explora kits publicados por la comunidad.", "§7Browse kits published by the community."),
            tr("§eClic para explorar", "§eClick to browse")));
        inv.setItem(22, item(Material.WRITABLE_BOOK, tr("§bLores de la comunidad", "§bCommunity lore"),
            tr("§7Descarga estilos de descripción para objetos.", "§7Download item description styles."),
            tr("§eClic para explorar", "§eClick to browse")));
        inv.setItem(24, item(Material.ENCHANTED_BOOK, tr("§dPresets de encantamientos", "§dEnchantment presets"),
            tr("§7Combinaciones listas para reutilizar.", "§7Reusable enchantment combinations."),
            tr("§eClic para explorar", "§eClick to browse")));
        inv.setItem(30, item(Material.PAINTING, tr("§bPaletas de colores", "§bColor palettes"),
            tr("§7Estilos de color creados por la comunidad.", "§7Color styles made by the community."),
            tr("§eClic para explorar", "§eClick to browse")));
        inv.setItem(32, item(Material.WRITABLE_BOOK, tr("§aCompartir tus recursos", "§aShare your resources"),
            tr("§7En las bibliotecas locales, haz clic derecho", "§7In local libraries, right-click"),
            tr("§7sobre un lore, paleta o preset para publicarlo.", "§7a lore, palette or preset to publish it."),
            tr("§7Para subir un kit: herramientas del proyecto.", "§7To upload a kit: project tools.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Menú principal", "§8Main menu")));
        p.openInventory(inv);
    }

    private void openCommunityResources(Player p, String type, int page) {
        if (!catalogClient.isEnabled()) {
            p.sendMessage(PREFIX + tr("La Community no está configurada. Define catalog.enabled y catalog.base-url en config.yml.", "Community is not configured. Set catalog.enabled and catalog.base-url in config.yml."));
            return;
        }
        int safePage = Math.max(1, page);
        p.sendMessage(PREFIX + tr("Cargando recursos de la comunidad…", "Loading community resources…"));
        catalogClient.searchResources(type, "", safePage, EDIT_SLOTS.length).whenComplete((resources, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    p.sendMessage(PREFIX + tr("No se pudieron cargar los recursos: ", "Could not load resources: ") + rootMessage(error));
                    return;
                }
                String typeLabel = switch (type) {
                    case "lore" -> tr("Lores", "Lore");
                    case "palette" -> tr("Paletas", "Palettes");
                    case "enchantments" -> tr("Presets de encantamientos", "Enchantment presets");
                    default -> "Community";
                };
                Inventory inv = menu("community-resources", type, safePage,
                    tr("§3✦ §bIxKits §8• §0" + typeLabel, "§3✦ §bIxKits §8• §0" + typeLabel) + " §7[" + safePage + "]");
                border(inv);
                MenuHolder holder = (MenuHolder) inv.getHolder();
                if (resources.isEmpty()) inv.setItem(22, item(Material.PAPER,
                    tr("§7Todavía no hay recursos publicados.", "§7No community resources have been published yet."),
                    tr("§7Puedes compartir uno desde la biblioteca local.", "§7You can share one from the local library.")));
                int index = 0;
                for (CatalogClient.CommunityResource resource : resources) {
                    if (index >= EDIT_SLOTS.length) break;
                    int slot = EDIT_SLOTS[index++];
                    Material icon = switch (resource.type()) {
                        case "lore" -> Material.WRITABLE_BOOK;
                        case "palette" -> Material.PAINTING;
                        case "enchantments" -> Material.ENCHANTED_BOOK;
                        default -> Material.BOOK;
                    };
                    inv.setItem(slot, item(icon, "§d" + resource.title(),
                        "§7" + tr("Autor: ", "Author: ") + resource.author(),
                        "§7" + tr("Tipo: ", "Type: ") + resource.type(),
                        "§7" + tr("Descargas: ", "Downloads: ") + resource.downloads(),
                        resource.description().isBlank() ? "§8IxKits Community" : "§8" + resource.description(),
                        "", tr("§eClic para descargar e instalar", "§eClick to download and install")));
                    holder.entryBySlot.put(slot, resource.id());
                }
                inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Community Hub", "§8Community Hub")));
                inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious")));
                inv.setItem(49, item(Material.BOOK, tr("§fCómo compartir", "§fHow to share"),
                    tr("§7Clic derecho en un recurso local.", "§7Right-click a local resource.")));
                inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext")));
                p.openInventory(inv);
            }));
    }

    private void downloadCommunityResource(Player p, String type, String id, int page) {
        catalogClient.downloadResource(id).whenComplete((resource, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    p.sendMessage(PREFIX + tr("Descarga fallida: ", "Download failed: ") + rootMessage(error));
                    return;
                }
                if (!type.equals(resource.type())) {
                    p.sendMessage(PREFIX + tr("El catálogo devolvió un tipo de recurso inesperado.", "The catalog returned an unexpected resource type."));
                    return;
                }
                String key = slug(resource.title());
                if (key.isBlank()) key = "community-resource";
                File templateFile = new File(templatesDir, switch (type) {
                    case "lore" -> "lore.yml";
                    case "palette" -> "palettes.yml";
                    case "enchantments" -> "enchantments.yml";
                    default -> "unsupported.yml";
                });
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(templateFile);
                if (yaml.contains(key)) key = (key.length() > 45 ? key.substring(0, 45) : key) + "-" + slug(id.substring(Math.max(0, id.length() - 6)));
                Map<String, Object> data = resource.data();
                if ("lore".equals(type)) {
                    Object raw = data.get("lore");
                    if (!(raw instanceof List<?> rows) || rows.isEmpty() || rows.size() > 40
                        || rows.stream().anyMatch(line -> !(line instanceof String value) || value.length() > 300)) {
                        p.sendMessage(PREFIX + tr("Este lore no pasó la validación local.", "This lore failed local validation."));
                        return;
                    }
                    List<String> loreLines = rows.stream().map(String.class::cast).toList();
                    yaml.set(key + ".display", resource.title());
                    yaml.set(key + ".display_en", resource.title());
                    yaml.set(key + ".lore", loreLines);
                    yaml.set(key + ".lore_en", loreLines);
                } else if ("palette".equals(type)) {
                    Object rawColor = data.get("color");
                    Object rawAccent = data.get("accent");
                    if (!(rawColor instanceof String color) || !isValidPaletteColor(color)
                        || (rawAccent != null && (!(rawAccent instanceof String accent) || !isValidPaletteColor((String) rawAccent)))) {
                        p.sendMessage(PREFIX + tr("Esta paleta no pasó la validación local.", "This palette failed local validation."));
                        return;
                    }
                    yaml.set(key + ".display", resource.title());
                    yaml.set(key + ".color", rawColor);
                    if (rawAccent instanceof String accent) yaml.set(key + ".accent", accent);
                } else if ("enchantments".equals(type)) {
                    if (data.isEmpty() || data.size() > 32) {
                        p.sendMessage(PREFIX + tr("Este preset no pasó la validación local.", "This preset failed local validation."));
                        return;
                    }
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        if (!(entry.getValue() instanceof Number level) || level.intValue() < 1 || level.intValue() > 32767) {
                            p.sendMessage(PREFIX + tr("Este preset contiene niveles no válidos.", "This preset contains invalid levels."));
                            return;
                        }
                    }
                    yaml.set(key + ".display", resource.title());
                    for (Map.Entry<String, Object> entry : data.entrySet()) yaml.set(key + "." + entry.getKey(), ((Number) entry.getValue()).intValue());
                } else {
                    p.sendMessage(PREFIX + tr("Tipo de recurso no compatible.", "Unsupported resource type."));
                    return;
                }
                saveYaml(yaml, templateFile);
                p.sendMessage(PREFIX + tr("Recurso instalado en la biblioteca local: §d", "Resource installed in local library: §d") + resource.title());
                openCommunityResources(p, type, page);
            }));
    }

    private boolean isValidPaletteColor(String value) {
        return value != null && (value.matches("&[0-9a-fk-or]") || value.matches("#[0-9a-fA-F]{6}") || value.matches("&#[0-9a-fA-F]{6}"));
    }

    private void publishLocalResource(Player p, String type, String key, String title) {
        if (!catalogClient.isEnabled()) {
            p.sendMessage(PREFIX + tr("La Community no está configurada. Define catalog.enabled y catalog.base-url en config.yml.", "Community is not configured. Set catalog.enabled and catalog.base-url in config.yml."));
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        File templateFile = new File(templatesDir, switch (type) {
            case "lore" -> "lore.yml";
            case "palette" -> "palettes.yml";
            case "enchantments" -> "enchantments.yml";
            default -> "unsupported.yml";
        });
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(templateFile);
        String display = title == null || title.isBlank() ? prettyKey(key) : title;
        String description = tr("Compartido desde IxKits por ", "Shared from IxKits by ") + p.getName();
        if ("lore".equals(type)) {
            List<String> lines = yaml.getStringList(key + (isEnglish() ? ".lore_en" : ".lore"));
            if (lines.isEmpty()) lines = yaml.getStringList(key + ".lore");
            if (lines.isEmpty()) { p.sendMessage(PREFIX + tr("Esta plantilla no contiene lore.", "This template contains no lore.")); return; }
            data.put("lore", lines);
            display = yaml.getString(key + (isEnglish() ? ".display_en" : ".display"), yaml.getString(key + ".display", display));
        } else if ("palette".equals(type)) {
            String color = yaml.getString(key + ".color");
            String accent = yaml.getString(key + ".accent", color);
            if (!isValidPaletteColor(color) || !isValidPaletteColor(accent)) {
                p.sendMessage(PREFIX + tr("La paleta tiene un color no válido.", "The palette contains an invalid color."));
                return;
            }
            data.put("color", color);
            data.put("accent", accent);
            display = yaml.getString(key + ".display", display);
        } else if ("enchantments".equals(type)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null || section.getKeys(false).isEmpty()) {
                p.sendMessage(PREFIX + tr("Este preset no tiene encantamientos.", "This preset has no enchantments."));
                return;
            }
            for (String enchant : section.getKeys(false)) {
                int level = section.getInt(enchant, 0);
                if (level < 1 || level > 32767) continue;
                data.put(enchant, level);
            }
            if (data.isEmpty()) { p.sendMessage(PREFIX + tr("No hay niveles válidos para publicar.", "No valid levels to publish.")); return; }
        } else {
            p.sendMessage(PREFIX + tr("Tipo de recurso no compatible.", "Unsupported resource type."));
            return;
        }
        catalogClient.publishResource(type, display, p.getName(), description, data).whenComplete((published, error) ->
            Bukkit.getScheduler().runTask(this, () -> p.sendMessage(PREFIX + (error == null
                ? tr("Recurso publicado en IxKits Community con ID §d", "Resource published to IxKits Community with ID §d") + published.id()
                : tr("No se pudo publicar el recurso: ", "Could not publish resource: ") + rootMessage(error)))));
    }

    private void openCatalog(Player p, String query) {
        openCatalog(p, query, 1);
    }

    private void openCatalog(Player p, String query, int requestedPage) {
        int page = Math.max(1, requestedPage);
        if (!catalogClient.isEnabled()) {
            p.sendMessage(PREFIX + tr("El catálogo todavía no está configurado. Activa catalog.enabled y define catalog.base-url en config.yml.", "The catalog is not configured yet. Enable catalog.enabled and set catalog.base-url in config.yml."));
            return;
        }
        p.sendMessage(PREFIX + tr("Conectando con IxKits Community...", "Connecting to IxKits Community..."));
        catalogClient.search(query, page, EDIT_SLOTS.length).whenComplete((items, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    p.sendMessage(PREFIX + tr("No se pudo cargar el catálogo: ", "Could not load catalog: ") + rootMessage(error));
                    return;
                }
                Inventory inv = menu("catalog", null, page, tr("§3✦ §bIxKits §8• §0Community", "§3✦ §bIxKits §8• §0Community") + " §7[" + page + "]");
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
                        "§7" + tr("Versión: ", "Revision: ") + Math.max(1, template.revision()),
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
                    p.sendMessage(PREFIX + tr("Plantilla importada como proyecto local: §b", "Template imported as local project: §b") + name);
                    if (p.hasPermission("ixkits.admin")) openProjects(p);
                    else openCatalog(p, "");
                } catch (IOException ex) {
                    p.sendMessage(PREFIX + "§c" + ex.getMessage());
                }
            }));
    }

    private void publishProjectRevision(Player p, String templateId, String project, String changelog) {
        if (!catalogClient.isEnabled()) {
            p.sendMessage(PREFIX + tr("Configura primero el catálogo online en config.yml.", "Configure the online catalog in config.yml first."));
            return;
        }
        File file = projectFile(project);
        if (!file.isFile()) {
            p.sendMessage(PREFIX + tr("No existe ese proyecto.", "Project not found."));
            return;
        }
        List<String> issues = projectService.validate(project);
        if (issues.stream().anyMatch(line -> line.startsWith("ERROR:"))) {
            p.sendMessage(PREFIX + tr("El proyecto tiene errores; corrígelos antes de publicarlo.", "The project has errors; fix them before publishing."));
            for (String issue : issues) if (issue.startsWith("ERROR:")) p.sendMessage(PREFIX + "§c" + issue);
            return;
        }
        try {
            String yaml = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            String version = getConfig().getString("catalog.minecraft-version", "1.21.11");
            catalogClient.publishRevision(templateId, version, changelog, yaml).whenComplete((updated, error) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) p.sendMessage(PREFIX + tr("No se pudo actualizar la publicación: ", "Could not update publication: ") + rootMessage(error));
                    else p.sendMessage(PREFIX + tr("Publicación actualizada a la versión §b", "Publication updated to revision §b") + updated.revision());
                }));
        } catch (IOException ex) {
            p.sendMessage(PREFIX + "§c" + ex.getMessage());
        }
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
        Inventory inv = menu("main", null, 0, tr("§3✦ §bIxKits §8• §0Kit Creation Studio", "§3✦ §bIxKits §8• §0Kit Creation Studio"));
        border(inv);
        File[] files = projectsDir.listFiles((directory, name) -> name.endsWith(".yml"));
        int projectCount = files == null ? 0 : files.length;
        inv.setItem(4, itemWithLore(Material.NETHER_STAR, "§b§lCS STUDIOS §8• §fIxKits",
            List.of("§7Kit Creation Studio", "§8Crea · personaliza · comparte", "",
                tr("§7Proyectos guardados: §b", "§7Saved projects: §b") + projectCount,
                "§8Desarrollado por CS Studios")));
        inv.setItem(20, item(Material.CHEST, tr("§b§lMis proyectos", "§b§lMy projects"),
            tr("§7Abrir, buscar, duplicar y organizar kits.", "§7Open, search, duplicate and organize kits."),
            tr("§eClic para abrir la biblioteca", "§eClick to open project library")));
        inv.setItem(22, item(Material.LIME_CONCRETE, tr("§a§lCrear proyecto", "§a§lCreate project"),
            tr("§7Empieza un kit desde cero.", "§7Start a kit from scratch."),
            tr("§eClic y escribe el nombre", "§eClick and type a name")));
        inv.setItem(24, item(Material.BOOKSHELF, tr("§bBiblioteca de plantillas", "§bTemplate library"),
            tr("§7Lore, colores y presets de encantamientos.", "§7Lore, colors and enchantment presets."),
            tr("§eClic para explorar", "§eClick to browse")));
        inv.setItem(29, item(Material.IRON_CHESTPLATE, tr("§bHerramientas e intercambio", "§bTools & imports/exports"),
            tr("§7Importar, validar y exportar proyectos.", "§7Import, validate and export projects."),
            tr("§eClic para elegir un proyecto", "§eClick to choose a project")));
        inv.setItem(31, item(Material.ENDER_CHEST, "§d§lIxKits Community",
            tr("§7Kits, lores, paletas y encantamientos.", "§7Kits, lore, palettes and enchantments."),
            tr("§eClic para explorar la comunidad", "§eClick to explore Community")));
        inv.setItem(33, item(Material.WRITABLE_BOOK, tr("§eIdioma / Language", "§eLanguage / Idioma"),
            tr("§7Actual: " + (isEnglish() ? "English" : "Español"), "§7Current: " + (isEnglish() ? "English" : "Spanish")),
            tr("§eClic para cambiar", "§eClick to change")));
        inv.setItem(38, item(Material.COMPASS, tr("§fAyuda rápida", "§fQuick help"),
            "§7/ixkits crear <nombre>", "§7/ixkits editar <proyecto>",
            "§7/ixkits importar-mano <proyecto> <página> <ranura>"));
        inv.setItem(40, item(Material.PAPER, "§f§lIxKits §7v0.1.0",
            tr("§7Editor administrativo de kits", "§7Administrative kit editor"),
            "§8Creado por CS Studios",
            "§bhttps://discord.gg/CnHSjMYDhr"));
        p.openInventory(inv);
    }

    private void openProjects(Player p) {
        openProjects(p, 0, "");
    }

    private void openProjects(Player p, int requestedPage, String query) {
        String safeQuery = query == null ? "" : query.trim();
        File[] files = projectsDir.listFiles((d, n) -> n.endsWith(".yml"));
        List<File> matching = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        matching.sort(Comparator.comparing(File::getName));
        if (!safeQuery.isBlank()) {
            String needle = safeQuery.toLowerCase(Locale.ROOT);
            matching.removeIf(file -> {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String display = yaml.getString("name", file.getName());
                return !display.toLowerCase(Locale.ROOT).contains(needle)
                    && !file.getName().toLowerCase(Locale.ROOT).contains(needle);
            });
        }
        int pages = Math.max(1, (int) Math.ceil(matching.size() / (double) EDIT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        String title = tr("§3✦ §bIxKits §8• §0Mis proyectos", "§3✦ §bIxKits §8• §0My projects")
            + " §7[" + (page + 1) + "/" + pages + "]";
        Inventory inv = menu("projects", null, page, title);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        holder.query = safeQuery;
        border(inv);
        for (int index = page * EDIT_SLOTS.length; index < Math.min(matching.size(), (page + 1) * EDIT_SLOTS.length); index++) {
            File file = matching.get(index);
            String id = file.getName().replaceFirst("\\.yml$", "");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            int slot = EDIT_SLOTS[index - page * EDIT_SLOTS.length];
            inv.setItem(slot, item(Material.CHEST, "§b" + yaml.getString("name", id),
                tr("§7Proyecto editable", "§7Editable project"), "§8ID: " + id,
                tr("§eClic izquierdo: abrir", "§eLeft-click: open"),
                tr("§eClic derecho: herramientas", "§eRight-click: tools")));
            holder.entryBySlot.put(slot, id);
        }
        if (matching.isEmpty()) {
            inv.setItem(22, item(Material.PAPER,
                safeQuery.isBlank() ? tr("§7Todavía no hay proyectos", "§7No projects yet") : tr("§7No hay resultados", "§7No results"),
                safeQuery.isBlank() ? tr("§eCrea tu primer proyecto", "§eCreate your first project") : tr("§7Prueba otra búsqueda.", "§7Try another search.")));
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Menú principal", "§8Main menu")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious"), tr("§7Página anterior", "§7Previous page")));
        inv.setItem(48, item(Material.COMPASS, tr("§bBuscar proyectos", "§bSearch projects"),
            safeQuery.isBlank() ? tr("§7Buscar por nombre", "§7Search by name") : "§7Filtro: " + safeQuery,
            tr("§eClic para escribir", "§eClick to type")));
        inv.setItem(49, item(Material.LIME_CONCRETE, tr("§aCrear proyecto", "§aCreate project"), tr("§eClic para escribir el nombre", "§eClick to type a name")));
        inv.setItem(50, item(Material.CHEST, tr("§6Papelera", "§6Trash"), tr("§7Restaurar proyectos eliminados.", "§7Restore deleted projects."), tr("§eClic para abrir", "§eClick to open")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext"), tr("§7Página siguiente", "§7Next page")));
        inv.setItem(53, item(Material.PAPER, "§f" + matching.size() + " / " + (files == null ? 0 : files.length),
            tr("§7Proyectos encontrados", "§7Projects found")));
        p.openInventory(inv);
    }

    private void openTemplates(Player p) {
        Inventory inv = menu("templates", null, 0, tr("§3✦ §bIxKits §8• §0Biblioteca", "§3✦ §bIxKits §8• §0Library"));
        border(inv);
        inv.setItem(20, item(Material.WRITABLE_BOOK, tr("§bPlantillas de lore", "§bLore templates"), tr("§7Diseños para varias rarezas y estilos.", "§7Layouts for multiple rarities and styles."), tr("§eClic para aplicar o previsualizar", "§eClick to apply or preview")));
        inv.setItem(22, item(Material.PAINTING, tr("§bPaletas de colores", "§bColor palettes"), tr("§7Estilos para nombres y líneas de lore.", "§7Styles for names and lore lines."), tr("§eClic para abrir la paleta", "§eClick to open palettes")));
        inv.setItem(24, item(Material.ENCHANTED_BOOK, tr("§bPresets de encantamientos", "§bEnchantment presets"), tr("§7Combinaciones editables y reutilizables.", "§7Editable, reusable combinations."), tr("§eClic para abrir la biblioteca", "§eClick to open the library")));
        inv.setItem(30, item(Material.ENDER_CHEST, "§dIxKits Community", tr("§7Descarga kits y comparte proyectos.", "§7Download kits and share projects."), tr("§eClic para abrir el catálogo online", "§eClick to open online catalog")));
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

    private void openTrash(Player p) {
        List<String> trashed = projectService.listTrash();
        Inventory inv = menu("trash", null, 0, tr("§3✦ §bIxKits §8• §0Papelera", "§3✦ §bIxKits §8• §0Trash"));
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        int index = 0;
        for (String id : trashed) {
            if (index >= EDIT_SLOTS.length) break;
            int slot = EDIT_SLOTS[index++];
            inv.setItem(slot, item(Material.CHEST, "§6" + projectService.trashedProjectName(id),
                "§8ID: " + id,
                tr("§7Proyecto conservado en papelera.", "§7Project retained in trash."),
                tr("§eClic para restaurar", "§eClick to restore")));
            holder.entryBySlot.put(slot, id);
        }
        if (trashed.isEmpty()) inv.setItem(22, item(Material.PAPER, tr("§7La papelera está vacía.", "§7Trash is empty.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back"), tr("§8Biblioteca", "§8Project library")));
        inv.setItem(49, item(Material.CLOCK, tr("§bActualizar", "§bRefresh")));
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
        inv.setItem(34, item(Material.ENDER_CHEST, tr("§dPublicar en Community", "§dPublish to Community"), tr("§7Comparte el kit con otros servidores.", "§7Share this kit with other servers."), tr("§eClic para elegir categoría y descripción", "§eClick to choose category and description"), tr("§8Requiere la Community online configurada.", "§8Requires online Community configuration.")));
        inv.setItem(38, item(Material.ENDER_CHEST, tr("§bActualizar publicación", "§bUpdate publication"), tr("§7Subir nueva versión de esta plantilla.", "§7Upload a new version of this template."), tr("§eClic y escribe el ID en el chat.", "§eClick and type the template ID in chat.")));
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
        openEquipment(p, project, 0);
    }

    private void openEquipment(Player p, String project, int returnPage) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(projectFile(project));
        Inventory inv = menu("equipment", project, Math.max(0, Math.min(1, returnPage)), tr("§3✦ §bIxKits §8• §0Equipamiento", "§3✦ §bIxKits §8• §0Equipment"));
        border(inv);
        inv.setItem(19, gearSlot(yaml.getItemStack("equipment.helmet"), Material.LEATHER_HELMET, tr("Casco", "Helmet")));
        inv.setItem(21, gearSlot(yaml.getItemStack("equipment.chestplate"), Material.LEATHER_CHESTPLATE, tr("Peto", "Chestplate")));
        inv.setItem(23, gearSlot(yaml.getItemStack("equipment.leggings"), Material.LEATHER_LEGGINGS, tr("Pantalones", "Leggings")));
        inv.setItem(25, gearSlot(yaml.getItemStack("equipment.boots"), Material.LEATHER_BOOTS, tr("Botas", "Boots")));
        inv.setItem(31, gearSlot(yaml.getItemStack("equipment.offhand"), Material.SHIELD, tr("Mano secundaria", "Offhand")));
        inv.setItem(40, item(Material.PAPER, tr("§fCómo usarlo", "§fHow to use"), tr("§7Mayús + clic equipa automáticamente.", "§7Shift-click equips automatically."), tr("§7Clic derecho en una pieza para editarla.", "§7Right-click a piece to edit it."), tr("§7Se guarda al cambiar de menú.", "§7Saved when changing menus.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al proyecto", "§7Back to project")));
        p.openInventory(inv);
    }

    private void openPotionEffects(Player p, String project, int projectPage, int targetSlot, int requestedPage) {
        ItemStack target = getProjectItem(project, projectPage, targetSlot);
        if (target == null || !(target.getItemMeta() instanceof PotionMeta potionMeta)) {
            p.sendMessage(PREFIX + tr("Este objeto no admite efectos de poción.", "This item does not support potion effects."));
            openItemEditor(p, project, projectPage, targetSlot);
            return;
        }
        List<PotionEffectType> types = new ArrayList<>();
        for (PotionEffectType effectType : Registry.MOB_EFFECT) types.add(effectType);
        types.sort(Comparator.comparing(type -> type.getKey().toString()));
        int pages = Math.max(1, (int) Math.ceil(types.size() / (double) EDIT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = menu("potion-effects", project, projectPage, targetSlot, page,
            tr("§3✦ §bIxKits §8• §0Efectos personalizados", "§3✦ §bIxKits §8• §0Custom effects") + " §7[" + (page + 1) + "/" + pages + "]");
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        inv.setItem(4, target.clone());
        for (int i = page * EDIT_SLOTS.length; i < Math.min(types.size(), (page + 1) * EDIT_SLOTS.length); i++) {
            PotionEffectType type = types.get(i);
            int slot = EDIT_SLOTS[i - page * EDIT_SLOTS.length];
            PotionEffect active = potionMeta.getCustomEffects().stream()
                .filter(effect -> effect.getType().getKey().equals(type.getKey())).findFirst().orElse(null);
            List<String> lore = new ArrayList<>();
            if (active == null) {
                lore.add(tr("§7No aplicado", "§7Not applied"));
                lore.add(tr("§aClic izquierdo: añadir nivel I", "§aLeft-click: add level I"));
            } else {
                lore.add(tr("§aNivel: §f", "§aLevel: §f") + (active.getAmplifier() + 1));
                lore.add(tr("§7Duración: §f", "§7Duration: §f") + (active.getDuration() / 20) + "s");
                lore.add(tr("§eClic izquierdo: quitar", "§eLeft-click: remove"));
                lore.add(tr("§bClic derecho: subir nivel", "§bRight-click: increase level"));
                lore.add(tr("§cMayús + clic: quitar", "§cShift-click: remove"));
            }
            inv.setItem(slot, itemWithLore(active == null ? Material.POTION : Material.LIME_DYE,
                "§b" + prettyKey(type.getKey().getKey()), lore));
            holder.entryBySlot.put(slot, type.getKey().toString());
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver a tipos de poción", "§7Back to potion types")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious"), tr("§7Página anterior", "§7Previous page")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext"), tr("§7Página siguiente", "§7Next page")));
        inv.setItem(49, item(Material.CLOCK, tr("§fDuración inicial: 30s", "§fDefault duration: 30s"),
            tr("§7Los efectos se pueden subir de nivel.", "§7Effects can be leveled up."),
            tr("§7Clic izquierdo: alternar.", "§7Left-click: toggle.")));
        p.openInventory(inv);
    }

    private void openSpecialProperties(Player p, String project, int page, int targetSlot) {
        ItemStack target = getProjectItem(project, page, targetSlot);
        if (target == null || target.getType().isAir()) { openEditor(p, project, page); return; }
        Inventory inv = menu("special-properties", project, page, targetSlot, 0,
            tr("§3✦ §bIxKits §8• §0Propiedades especiales", "§3✦ §bIxKits §8• §0Special properties"));
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        inv.setItem(4, target.clone());
        ItemMeta meta = target.getItemMeta();
        if (target.getType() == Material.WRITTEN_BOOK && meta instanceof BookMeta) {
            inv.setItem(20, item(Material.WRITTEN_BOOK, tr("§bEditor de libro", "§bBook editor"), tr("§7Título, autor y páginas MiniMessage.", "§7Title, author and MiniMessage pages.")));
            holder.entryBySlot.put(20, "book");
        }
        if (meta instanceof BannerMeta) {
            inv.setItem(22, item(Material.WHITE_BANNER, tr("§bPatrones de estandarte", "§bBanner patterns"), tr("§7Añadir y quitar patrones por GUI.", "§7Add and remove patterns from a GUI.")));
            holder.entryBySlot.put(22, "banner");
        }
        if (meta instanceof ArmorMeta) {
            inv.setItem(24, item(Material.NETHERITE_CHESTPLATE, tr("§bAdornos de armadura", "§bArmor trims"), tr("§7Elegir patrón y material.", "§7Choose a trim pattern and material.")));
            holder.entryBySlot.put(24, "trim");
        }
        if (meta instanceof SkullMeta) {
            inv.setItem(30, item(Material.PLAYER_HEAD, tr("§bCabeza personalizada", "§bCustom head"), tr("§7Asignar o quitar propietario.", "§7Set or clear head owner.")));
            holder.entryBySlot.put(30, "skull");
        }
        if (meta instanceof FireworkMeta) {
            inv.setItem(32, item(Material.FIREWORK_ROCKET, tr("§bEditor de fuegos artificiales", "§bFirework editor"), tr("§7Potencia, forma, color y efectos.", "§7Power, shape, color and effects.")));
            holder.entryBySlot.put(32, "firework");
        }
        if (holder.entryBySlot.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, tr("§7Sin propiedades especiales disponibles", "§7No special properties available"),
                tr("§7Este objeto no tiene un editor compatible.", "§7This item has no compatible special editor.")));
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al objeto", "§7Back to item")));
        p.openInventory(inv);
    }

    private void openBookEditor(Player p, String project, int page, int targetSlot) {
        ItemStack target = getProjectItem(project, page, targetSlot);
        if (target == null || !(target.getItemMeta() instanceof BookMeta bookMeta) || target.getType() != Material.WRITTEN_BOOK) {
            p.sendMessage(PREFIX + tr("El editor de libro requiere un libro escrito.", "The book editor requires a written book."));
            openItemEditor(p, project, page, targetSlot);
            return;
        }
        Inventory inv = menu("book-editor", project, page, targetSlot, 0,
            tr("§3✦ §bIxKits §8• §0Editor de libro", "§3✦ §bIxKits §8• §0Book editor"));
        border(inv);
        inv.setItem(4, target.clone());
        inv.setItem(20, item(Material.NAME_TAG, tr("§bTítulo", "§bTitle"),
            "§7" + (bookMeta.hasTitle() ? bookMeta.getTitle() : tr("Sin título", "Untitled")),
            tr("§eClic para escribir", "§eClick to type")));
        inv.setItem(22, item(Material.PLAYER_HEAD, tr("§bAutor", "§bAuthor"),
            "§7" + (bookMeta.hasAuthor() ? bookMeta.getAuthor() : tr("Sin autor", "No author")),
            tr("§eClic para escribir", "§eClick to type")));
        List<String> pagePreview = bookMeta.getPages().stream().limit(3).map(ChatColor::stripColor).toList();
        inv.setItem(24, item(Material.WRITABLE_BOOK, tr("§bContenido", "§bContents"),
            "§7" + bookMeta.getPageCount() + " " + tr("página(s)", "page(s)"),
            pagePreview.isEmpty() ? tr("§8Sin contenido", "§8No content") : "§f" + String.join(" / ", pagePreview),
            tr("§eClic para editar todas las páginas", "§eClick to edit all pages"),
            tr("§8Separa páginas con |", "§8Separate pages with |")));
        inv.setItem(31, item(Material.PAPER, tr("§fUso", "§fHow to use"),
            tr("§7Escribe texto compatible con", "§7Enter text compatible with"),
            tr("§7MiniMessage en el chat.", "§7MiniMessage in chat."),
            tr("§7Usa - para vaciar un campo.", "§7Use - to clear a field.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver a propiedades", "§7Back to special properties")));
        p.openInventory(inv);
    }

    private void openBannerEditor(Player p, String project, int projectPage, int targetSlot, int requestedPage, String requestedColor) {
        ItemStack target = getProjectItem(project, projectPage, targetSlot);
        if (target == null || !(target.getItemMeta() instanceof BannerMeta bannerMeta)) {
            p.sendMessage(PREFIX + tr("Este objeto no admite patrones de estandarte.", "This item does not support banner patterns."));
            openSpecialProperties(p, project, projectPage, targetSlot);
            return;
        }
        List<PatternType> patterns = new ArrayList<>();
        for (PatternType pattern : Registry.BANNER_PATTERN) patterns.add(pattern);
        patterns.sort(Comparator.comparing(pattern -> pattern.getKey().toString()));
        int pages = Math.max(1, (int) Math.ceil(patterns.size() / (double) EDIT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        String colorName = requestedColor == null ? "WHITE" : requestedColor;
        try { DyeColor.valueOf(colorName); } catch (IllegalArgumentException ex) { colorName = "WHITE"; }
        Inventory inv = menu("banner-editor", project, projectPage, targetSlot, page,
            tr("§3✦ §bIxKits §8• §0Patrones", "§3✦ §bIxKits §8• §0Patterns") + " §7[" + (page + 1) + "/" + pages + "]");
        MenuHolder holder = (MenuHolder) inv.getHolder();
        holder.query = colorName;
        border(inv);
        inv.setItem(4, target.clone());
        for (int i = page * EDIT_SLOTS.length; i < Math.min(patterns.size(), (page + 1) * EDIT_SLOTS.length); i++) {
            PatternType pattern = patterns.get(i);
            int slot = EDIT_SLOTS[i - page * EDIT_SLOTS.length];
            long count = bannerMeta.getPatterns().stream().filter(existing -> existing.getPattern().getKey().equals(pattern.getKey())).count();
            inv.setItem(slot, item(Material.WHITE_BANNER, "§b" + prettyKey(pattern.getKey().getKey()),
                tr("§7En este objeto: ", "§7On this item: ") + count,
                tr("§eClic: añadir patrón", "§eClick: add pattern"),
                tr("§cMayús + clic: quitar uno", "§cShift-click: remove one")));
            holder.entryBySlot.put(slot, pattern.getKey().toString());
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver a propiedades", "§7Back to special properties")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious")));
        inv.setItem(49, item(Material.INK_SAC, tr("§bColor actual: ", "§bCurrent color: ") + prettyKey(colorName),
            tr("§7Clic para cambiar el color", "§7Click to change color"),
            tr("§7Máximo seis patrones", "§7Maximum six patterns"),
            tr("§7Patrones actuales: ", "§7Current patterns: ") + bannerMeta.numberOfPatterns()));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext")));
        p.openInventory(inv);
    }

    private void openTrimEditor(Player p, String project, int projectPage, int targetSlot, int requestedPage, String requestedMaterial) {
        ItemStack target = getProjectItem(project, projectPage, targetSlot);
        if (target == null || !(target.getItemMeta() instanceof ArmorMeta armorMeta)) {
            p.sendMessage(PREFIX + tr("Este objeto no admite adornos de armadura.", "This item does not support armor trims."));
            openSpecialProperties(p, project, projectPage, targetSlot);
            return;
        }
        List<TrimPattern> patterns = new ArrayList<>();
        for (TrimPattern pattern : Registry.TRIM_PATTERN) patterns.add(pattern);
        patterns.sort(Comparator.comparing(pattern -> pattern.getKey().toString()));
        List<TrimMaterial> materials = new ArrayList<>();
        for (TrimMaterial material : Registry.TRIM_MATERIAL) materials.add(material);
        materials.sort(Comparator.comparing(material -> material.getKey().toString()));
        if (materials.isEmpty() || patterns.isEmpty()) {
            p.sendMessage(PREFIX + tr("Paper no expone patrones de trim en esta versión.", "Paper exposes no trim patterns in this version."));
            openItemEditor(p, project, projectPage, targetSlot);
            return;
        }
        String materialKey = requestedMaterial;
        if ((materialKey == null || materialKey.isBlank()) && armorMeta.hasTrim()) {
            materialKey = armorMeta.getTrim().getMaterial().getKey().getKey();
        }
        boolean materialFound = false;
        if (materialKey != null) {
            for (TrimMaterial candidate : materials) if (candidate.getKey().getKey().equals(materialKey)) { materialFound = true; break; }
        }
        if (!materialFound) materialKey = materials.get(0).getKey().getKey();
        int pages = Math.max(1, (int) Math.ceil(patterns.size() / (double) EDIT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = menu("trim-editor", project, projectPage, targetSlot, page,
            tr("§3✦ §bIxKits §8• §0Adornos de armadura", "§3✦ §bIxKits §8• §0Armor trims") + " §7[" + (page + 1) + "/" + pages + "]");
        MenuHolder holder = (MenuHolder) inv.getHolder();
        holder.query = materialKey;
        border(inv);
        inv.setItem(4, target.clone());
        for (int i = page * EDIT_SLOTS.length; i < Math.min(patterns.size(), (page + 1) * EDIT_SLOTS.length); i++) {
            TrimPattern pattern = patterns.get(i);
            int slot = EDIT_SLOTS[i - page * EDIT_SLOTS.length];
            boolean selected = armorMeta.hasTrim() && armorMeta.getTrim().getPattern().getKey().equals(pattern.getKey());
            inv.setItem(slot, item(selected ? Material.LIME_DYE : Material.AMETHYST_SHARD,
                "§b" + prettyKey(pattern.getKey().getKey()),
                selected ? tr("§aPatrón actual", "§aCurrent pattern") : tr("§7Patrón disponible", "§7Available pattern"),
                tr("§7Material: ", "§7Material: ") + prettyKey(materialKey),
                tr("§eClic para aplicar", "§eClick to apply"),
                tr("§cMayús + clic: quitar trim", "§cShift-click: remove trim")));
            holder.entryBySlot.put(slot, pattern.getKey().toString());
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver a propiedades", "§7Back to special properties")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious")));
        inv.setItem(49, item(Material.IRON_INGOT, tr("§bMaterial: ", "§bMaterial: ") + prettyKey(materialKey),
            tr("§7Clic para cambiar material", "§7Click to change material")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext")));
        p.openInventory(inv);
    }

    private void openFireworkEditor(Player p, String project, int projectPage, int targetSlot, String selection) {
        ItemStack target = getProjectItem(project, projectPage, targetSlot);
        if (target == null || !(target.getItemMeta() instanceof FireworkMeta fireworkMeta)) {
            p.sendMessage(PREFIX + tr("Este objeto no es un cohete de fuegos artificiales.", "This item is not a firework rocket."));
            openSpecialProperties(p, project, projectPage, targetSlot);
            return;
        }
        String[] choices = (selection == null ? "BALL|RED" : selection).split("\\|", -1);
        String typeName = choices.length > 0 ? choices[0] : "BALL";
        String colorName = choices.length > 1 ? choices[1] : "RED";
        try { FireworkEffect.Type.valueOf(typeName); } catch (IllegalArgumentException ex) { typeName = "BALL"; }
        try { DyeColor.valueOf(colorName); } catch (IllegalArgumentException ex) { colorName = "RED"; }
        Inventory inv = menu("firework-editor", project, projectPage, targetSlot, 0,
            tr("§3✦ §bIxKits §8• §0Fuegos artificiales", "§3✦ §bIxKits §8• §0Fireworks"));
        MenuHolder holder = (MenuHolder) inv.getHolder();
        holder.query = typeName + "|" + colorName;
        border(inv);
        inv.setItem(4, target.clone());
        inv.setItem(20, item(Material.CLOCK, tr("§bPotencia: ", "§bPower: ") + fireworkMeta.getPower(),
            tr("§aClic: aumentar", "§aClick: increase"), tr("§eClic derecho: reducir", "§eRight-click: decrease"), "§7Rango de editor: 0-3"));
        inv.setItem(22, item(Material.FIREWORK_ROCKET, tr("§bAñadir efecto", "§bAdd effect"),
            tr("§7Forma: ", "§7Shape: ") + prettyKey(typeName),
            tr("§7Color: ", "§7Color: ") + prettyKey(colorName),
            tr("§eClic para añadir", "§eClick to add")));
        inv.setItem(24, item(Material.FIREWORK_STAR, tr("§bForma del efecto", "§bEffect shape"),
            "§7" + prettyKey(typeName), tr("§eClic para cambiar", "§eClick to cycle")));
        inv.setItem(26, item(Material.ORANGE_DYE, tr("§bColor del efecto", "§bEffect color"),
            "§7" + prettyKey(colorName), tr("§eClic para cambiar", "§eClick to cycle")));
        inv.setItem(30, item(Material.SHEARS, tr("§cQuitar último efecto", "§cRemove last effect"),
            tr("§7Efectos actuales: ", "§7Current effects: ") + fireworkMeta.getEffectsSize()));
        inv.setItem(32, item(Material.BARRIER, tr("§cQuitar todos los efectos", "§cClear all effects")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver a propiedades", "§7Back to special properties")));
        p.openInventory(inv);
    }

    private void openPotionTypes(Player p, String project, int projectPage, int targetSlot, int requestedSubPage) {
        ItemStack target = getProjectItem(project, projectPage, targetSlot);
        if (target == null || !(target.getItemMeta() instanceof PotionMeta potionMeta)) {
            p.sendMessage(PREFIX + tr("Este objeto no admite tipos de poción.", "This item does not support potion types."));
            openItemEditor(p, project, projectPage, targetSlot);
            return;
        }
        List<PotionType> types = new ArrayList<>(Arrays.asList(PotionType.values()));
        types.sort(Comparator.comparing(type -> type.getKey().toString()));
        int pages = Math.max(1, (int) Math.ceil(types.size() / (double) EDIT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedSubPage, pages - 1));
        Inventory inv = menu("potion-types", project, projectPage, targetSlot, page,
            tr("§3✦ §bIxKits §8• §0Tipos de poción", "§3✦ §bIxKits §8• §0Potion types") + " §7[" + (page + 1) + "/" + pages + "]");
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        PotionType current = potionMeta.getBasePotionType();
        for (int i = page * EDIT_SLOTS.length; i < Math.min(types.size(), (page + 1) * EDIT_SLOTS.length); i++) {
            PotionType type = types.get(i);
            int slot = EDIT_SLOTS[i - page * EDIT_SLOTS.length];
            boolean selected = type == current;
            inv.setItem(slot, item(selected ? Material.LIME_DYE : Material.POTION,
                "§b" + prettyKey(type.name()),
                selected ? tr("§aTipo seleccionado", "§aSelected type") : tr("§7Tipo disponible", "§7Available type"),
                tr("§eClic para aplicar", "§eClick to apply")));
            holder.entryBySlot.put(slot, type.name());
        }
        inv.setItem(4, target.clone());
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al objeto", "§7Back to item")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious"), tr("§7Página anterior", "§7Previous page")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext"), tr("§7Página siguiente", "§7Next page")));
        inv.setItem(49, item(Material.POTION, tr("§fEfectos personalizados", "§fCustom effects"),
            potionMeta.hasCustomEffects() ? tr("§aTiene efectos personalizados", "§aHas custom effects") : tr("§7No tiene efectos personalizados", "§7No custom effects"),
            tr("§eClic para gestionar efectos", "§eClick to manage effects")));
        p.openInventory(inv);
    }

    private void openFlags(Player p, String project, int page, int targetSlot) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(projectFile(project));
        ItemStack target = yaml.getItemStack(storagePath(page, targetSlot));
        if (target == null || target.getType().isAir()) { openEditor(p, project, page); return; }
        Inventory inv = menu("flags", project, page, targetSlot, 0, tr("§3✦ §bIxKits §8• §0Visibilidad", "§3✦ §bIxKits §8• §0Item flags"));
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        ItemFlag[] flags = ItemFlag.values();
        for (int i = 0; i < flags.length && i < EDIT_SLOTS.length; i++) {
            ItemFlag flag = flags[i];
            int slot = EDIT_SLOTS[i];
            boolean enabled = target.getItemMeta().hasItemFlag(flag);
            inv.setItem(slot, item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "§b" + prettyKey(flag.name()),
                enabled ? tr("§aOculto en la descripción del objeto", "§aHidden from the item tooltip")
                    : tr("§7Visible en la descripción del objeto", "§7Visible in the item tooltip"),
                tr("§eClic para alternar", "§eClick to toggle")));
            holder.entryBySlot.put(slot, flag.name());
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al objeto", "§7Back to item")));
        inv.setItem(49, item(Material.PAPER, tr("§fQué hacen los flags", "§fAbout item flags"),
            tr("§7Ocultan secciones de la descripción.", "§7Hide sections of the item's tooltip."),
            tr("§7No quitan los datos del objeto.", "§7They do not remove item data.")));
        p.openInventory(inv);
    }

    private void openAttributes(Player p, String project, int page, int targetSlot) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(projectFile(project));
        ItemStack target = yaml.getItemStack(storagePath(page, targetSlot));
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
        String path = storagePath(holder.page, holder.targetSlot);
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
        promptInput(p, type, project, page, slot, promptEs, promptEn, null);
    }

    private void promptInput(Player p, InputType type, String project, int page, int slot, String promptEs, String promptEn, String argument) {
        pendingInputs.put(p.getUniqueId(), new PendingInput(type, project, page, slot, -1, argument));
        p.closeInventory();
        p.sendMessage(PREFIX + tr(promptEs, promptEn));
        sendCancelPrompt(p);
    }

    private void sendCancelPrompt(Player p) {
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        net.kyori.adventure.text.Component beginning = legacy.deserialize(PREFIX + "§7Pulsa ");
        net.kyori.adventure.text.Component cancel = net.kyori.adventure.text.Component
            .text(tr("Cancelar", "Cancel"), net.kyori.adventure.text.format.NamedTextColor.RED)
            .decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ixkits cancelar"))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                net.kyori.adventure.text.Component.text(tr("Haz clic para cancelar la edición.", "Click to cancel this edit."))));
        net.kyori.adventure.text.Component ending = legacy.deserialize(
            tr(" §7o escribe §fcancelar §7en el chat.", " §7or type §fcancel §7in chat."));
        p.sendMessage(beginning.append(cancel).append(ending));
    }

    private boolean isCancelInput(String input) {
        if (input == null) return false;
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cancelar") || normalized.equals("cancel") || normalized.equals("-cancelar") || normalized.equals("-cancel");
    }

    private void returnFromCancelledInput(Player player, PendingInput pending) {
        player.sendMessage(PREFIX + tr("Edición cancelada.", "Edit cancelled."));
        if (pending.type == InputType.PROJECT_SEARCH) openProjects(player);
        else if (pending.type == InputType.CATALOG_SEARCH) openCatalog(player, "");
        else if (pending.project != null && projectFile(pending.project).isFile()
            && (pending.type == InputType.LORE_ADD_LINE || pending.type == InputType.LORE_EDIT_LINE || pending.type == InputType.LORE_CLEAR_CONFIRM))
            openLoreEditor(player, pending.project, pending.page, pending.slot, 0);
        else if (pending.project != null && projectFile(pending.project).isFile()
            && (pending.type == InputType.PUBLISH_PROJECT_CATEGORY || pending.type == InputType.PUBLISH_PROJECT_DESCRIPTION))
            openProjectActions(player, pending.project);
        else if (pending.project != null && projectFile(pending.project).isFile() && pending.type == InputType.ABILITY_COOLDOWN)
            openAbilities(player, pending.project, pending.page, pending.slot);
        else if (pending.project != null && projectFile(pending.project).isFile() && pending.type == InputType.ENCHANTMENT_LEVEL)
            openEnchantments(player, pending.project, pending.page, pending.slot, 0);
        else if (pending.project != null && projectFile(pending.project).isFile())
            openItemEditor(player, pending.project, pending.page, pending.slot);
        else openMain(player);
    }

    private void cancelPendingInput(Player player) {
        PendingInput pending = pendingInputs.remove(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(PREFIX + tr("No tienes ninguna edición pendiente para cancelar.", "There is no pending edit to cancel."));
            return;
        }
        returnFromCancelledInput(player, pending);
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
        if (isCancelInput(input)) {
            returnFromCancelledInput(player, pending);
            return;
        }
        if (pending.type == InputType.PUBLISH_PROJECT_CATEGORY) {
            String category = slug(input.trim());
            if (category.isBlank()) {
                player.sendMessage(PREFIX + tr("Categoría no válida. Prueba pvp, survival, rpg o utility.", "Invalid category. Try pvp, survival, rpg or utility."));
                promptInput(player, InputType.PUBLISH_PROJECT_CATEGORY, pending.project, 0, -1,
                    "Escribe una categoría para el kit:", "Type a category for this kit:");
                return;
            }
            promptInput(player, InputType.PUBLISH_PROJECT_DESCRIPTION, pending.project, 0, -1,
                "Escribe una descripción breve para la comunidad:", "Type a short description for the community:", category);
            return;
        }
        if (pending.type == InputType.PUBLISH_PROJECT_DESCRIPTION) {
            String description = input.trim();
            if (description.isBlank()) description = "Compartido con IxKits por " + player.getName();
            if (description.length() > 500) description = description.substring(0, 500);
            publishProject(player, pending.project, pending.argument == null ? "misc" : pending.argument, description);
            return;
        }
        if (pending.type == InputType.CATALOG_SEARCH) {
            openCatalog(player, input);
            return;
        }
        if (pending.type == InputType.PROJECT_SEARCH) {
            openProjects(player, 0, input);
            return;
        }
        if (pending.type == InputType.PUBLISH_REVISION_ID) {
            publishProjectRevision(player, input.trim(), pending.project, "Updated from IxKits GUI");
            return;
        }
        if (pending.type == InputType.CREATE_PROJECT) {
            String displayName = input.trim();
            String id = slug(displayName);
            if (displayName.isBlank() || id.isBlank()) {
                player.sendMessage(PREFIX + tr("Nombre de proyecto no válido.", "Invalid project name."));
                openProjects(player);
                return;
            }
            File targetFile = projectFile(id);
            if (targetFile.exists()) {
                player.sendMessage(PREFIX + tr("Ya existe un proyecto con ese nombre.", "A project with that name already exists."));
                openProjects(player);
                return;
            }
            YamlConfiguration projectYaml = new YamlConfiguration();
            projectYaml.set("name", displayName);
            projectYaml.set("id", id);
            projectYaml.set("version", 1);
            projectYaml.set("format-version", 2);
            projectYaml.set("created", System.currentTimeMillis());
            saveYaml(projectYaml, targetFile);
            player.sendMessage(PREFIX + tr("Proyecto creado: §b", "Project created: §b") + displayName);
            openEditor(player, id, 0);
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
        String itemPath = storagePath(pending.page, pending.slot);
        ItemStack target = yaml.getItemStack(itemPath);
        if (target == null || target.getType().isAir()) { player.sendMessage(PREFIX + tr("No se encontró el objeto.", "Item not found.")); openEditor(player, pending.project, pending.page); return; }
        if (pending.type == InputType.ENCHANTMENT_LEVEL) {
            NamespacedKey enchantmentKey = pending.argument == null ? null : NamespacedKey.fromString(pending.argument);
            Enchantment enchantment = enchantmentKey == null ? null : Registry.ENCHANTMENT.get(enchantmentKey);
            int level;
            try { level = Integer.parseInt(input.trim()); }
            catch (NumberFormatException ex) {
                player.sendMessage(PREFIX + tr("Nivel inválido. Usa un número del 1 al 32767.", "Invalid level. Use a number from 1 to 32767."));
                promptEnchantLevel(player, pending.project, pending.page, pending.slot, pending.argument == null ? "" : pending.argument);
                return;
            }
            if (enchantment == null || level < 1 || level > 32767) {
                player.sendMessage(PREFIX + tr("No se pudo aplicar el nivel. Usa un encantamiento válido y un nivel de 1 a 32767.", "Could not apply the level. Use a valid enchantment and a level from 1 to 32767."));
                promptEnchantLevel(player, pending.project, pending.page, pending.slot, pending.argument == null ? "" : pending.argument);
                return;
            }
            target.addUnsafeEnchantment(enchantment, level);
            projectService.backupBeforeChange(file);
            yaml.set(itemPath, target);
            yaml.set("format-version", 2);
            yaml.set("updated", System.currentTimeMillis());
            saveYaml(yaml, file);
            player.sendMessage(PREFIX + tr("Encantamiento configurado a nivel §b", "Enchantment set to level §b") + level + "§7.");
            openEnchantments(player, pending.project, pending.page, pending.slot, 0);
            return;
        }
        if (pending.type == InputType.ABILITY_COOLDOWN) {
            double seconds;
            try { seconds = Double.parseDouble(input.trim().replace(',', '.')); }
            catch (NumberFormatException ex) {
                player.sendMessage(PREFIX + tr("Cooldown inválido. Escribe segundos, por ejemplo 2.5.", "Invalid cooldown. Type seconds, for example 2.5."));
                promptAbilityCooldown(player, pending.project, pending.page, pending.slot, pending.argument == null ? "tnt" : pending.argument);
                return;
            }
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 86400) {
                player.sendMessage(PREFIX + tr("El cooldown debe estar entre 0 y 86400 segundos.", "Cooldown must be between 0 and 86400 seconds."));
                promptAbilityCooldown(player, pending.project, pending.page, pending.slot, pending.argument == null ? "tnt" : pending.argument);
                return;
            }
            NamespacedKey cooldownKey = abilityCooldownKey(pending.argument);
            if (cooldownKey == null) {
                player.sendMessage(PREFIX + tr("No se reconoció esa habilidad.", "That ability was not recognized."));
                openAbilities(player, pending.project, pending.page, pending.slot);
                return;
            }
            ItemMeta meta = target.getItemMeta();
            long cooldownMs = Math.round(seconds * 1000.0);
            meta.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, cooldownMs);
            target.setItemMeta(meta);
            projectService.backupBeforeChange(file);
            yaml.set(itemPath, target);
            yaml.set("format-version", 2);
            yaml.set("updated", System.currentTimeMillis());
            saveYaml(yaml, file);
            player.sendMessage(PREFIX + tr("Cooldown guardado: §b", "Cooldown saved: §b") + formatCooldown(cooldownMs));
            openAbilities(player, pending.project, pending.page, pending.slot);
            return;
        }
        if (pending.type == InputType.LORE_ADD_LINE || pending.type == InputType.LORE_EDIT_LINE
            || pending.type == InputType.LORE_CLEAR_CONFIRM) {
            ItemMeta loreMeta = target.getItemMeta();
            List<net.kyori.adventure.text.Component> lines = loreMeta.lore() == null
                ? new ArrayList<>() : new ArrayList<>(loreMeta.lore());
            if (pending.type == InputType.LORE_CLEAR_CONFIRM) {
                if (!input.equalsIgnoreCase("BORRAR") && !input.equalsIgnoreCase("CLEAR")) {
                    player.sendMessage(PREFIX + tr("Lore conservado.", "Lore kept."));
                    openLoreEditor(player, pending.project, pending.page, pending.slot, 0);
                    return;
                }
                lines.clear();
            } else {
                net.kyori.adventure.text.Component line = MiniMessage.miniMessage().deserialize(input);
                if (pending.type == InputType.LORE_ADD_LINE) lines.add(line);
                else {
                    if (pending.lineIndex < 0 || pending.lineIndex >= lines.size()) {
                        player.sendMessage(PREFIX + tr("Esa línea ya no existe.", "That line no longer exists."));
                        openLoreEditor(player, pending.project, pending.page, pending.slot, 0);
                        return;
                    }
                    lines.set(pending.lineIndex, line);
                }
            }
            loreMeta.lore(lines.isEmpty() ? null : lines);
            target.setItemMeta(loreMeta);
            projectService.backupBeforeChange(file);
            yaml.set(itemPath, target);
            yaml.set("format-version", 2);
            yaml.set("updated", System.currentTimeMillis());
            saveYaml(yaml, file);
            player.sendMessage(PREFIX + tr("Lore actualizado.", "Lore updated."));
            openLoreEditor(player, pending.project, pending.page, pending.slot, 0);
            return;
        }
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
                case BOOK_TITLE -> {
                    if (!(meta instanceof BookMeta book)) throw new IllegalArgumentException("This item is not a written book.");
                    book.title(input.equals("-") ? null : MiniMessage.miniMessage().deserialize(input));
                }
                case BOOK_AUTHOR -> {
                    if (!(meta instanceof BookMeta book)) throw new IllegalArgumentException("This item is not a written book.");
                    book.author(input.equals("-") ? null : MiniMessage.miniMessage().deserialize(input));
                }
                case BOOK_PAGES -> {
                    if (!(meta instanceof BookMeta book)) throw new IllegalArgumentException("This item is not a written book.");
                    if (input.equals("-")) book.pages(List.of());
                    else book.pages(Arrays.stream(input.split("\\|", -1)).limit(100).map(MiniMessage.miniMessage()::deserialize).toList());
                }
                case SKULL_OWNER -> {
                    if (!(meta instanceof SkullMeta skull)) throw new IllegalArgumentException("This item is not a player head.");
                    skull.setOwner(input.equals("-") ? null : input.trim());
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
                ? tr("§eClic izquierdo: aplicar al objeto en mano", "§eLeft-click: apply to held item")
                : tr("§eClic izquierdo: aplicar al objeto del kit", "§eLeft-click: apply to kit item"));
            preview.add(tr("§dClic derecho: publicar en Community", "§dRight-click: publish to Community"));
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

    private void openEnchantTemplates(Player p, int requestedPage, String project, int projectPage, int targetSlot) {
        YamlConfiguration presets = YamlConfiguration.loadConfiguration(new File(templatesDir, "enchantments.yml"));
        List<String> keys = new ArrayList<>();
        for (String key : presets.getKeys(false)) if (presets.isConfigurationSection(key)) keys.add(key);
        Collections.sort(keys);
        int pages = Math.max(1, (int) Math.ceil(keys.size() / (double) EDIT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = project == null
            ? menu("enchantment-templates", null, page, tr("§3✦ §bIxKits §8• §0Presets de encantamientos", "§3✦ §bIxKits §8• §0Enchantment presets"))
            : menu("enchantment-templates", project, projectPage, targetSlot, page,
                tr("§3✦ §bIxKits §8• §0Presets de encantamientos", "§3✦ §bIxKits §8• §0Enchantment presets"));
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        for (int i = page * EDIT_SLOTS.length; i < Math.min(keys.size(), (page + 1) * EDIT_SLOTS.length); i++) {
            String key = keys.get(i);
            ConfigurationSection section = presets.getConfigurationSection(key);
            List<String> lore = new ArrayList<>();
            lore.add(tr("§7Incluye:", "§7Includes:"));
            if (section != null) for (String enchant : section.getKeys(false)) {
                lore.add("§b• §f" + prettyKey(enchant) + " " + section.getInt(enchant, 1));
                if (lore.size() >= 7) { lore.add("§8…"); break; }
            }
            lore.add("");
            lore.add(project == null ? tr("§eClic para aplicar al objeto en mano", "§eClick to apply to held item")
                : tr("§eClic para aplicar al objeto del kit", "§eClick to apply to kit item"));
            int slot = EDIT_SLOTS[i - page * EDIT_SLOTS.length];
            inv.setItem(slot, itemWithLore(Material.ENCHANTED_BOOK, "§d" + prettyKey(key), lore));
            holder.entryBySlot.put(slot, key);
        }
        if (keys.isEmpty()) inv.setItem(22, item(Material.PAPER, tr("§7No hay presets guardados.", "§7No saved presets.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext")));
        p.openInventory(inv);
    }

    private void applyEnchantPreset(Player p, String presetName, String project, int projectPage, int targetSlot) {
        YamlConfiguration presets = YamlConfiguration.loadConfiguration(new File(templatesDir, "enchantments.yml"));
        ConfigurationSection section = presets.getConfigurationSection(presetName);
        if (section == null) {
            p.sendMessage(PREFIX + tr("Ese preset de encantamientos no existe.", "That enchantment preset does not exist."));
            if (project != null) openEnchantTemplates(p, 0, project, projectPage, targetSlot);
            return;
        }
        ItemStack target = project == null ? p.getInventory().getItemInMainHand()
            : getProjectItem(project, projectPage, targetSlot);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(PREFIX + tr("Selecciona primero un objeto.", "Select an item first."));
            return;
        }
        for (String rawKey : section.getKeys(false)) {
            NamespacedKey key = rawKey.contains(":") ? NamespacedKey.fromString(rawKey) : NamespacedKey.minecraft(rawKey.toLowerCase(Locale.ROOT));
            Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
            if (enchantment != null) target.addUnsafeEnchantment(enchantment, Math.max(1, Math.min(32767, section.getInt(rawKey, 1))));
        }
        if (project != null) saveProjectItem(project, projectPage, targetSlot, target);
        p.sendMessage(PREFIX + tr("Preset aplicado: §d", "Preset applied: §d") + prettyKey(presetName));
        if (project != null) openItemEditor(p, project, projectPage, targetSlot);
        else openEnchantTemplates(p, 0, null, 0, -1);
    }

    private void openPaletteTemplates(Player p, int requestedPage, String project, int projectPage, int targetSlot) {
        YamlConfiguration palettes = YamlConfiguration.loadConfiguration(new File(templatesDir, "palettes.yml"));
        List<String> keys = new ArrayList<>();
        for (String key : palettes.getKeys(false)) if (palettes.getString(key + ".color") != null) keys.add(key);
        Collections.sort(keys);
        int pages = Math.max(1, (int) Math.ceil(keys.size() / (double) EDIT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = project == null
            ? menu("palette-templates", null, page, tr("§3✦ §bIxKits §8• §0Paletas de color", "§3✦ §bIxKits §8• §0Color palettes"))
            : menu("palette-templates", project, projectPage, targetSlot, page,
                tr("§3✦ §bIxKits §8• §0Paletas de color", "§3✦ §bIxKits §8• §0Color palettes"));
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        for (int i = page * EDIT_SLOTS.length; i < Math.min(keys.size(), (page + 1) * EDIT_SLOTS.length); i++) {
            String key = keys.get(i);
            String color = palettes.getString(key + ".color", "&b");
            String accent = palettes.getString(key + ".accent", color);
            Material icon = paletteIcon(key, color);
            int slot = EDIT_SLOTS[i - page * EDIT_SLOTS.length];
            inv.setItem(slot, item(icon, "§f" + palettes.getString(key + ".display", prettyKey(key)),
                tr("§7Color principal: ", "§7Primary color: ") + color,
                tr("§7Acento: ", "§7Accent: ") + accent, "",
                project == null ? tr("§eClic para aplicar al objeto en mano", "§eClick to apply to held item")
                    : tr("§eClic para aplicar al objeto del kit", "§eClick to apply to kit item")));
            holder.entryBySlot.put(slot, key);
        }
        if (keys.isEmpty()) inv.setItem(22, item(Material.PAPER, tr("§7No hay paletas guardadas.", "§7No saved palettes.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver", "§7Back")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext")));
        p.openInventory(inv);
    }

    private Material paletteIcon(String name, String color) {
        String key = (name + " " + color).toLowerCase(Locale.ROOT);
        if (key.contains("gold") || key.contains("sun") || key.contains("yellow") || key.contains("&6")) return Material.YELLOW_STAINED_GLASS_PANE;
        if (key.contains("inferno") || key.contains("rose") || key.contains("red") || key.contains("&c")) return Material.RED_STAINED_GLASS_PANE;
        if (key.contains("emerald") || key.contains("nature") || key.contains("green") || key.contains("&a")) return Material.LIME_STAINED_GLASS_PANE;
        if (key.contains("royal") || key.contains("void") || key.contains("amethyst") || key.contains("&5")) return Material.PURPLE_STAINED_GLASS_PANE;
        if (key.contains("frost") || key.contains("white") || key.contains("&f")) return Material.WHITE_STAINED_GLASS_PANE;
        if (key.contains("shadow") || key.contains("black") || key.contains("&0")) return Material.BLACK_STAINED_GLASS_PANE;
        if (key.contains("ocean") || key.contains("blue") || key.contains("&9")) return Material.BLUE_STAINED_GLASS_PANE;
        return Material.CYAN_STAINED_GLASS_PANE;
    }

    private String paletteLegacyColor(String color) {
        String value = color == null ? "&b" : color.trim();
        if (value.startsWith("&#") && value.length() == 8) value = value.substring(1);
        if (value.matches("#[0-9a-fA-F]{6}")) {
            StringBuilder out = new StringBuilder("§x");
            for (char ch : value.substring(1).toCharArray()) out.append('§').append(ch);
            return out.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private void applyPaletteToItem(ItemStack target, YamlConfiguration palettes, String key) {
        String color = palettes.getString(key + ".color");
        if (color == null || target == null || target.getType().isAir()) return;
        String legacyColor = paletteLegacyColor(color);
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;
        String name = meta.hasDisplayName() ? meta.getDisplayName() : prettyKey(target.getType().name());
        meta.setDisplayName(legacyColor + ChatColor.stripColor(name));
        if (meta.lore() != null) {
            var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            List<String> lore = meta.lore().stream()
                .map(line -> legacyColor + ChatColor.stripColor(legacy.serialize(line)))
                .toList();
            meta.setLore(lore);
        }
        target.setItemMeta(meta);
    }

    private void applyPalettePreset(Player p, String key, String project, int projectPage, int targetSlot) {
        YamlConfiguration palettes = YamlConfiguration.loadConfiguration(new File(templatesDir, "palettes.yml"));
        if (!palettes.contains(key + ".color")) {
            p.sendMessage(PREFIX + tr("Esa paleta no existe.", "That palette does not exist."));
            return;
        }
        ItemStack target = project == null ? p.getInventory().getItemInMainHand()
            : getProjectItem(project, projectPage, targetSlot);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(PREFIX + tr("Selecciona primero un objeto.", "Select an item first."));
            return;
        }
        applyPaletteToItem(target, palettes, key);
        if (project != null) saveProjectItem(project, projectPage, targetSlot, target);
        p.sendMessage(PREFIX + tr("Paleta aplicada: §b", "Palette applied: §b") + palettes.getString(key + ".display", prettyKey(key)));
        if (project != null) openItemEditor(p, project, projectPage, targetSlot);
        else openPaletteTemplates(p, 0, null, 0, -1);
    }

    private void openItemEditor(Player p, String project, int page, int targetSlot) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(project));
        ItemStack target = y.getItemStack(storagePath(page, targetSlot));
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
        inv.setItem(15, item(Material.PAPER, tr("§bCopiar objeto", "§bCopy item"),
            tr("§7Guardar una copia exacta en IxKits.", "§7Keep an exact copy in IxKits."),
            tr("§7Después haz clic en una ranura vacía.", "§7Then click an empty project slot.")));
        inv.setItem(16, item(Material.CHEST, tr("§bPortapapeles", "§bClipboard"),
            itemClipboard.containsKey(p.getUniqueId()) ? tr("§aHay un objeto copiado", "§aAn item is copied") : tr("§8Está vacío", "§8It is empty"),
            tr("§7Clic para ver el estado", "§7Click to check status")));
        inv.setItem(14, item(Material.NETHER_STAR, tr("§dPropiedades especiales", "§dSpecial properties"),
            tr("§7Libros, estandartes, armaduras,", "§7Books, banners, armor trims,"),
            tr("§7cabezas y fuegos artificiales.", "§7heads and fireworks."),
            tr("§eClic para abrir", "§eClick to open")));
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
        inv.setItem(13, item(Material.WRITABLE_BOOK, tr("§bEditor visual de lore", "§bVisual lore editor"),
            tr("§7Añadir, editar, borrar y ordenar líneas.", "§7Add, edit, delete and reorder lines."),
            tr("§7Cada línea se edita por separado.", "§7Edit each line separately."),
            tr("§eClic para abrir", "§eClick to open")));
        inv.setItem(24, item(Material.BARRIER, tr("§cQuitar todos los encantamientos", "§cRemove all enchantments"), tr("§7Solo afecta a este objeto del proyecto.", "§7Only affects this project item."), tr("§eClic para confirmar al instante", "§eClick to apply immediately")));
        inv.setItem(27, item(Material.ENCHANTED_BOOK, tr("§dPlantillas de encantamientos", "§dEnchantment presets"), tr("§7Aplicar una combinación guardada.", "§7Apply a saved combination."), tr("§eClic para elegir", "§eClick to choose")));
        inv.setItem(32, item(Material.PAINTING, tr("§bPaleta de colores", "§bColor palette"), tr("§7Cambiar el estilo del nombre y el lore.", "§7Restyle the name and lore."), tr("§eClic para elegir", "§eClick to choose")));
        inv.setItem(30, item(Material.TNT, tr("§cHabilidades especiales", "§cCustom abilities"),
            tr("§7Efectos activados al usar el objeto.", "§7Effects triggered when using the item."),
            tr("§eClic para configurar", "§eClick to configure")));
        inv.setItem(29, item(Material.IRON_BLOCK, tr("§bIrrompible", "§bUnbreakable"), tr("§7Alternar la propiedad del objeto.", "§7Toggle the item's property."), tr("§eClic para cambiar", "§eClick to toggle")));
        inv.setItem(33, item(Material.ANVIL, tr("§bDurabilidad", "§bDurability"), tr("§7Clic: +10 de daño", "§7Click: +10 damage"), tr("§7Derecho: -10; Mayús: reparar", "§7Right-click: -10; Shift: repair")));
        inv.setItem(35, item(Material.CARVED_PUMPKIN, tr("§bModelo personalizado", "§bCustom model data"), tr("§7Define o borra el número de modelo.", "§7Set or clear the model number."), tr("§eClic para escribir", "§eClick to type")));
        inv.setItem(37, item(Material.BRUSH, tr("§bAtributos", "§bAttributes"), tr("§7Salud, daño, armadura y más.", "§7Health, damage, armor and more."), tr("§eClic para abrir", "§eClick to open")));
        inv.setItem(39, item(Material.GLASS, tr("§bFlags del objeto", "§bItem flags"), tr("§7Alternar ocultación de atributos.", "§7Toggle hidden attributes."), tr("§eClic para cambiar", "§eClick to toggle")));
        inv.setItem(41, item(Material.LEATHER_CHESTPLATE, tr("§bColor de cuero", "§bLeather color"), tr("§7Para objetos de cuero.", "§7For leather items."), tr("§eEscribe #RRGGBB o R,G,B", "§eType #RRGGBB or R,G,B")));
        inv.setItem(43, item(Material.POTION, tr("§bTipo de poción", "§bPotion type"), tr("§7Para pociones y flechas con efecto.", "§7For potions and tipped arrows."), tr("§eClic para editar", "§eClick to edit")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al proyecto", "§7Back to project")));
        p.openInventory(inv);
    }

    private void promptLoreInput(Player p, InputType type, String project, int page, int targetSlot, int lineIndex, String spanish, String english) {
        pendingInputs.put(p.getUniqueId(), new PendingInput(type, project, page, targetSlot, lineIndex));
        p.closeInventory();
        p.sendMessage(PREFIX + tr(spanish, english));
        sendCancelPrompt(p);
    }

    private void openLoreEditor(Player p, String project, int page, int targetSlot, int requestedPage) {
        ItemStack target = getProjectItem(project, page, targetSlot);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(PREFIX + tr("No se encontró el objeto para editar el lore.", "Could not find the item to edit lore."));
            if (targetSlot < 0) openEquipment(p, project, page); else openEditor(p, project, page);
            return;
        }
        List<net.kyori.adventure.text.Component> lines = target.getItemMeta().lore();
        if (lines == null) lines = new ArrayList<>();
        int pages = Math.max(1, (int) Math.ceil(lines.size() / (double) EDIT_SLOTS.length));
        int currentPage = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = menu("lore-editor", project, page, targetSlot, currentPage,
            tr("§3✦ §bIxKits §8• §0Editor de lore", "§3✦ §bIxKits §8• §0Lore editor")
                + " §7[" + (currentPage + 1) + "/" + pages + "]");
        border(inv);
        MenuHolder holder = (MenuHolder) inv.getHolder();
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        int start = currentPage * EDIT_SLOTS.length;
        for (int index = start; index < Math.min(lines.size(), start + EDIT_SLOTS.length); index++) {
            int slot = EDIT_SLOTS[index - start];
            String preview = legacy.serialize(lines.get(index));
            if (preview.length() > 80) preview = preview.substring(0, 77) + "...";
            ItemStack lineItem = new ItemStack(Material.PAPER);
            ItemMeta lineMeta = lineItem.getItemMeta();
            lineMeta.displayName(net.kyori.adventure.text.Component.text("§bLínea " + (index + 1)));
            List<net.kyori.adventure.text.Component> previewLore = new ArrayList<>();
            previewLore.add(net.kyori.adventure.text.Component.text("§7Vista previa: ").append(lines.get(index)));
            previewLore.add(net.kyori.adventure.text.Component.empty());
            previewLore.add(net.kyori.adventure.text.Component.text(tr("§eClic: editar esta línea", "§eClick: edit this line")));
            previewLore.add(net.kyori.adventure.text.Component.text(tr("§cClic derecho: borrar", "§cRight-click: delete")));
            previewLore.add(net.kyori.adventure.text.Component.text(tr("§bMayús + clic: mover arriba", "§bShift-click: move up")));
            previewLore.add(net.kyori.adventure.text.Component.text(tr("§bMayús + clic derecho: mover abajo", "§bShift + right-click: move down")));
            previewLore.add(net.kyori.adventure.text.Component.text(tr("§dClic central: duplicar", "§dMiddle-click: duplicate")));
            lineMeta.lore(previewLore);
            lineItem.setItemMeta(lineMeta);
            inv.setItem(slot, lineItem);
            holder.entryBySlot.put(slot, Integer.toString(index));
        }
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al objeto", "§7Back to item")));
        inv.setItem(47, item(Material.SPECTRAL_ARROW, tr("§bAnterior", "§bPrevious"), tr("§7Página anterior", "§7Previous page")));
        inv.setItem(48, item(Material.LIME_DYE, tr("§aAñadir línea", "§aAdd line"),
            tr("§7Escribe el contenido de la nueva línea.", "§7Type the new line's content.")));
        inv.setItem(49, item(Material.BARRIER, tr("§cBorrar todo el lore", "§cClear all lore"),
            tr("§7Pide confirmación por chat.", "§7Requires confirmation in chat.")));
        inv.setItem(51, item(Material.SPECTRAL_ARROW, tr("§bSiguiente", "§bNext"), tr("§7Página siguiente", "§7Next page")));
        inv.setItem(53, item(Material.PAPER, tr("§fLíneas: ", "§fLines: ") + lines.size(),
            tr("§7Cada línea es independiente.", "§7Each line is independent.")));
        p.openInventory(inv);
    }

    private void saveLoreLines(Player p, String project, int page, int targetSlot, List<net.kyori.adventure.text.Component> lines, int viewPage) {
        ItemStack target = getProjectItem(project, page, targetSlot);
        if (target == null || target.getType().isAir()) return;
        ItemMeta meta = target.getItemMeta();
        meta.lore(lines.isEmpty() ? null : new ArrayList<>(lines));
        target.setItemMeta(meta);
        saveProjectItem(project, page, targetSlot, target);
        openLoreEditor(p, project, page, targetSlot, viewPage);
    }

    private NamespacedKey abilityCooldownKey(String ability) {
        if (ability == null) return null;
        return switch (ability.toLowerCase(Locale.ROOT)) {
            case "tnt" -> tntCooldownKey;
            case "lightning" -> lightningCooldownKey;
            case "lifesteal" -> lifeStealCooldownKey;
            default -> null;
        };
    }

    private long getAbilityCooldown(ItemStack stack, String ability) {
        NamespacedKey key = abilityCooldownKey(ability);
        String path;
        long fallback;
        switch (ability) {
            case "tnt" -> { path = "abilities.tnt-on-hit-cooldown-ms"; fallback = 3000L; }
            case "lightning" -> { path = "abilities.lightning-on-hit-cooldown-ms"; fallback = 5000L; }
            case "lifesteal" -> { path = "abilities.lifesteal-on-hit-cooldown-ms"; fallback = 1000L; }
            default -> { return 0L; }
        }
        if (stack != null && !stack.getType().isAir() && stack.hasItemMeta() && key != null) {
            Long stored = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.LONG);
            if (stored != null) return Math.max(0L, stored);
        }
        return Math.max(0L, getConfig().getLong(path, fallback));
    }

    private String formatCooldown(long milliseconds) {
        if (milliseconds == 0) return tr("Sin espera", "No cooldown");
        if (milliseconds < 1000) return milliseconds + " ms";
        String value = String.format(Locale.US, "%.2f", milliseconds / 1000.0).replaceAll("0+$", "").replaceAll("\\.$", "");
        return value + " s";
    }

    private void promptAbilityCooldown(Player p, String project, int page, int targetSlot, String ability) {
        String current = formatCooldown(getAbilityCooldown(getProjectItem(project, page, targetSlot), ability));
        promptInput(p, InputType.ABILITY_COOLDOWN, project, page, targetSlot,
            "Escribe el cooldown en segundos para esta habilidad (0 = sin espera, máximo 86400). Actual: " + current,
            "Type the cooldown in seconds for this ability (0 = no wait, maximum 86400). Current: " + current,
            ability);
    }

    private void promptEnchantLevel(Player p, String project, int page, int targetSlot, String enchantmentKey) {
        promptInput(p, InputType.ENCHANTMENT_LEVEL, project, page, targetSlot,
            "Escribe el nivel exacto del encantamiento (1-32767):",
            "Type the exact enchantment level (1-32767):",
            enchantmentKey);
    }

    private void openAbilities(Player p, String project, int page, int targetSlot) {
        ItemStack target = getProjectItem(project, page, targetSlot);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(PREFIX + tr("No se encontró el objeto.", "Item not found."));
            if (targetSlot < 0) openEquipment(p, project, page); else openEditor(p, project, page);
            return;
        }
        Inventory inv = menu("abilities", project, page, targetSlot, 0,
            tr("§3✦ §bIxKits §8• §0Habilidades", "§3✦ §bIxKits §8• §0Abilities"));
        border(inv);
        boolean tntEnabled = isTntOnHitEnabled(target);
        inv.setItem(20, item(tntEnabled ? Material.TNT : Material.GUNPOWDER,
            tntEnabled ? tr("§aTNT al golpear: ACTIVADA", "§aTNT on hit: ENABLED") : tr("§cTNT al golpear: DESACTIVADA", "§cTNT on hit: DISABLED"),
            tr("§7Hace aparecer una TNT junto al enemigo.", "§7Spawns TNT beside the target."),
            tr("§7Mecha: ", "§7Fuse: ") + getConfig().getInt("abilities.tnt-on-hit-fuse-ticks", 40) + " ticks",
            tr("§7Enfriamiento: ", "§7Cooldown: ") + formatCooldown(getAbilityCooldown(target, "tnt")),
            tr("§eClic izquierdo: activar/desactivar", "§eLeft-click: toggle"),
            tr("§bClic derecho: configurar cooldown", "§bRight-click: configure cooldown")));
        inv.setItem(22, item(isAbilityEnabled(target, lightningOnHitKey) ? Material.LIGHTNING_ROD : Material.COPPER_INGOT,
            isAbilityEnabled(target, lightningOnHitKey) ? tr("§aRayo al golpear: ACTIVADO", "§aLightning on hit: ENABLED") : tr("§cRayo al golpear: DESACTIVADO", "§cLightning on hit: DISABLED"),
            tr("§7Invoca un rayo sobre el objetivo.", "§7Calls lightning down on the target."),
            tr("§7Puede causar daño adicional y fuego.", "§7Can cause extra damage and fire."),
            tr("§7Enfriamiento: ", "§7Cooldown: ") + formatCooldown(getAbilityCooldown(target, "lightning")),
            tr("§eClic izquierdo: activar/desactivar", "§eLeft-click: toggle"),
            tr("§bClic derecho: configurar cooldown", "§bRight-click: configure cooldown")));
        inv.setItem(24, item(isAbilityEnabled(target, lifeStealOnHitKey) ? Material.GHAST_TEAR : Material.ROTTEN_FLESH,
            isAbilityEnabled(target, lifeStealOnHitKey) ? tr("§aRobo de vida: ACTIVADO", "§aLifesteal: ENABLED") : tr("§cRobo de vida: DESACTIVADO", "§cLifesteal: DISABLED"),
            tr("§7Recupera parte del daño al golpear.", "§7Heals you for a portion of damage dealt."),
            tr("§7Porcentaje: ", "§7Percent: ") + (int)Math.round(getConfig().getDouble("abilities.lifesteal-on-hit-percent", 0.25) * 100) + "%",
            tr("§7Enfriamiento: ", "§7Cooldown: ") + formatCooldown(getAbilityCooldown(target, "lifesteal")),
            tr("§eClic izquierdo: activar/desactivar", "§eLeft-click: toggle"),
            tr("§bClic derecho: configurar cooldown", "§bRight-click: configure cooldown")));
        inv.setItem(31, item(Material.BOOK, tr("§fCómo funciona", "§fHow it works"),
            tr("§7Se aplica al arma cuando está en tu", "§7Applies when the item is in your"),
            tr("§7mano principal y golpeas a un ser vivo.", "§7main hand and you hit a living entity."),
            tr("§7Cada habilidad tiene su enfriamiento.", "§7Each ability has its own cooldown.")));
        inv.setItem(45, item(Material.ARROW, tr("§7Volver al objeto", "§7Back to item")));
        p.openInventory(inv);
    }

    private void openEnchantments(Player p, String project, int projectPage, int targetSlot, int enchantPage) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(project));
        ItemStack target = y.getItemStack(storagePath(projectPage, targetSlot));
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
            lines.add(tr("§dMayús + clic izquierdo: nivel exacto", "§dShift + left-click: exact level"));
            lines.add(tr("§cMayús + clic derecho: eliminar", "§cShift + right-click: remove"));
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
            Inventory top = e.getView().getTopInventory();
            if (slot >= top.getSize()) {
                if (e.isShiftClick()) {
                    ItemStack moving = e.getCurrentItem();
                    if (moving != null && !moving.getType().isAir()) {
                        int destination = -1;
                        for (int editable : EDIT_SLOTS) {
                            ItemStack present = top.getItem(editable);
                            if (present == null || present.getType().isAir()) { destination = editable; break; }
                        }
                        e.setCancelled(true);
                        if (destination < 0) p.sendMessage(PREFIX + tr("No hay ranuras vacías en esta página.", "There are no empty slots on this page."));
                        else {
                            top.setItem(destination, moving.clone());
                            e.setCurrentItem(new ItemStack(Material.AIR));
                            savePage(top, h);
                            openItemEditor(p, h.project, h.page, destination);
                        }
                    }
                }
                return;
            }
            if (slot >= 0 && contains(EDIT_SLOTS, slot)) {
                ItemStack clicked = e.getCurrentItem();
                ItemStack cursor = e.getCursor();
                boolean cursorEmpty = cursor == null || cursor.getType().isAir();
                if (e.isRightClick() && clicked != null && !clicked.getType().isAir() && cursorEmpty) {
                    e.setCancelled(true);
                    savePage(top, h);
                    openItemEditor(p, h.project, h.page, slot);
                } else if ((clicked == null || clicked.getType().isAir()) && !cursorEmpty) {
                    e.setCancelled(true);
                    ItemStack placed = cursor.clone();
                    if (e.isRightClick()) {
                        placed.setAmount(1);
                        if (cursor.getAmount() <= 1) e.getView().setCursor(new ItemStack(Material.AIR));
                        else { cursor.setAmount(cursor.getAmount() - 1); e.getView().setCursor(cursor); }
                    } else e.getView().setCursor(new ItemStack(Material.AIR));
                    top.setItem(slot, placed);
                    savePage(top, h);
                    openItemEditor(p, h.project, h.page, slot);
                } else if ((clicked == null || clicked.getType().isAir()) && cursorEmpty && !e.isRightClick()
                    && itemClipboard.containsKey(p.getUniqueId())) {
                    e.setCancelled(true);
                    ItemStack copy = itemClipboard.get(p.getUniqueId());
                    if (copy != null) {
                        top.setItem(slot, copy.clone());
                        savePage(top, h);
                        openItemEditor(p, h.project, h.page, slot);
                        p.sendMessage(PREFIX + tr("Objeto pegado desde el portapapeles de IxKits.", "Item pasted from the IxKits clipboard."));
                    }
                }
                return;
            }
            e.setCancelled(true);
            if (slot == 45) { savePage(e.getView().getTopInventory(), h); openProjects(p); }
            else if (slot == 47) { savePage(e.getView().getTopInventory(), h); openEditor(p, h.project, Math.max(0, h.page-1)); }
            else if (slot == 51) { savePage(e.getView().getTopInventory(), h); openEditor(p, h.project, Math.min(1, h.page+1)); }
            else if (slot == 49) { savePage(e.getView().getTopInventory(), h); p.closeInventory(); p.sendMessage(PREFIX + tr("Proyecto guardado.", "Project saved.")); }
            else if (slot == 44) { savePage(e.getView().getTopInventory(), h); openEquipment(p, h.project, h.page); }
            else if (slot == 53) { savePage(e.getView().getTopInventory(), h); exportProject(p, h.project); }
            return;
        }

        if (h.kind.equals("equipment")) {
            Inventory top = e.getView().getTopInventory();
            if (slot >= top.getSize()) {
                if (e.isShiftClick()) {
                    ItemStack moving = e.getCurrentItem();
                    if (moving != null && !moving.getType().isAir()) {
                        int preferred = preferredEquipmentSlot(moving.getType());
                        int destination = preferred >= 0 && isEmptyEquipmentSlot(top.getItem(preferred)) ? preferred : -1;
                        if (destination < 0 && preferred < 0 && isEmptyEquipmentSlot(top.getItem(31))) destination = 31;
                        e.setCancelled(true);
                        if (destination < 0) p.sendMessage(PREFIX + tr("No hay una ranura de equipamiento compatible y vacía.", "No compatible empty equipment slot is available."));
                        else {
                            top.setItem(destination, moving.clone());
                            e.setCurrentItem(new ItemStack(Material.AIR));
                            saveEquipment(top, h.project);
                            openItemEditor(p, h.project, h.page, equipmentTargetSlot(destination));
                        }
                    }
                }
                return;
            }
            if (slot == 45) {
                e.setCancelled(true);
                saveEquipment(top, h.project);
                openEditor(p, h.project, h.page);
                return;
            }
            if (slot == 40) {
                e.setCancelled(true);
                p.sendMessage(PREFIX + tr("Clic izquierdo para mover objetos; Mayús + clic desde tu inventario los coloca automáticamente; clic derecho sobre una pieza para editarla.", "Left-click to move items; Shift-click from your inventory equips automatically; right-click a piece to edit it."));
                return;
            }
            if (!contains(EQUIP_SLOTS, slot)) { e.setCancelled(true); return; }
            ItemStack current = e.getCurrentItem();
            ItemStack cursor = e.getCursor();
            boolean cursorEmpty = cursor == null || cursor.getType().isAir();
            if (isEquipmentPlaceholder(current)) {
                e.setCancelled(true);
                if (!cursorEmpty) {
                    ItemStack placed = cursor.clone();
                    if (e.isRightClick()) {
                        placed.setAmount(1);
                        if (cursor.getAmount() <= 1) e.getView().setCursor(new ItemStack(Material.AIR));
                        else { cursor.setAmount(cursor.getAmount() - 1); e.getView().setCursor(cursor); }
                    } else e.getView().setCursor(new ItemStack(Material.AIR));
                    top.setItem(slot, placed);
                    saveEquipment(top, h.project);
                    openItemEditor(p, h.project, h.page, equipmentTargetSlot(slot));
                }
            } else if (cursorEmpty && current != null && !current.getType().isAir() && e.isRightClick()) {
                e.setCancelled(true);
                saveEquipment(top, h.project);
                openItemEditor(p, h.project, h.page, equipmentTargetSlot(slot));
            }
            return;
        }
        e.setCancelled(true);
        if (h.kind.equals("main")) {
            if (slot == 20) openProjects(p);
            else if (slot == 22) promptInput(p, InputType.CREATE_PROJECT, null, 0, -1,
                "Escribe el nombre del nuevo proyecto:", "Type a name for the new project:");
            else if (slot == 24) openTemplates(p);
            else if (slot == 29) openProjects(p);
            else if (slot == 31) openCommunityHub(p);
            else if (slot == 33) openLanguages(p);
            else if (slot == 38) {
                p.sendMessage("§bIxKits §7— §f/ixkits §8| §7Abrir estudio");
                p.sendMessage("§f/ixkits crear <nombre> §8| §7Crear proyecto");
                p.sendMessage("§f/ixkits proyectos §8| §7Biblioteca y búsqueda");
                p.sendMessage("§f/ixkits editar <proyecto> §8| §7Abrir editor");
                p.sendMessage("§f/ixkits equipamiento <proyecto> §8| §7Editar armadura");
                p.sendMessage("§f/ixkits exportar <proyecto> §8| §7Exportar");
                p.sendMessage("§f/ixkits idioma §8| §7Cambiar idioma");
            } else if (slot == 40) openLanguages(p);
        } else if (h.kind.equals("language")) {
            if (slot == 45) openMain(p);
            else if (slot == 21) setLanguage(p, "en");
            else if (slot == 23) setLanguage(p, "es");
        } else if (h.kind.equals("projects")) {
            if (slot == 45) openMain(p);
            else if (slot == 47) openProjects(p, Math.max(0, h.page - 1), h.query);
            else if (slot == 48) promptInput(p, InputType.PROJECT_SEARCH, null, h.page, -1, "Escribe el nombre que quieras buscar:", "Type the project name to search:");
            else if (slot == 49) promptInput(p, InputType.CREATE_PROJECT, null, 0, -1, "Escribe el nombre del nuevo proyecto:", "Type a name for the new project:");
            else if (slot == 50) openTrash(p);
            else if (slot == 51) openProjects(p, h.page + 1, h.query);
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
            } else if (slot == 42) {
                try {
                    if (projectService.redo(h.project)) p.sendMessage(PREFIX + tr("Último cambio rehecho.", "Last change redone."));
                    else p.sendMessage(PREFIX + tr("No hay una acción para rehacer.", "There is no action to redo."));
                } catch (IOException ex) { p.sendMessage(PREFIX + "§c" + ex.getMessage()); }
                openProjectActions(p, h.project);
            } else if (slot == 34) promptInput(p, InputType.PUBLISH_PROJECT_CATEGORY, h.project, 0, -1,
                "Escribe la categoría del kit (pvp, survival, rpg, utility, etc.):",
                "Type the kit category (pvp, survival, rpg, utility, etc.):");
            else if (slot == 38) promptInput(p, InputType.PUBLISH_REVISION_ID, h.project, 0, -1, "Escribe el ID de la plantilla publicada:", "Type the ID of the published template:");
            else if (slot == 49) { exportProject(p, h.project); openProjectActions(p, h.project); }
        } else if (h.kind.equals("trash")) {
            if (slot == 45) openProjects(p);
            else if (slot == 49) openTrash(p);
            else if (h.entryBySlot.containsKey(slot)) {
                String id = h.entryBySlot.get(slot);
                try {
                    String displayName = projectService.trashedProjectName(id);
                    if (projectService.restoreFromTrash(id)) p.sendMessage(PREFIX + tr("Proyecto restaurado: §b", "Project restored: §b") + displayName);
                    else p.sendMessage(PREFIX + tr("No se pudo restaurar porque el ID ya está ocupado o el proyecto no existe.", "Restore failed because the ID is occupied or the project is missing."));
                } catch (IOException ex) { p.sendMessage(PREFIX + "§c" + ex.getMessage()); }
                openTrash(p);
            }
        } else if (h.kind.equals("special-properties")) {
            if (slot == 45) openItemEditor(p, h.project, h.page, h.targetSlot);
            else if (h.entryBySlot.containsKey(slot)) {
                switch (h.entryBySlot.get(slot)) {
                    case "book" -> openBookEditor(p, h.project, h.page, h.targetSlot);
                    case "banner" -> openBannerEditor(p, h.project, h.page, h.targetSlot, 0, "WHITE");
                    case "trim" -> openTrimEditor(p, h.project, h.page, h.targetSlot, 0, null);
                    case "skull" -> promptInput(p, InputType.SKULL_OWNER, h.project, h.page, h.targetSlot,
                        "Escribe el nombre del jugador propietario o - para borrar:", "Type the owning player's name or - to clear:");
                    case "firework" -> openFireworkEditor(p, h.project, h.page, h.targetSlot, null);
                    default -> { }
                }
            }
        } else if (h.kind.equals("book-editor")) {
            if (slot == 45) openSpecialProperties(p, h.project, h.page, h.targetSlot);
            else if (slot == 20) promptInput(p, InputType.BOOK_TITLE, h.project, h.page, h.targetSlot, "Escribe el título del libro o - para borrar:", "Type the book title or - to clear:");
            else if (slot == 22) promptInput(p, InputType.BOOK_AUTHOR, h.project, h.page, h.targetSlot, "Escribe el autor del libro o - para borrar:", "Type the book author or - to clear:");
            else if (slot == 24) promptInput(p, InputType.BOOK_PAGES, h.project, h.page, h.targetSlot, "Escribe el contenido; separa páginas con |. Usa MiniMessage.", "Type the contents; separate pages with |. MiniMessage is supported.");
        } else if (h.kind.equals("banner-editor")) {
            if (slot == 45) openSpecialProperties(p, h.project, h.page, h.targetSlot);
            else if (slot == 47) openBannerEditor(p, h.project, h.page, h.targetSlot, Math.max(0, h.subPage - 1), h.query);
            else if (slot == 51) openBannerEditor(p, h.project, h.page, h.targetSlot, h.subPage + 1, h.query);
            else if (slot == 49) {
                DyeColor[] colors = DyeColor.values();
                int index = 0;
                for (int i = 0; i < colors.length; i++) if (colors[i].name().equals(h.query)) { index = i; break; }
                openBannerEditor(p, h.project, h.page, h.targetSlot, h.subPage, colors[(index + 1) % colors.length].name());
            } else if (h.entryBySlot.containsKey(slot)) {
                File file = projectFile(h.project);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String itemPath = storagePath(h.page, h.targetSlot);
                ItemStack target = yaml.getItemStack(itemPath);
                if (target != null && target.getItemMeta() instanceof BannerMeta bannerMeta) {
                    PatternType type = Registry.BANNER_PATTERN.get(NamespacedKey.fromString(h.entryBySlot.get(slot)));
                    if (type != null) {
                        if (e.isShiftClick()) {
                            List<Pattern> patterns = new ArrayList<>(bannerMeta.getPatterns());
                            for (int i = patterns.size() - 1; i >= 0; i--) {
                                if (patterns.get(i).getPattern().getKey().equals(type.getKey())) { patterns.remove(i); break; }
                            }
                            bannerMeta.setPatterns(patterns);
                        } else if (bannerMeta.numberOfPatterns() < 6) {
                            bannerMeta.addPattern(new Pattern(DyeColor.valueOf(h.query), type));
                        } else {
                            p.sendMessage(PREFIX + tr("Un estandarte admite un máximo de seis patrones.", "A banner supports at most six patterns."));
                        }
                        target.setItemMeta(bannerMeta);
                        projectService.backupBeforeChange(file);
                        yaml.set(itemPath, target);
                        saveYaml(yaml, file);
                    }
                }
                openBannerEditor(p, h.project, h.page, h.targetSlot, h.subPage, h.query);
            }
        } else if (h.kind.equals("trim-editor")) {
            if (slot == 45) openSpecialProperties(p, h.project, h.page, h.targetSlot);
            else if (slot == 47) openTrimEditor(p, h.project, h.page, h.targetSlot, Math.max(0, h.subPage - 1), h.query);
            else if (slot == 51) openTrimEditor(p, h.project, h.page, h.targetSlot, h.subPage + 1, h.query);
            else if (slot == 49) {
                List<TrimMaterial> materials = new ArrayList<>();
                for (TrimMaterial material : Registry.TRIM_MATERIAL) materials.add(material);
                materials.sort(Comparator.comparing(material -> material.getKey().toString()));
                if (!materials.isEmpty()) {
                    int index = 0;
                    for (int i = 0; i < materials.size(); i++) if (materials.get(i).getKey().getKey().equals(h.query)) { index = i; break; }
                    openTrimEditor(p, h.project, h.page, h.targetSlot, h.subPage, materials.get((index + 1) % materials.size()).getKey().getKey());
                }
            } else if (h.entryBySlot.containsKey(slot)) {
                File file = projectFile(h.project);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String itemPath = storagePath(h.page, h.targetSlot);
                ItemStack target = yaml.getItemStack(itemPath);
                if (target != null && target.getItemMeta() instanceof ArmorMeta armorMeta) {
                    if (e.isShiftClick()) armorMeta.setTrim(null);
                    else {
                        NamespacedKey key = NamespacedKey.fromString(h.entryBySlot.get(slot));
                        TrimPattern pattern = key == null ? null : Registry.TRIM_PATTERN.get(key);
                        TrimMaterial material = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(h.query));
                        if (pattern != null && material != null) armorMeta.setTrim(new ArmorTrim(material, pattern));
                    }
                    target.setItemMeta(armorMeta);
                    projectService.backupBeforeChange(file);
                    yaml.set(itemPath, target);
                    saveYaml(yaml, file);
                }
                openTrimEditor(p, h.project, h.page, h.targetSlot, h.subPage, h.query);
            }
        } else if (h.kind.equals("firework-editor")) {
            if (slot == 45) openSpecialProperties(p, h.project, h.page, h.targetSlot);
            else if (slot == 20 || slot == 22 || slot == 24 || slot == 26 || slot == 30 || slot == 32) {
                String[] choices = h.query.split("\\|", -1);
                String typeName = choices.length > 0 ? choices[0] : "BALL";
                String colorName = choices.length > 1 ? choices[1] : "RED";
                FireworkEffect.Type[] types = FireworkEffect.Type.values();
                DyeColor[] colors = DyeColor.values();
                if (slot == 24) {
                    int index = 0;
                    for (int i = 0; i < types.length; i++) if (types[i].name().equals(typeName)) { index = i; break; }
                    typeName = types[(index + 1) % types.length].name();
                } else if (slot == 26) {
                    int index = 0;
                    for (int i = 0; i < colors.length; i++) if (colors[i].name().equals(colorName)) { index = i; break; }
                    colorName = colors[(index + 1) % colors.length].name();
                }
                ItemStack target = getProjectItem(h.project, h.page, h.targetSlot);
                if (target != null && target.getItemMeta() instanceof FireworkMeta fireworkMeta) {
                    if (slot == 20) fireworkMeta.setPower(Math.max(0, Math.min(3, fireworkMeta.getPower() + (e.isRightClick() ? -1 : 1))));
                    else if (slot == 22) fireworkMeta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.valueOf(typeName))
                        .withColor(DyeColor.valueOf(colorName).getColor()).withFade(Color.WHITE).trail(true).build());
                    else if (slot == 30 && fireworkMeta.getEffectsSize() > 0) fireworkMeta.removeEffect(fireworkMeta.getEffectsSize() - 1);
                    else if (slot == 32) fireworkMeta.clearEffects();
                    target.setItemMeta(fireworkMeta);
                    saveProjectItem(h.project, h.page, h.targetSlot, target);
                }
                openFireworkEditor(p, h.project, h.page, h.targetSlot, typeName + "|" + colorName);
            }
        } else if (h.kind.equals("catalog")) {
            if (slot == 45) openMain(p);
            else if (slot == 47) {
                if (h.page > 1) openCatalog(p, h.query, h.page - 1);
                else promptInput(p, InputType.CATALOG_SEARCH, null, h.page, -1, "Escribe un texto para buscar plantillas:", "Type a search query for templates:");
            } else if (slot == 51) openCatalog(p, h.query, h.page + 1);
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
        } else if (h.kind.equals("community-hub")) {
            if (slot == 45) openMain(p);
            else if (slot == 20) openCatalog(p, "");
            else if (slot == 22) openCommunityResources(p, "lore", 1);
            else if (slot == 24) openCommunityResources(p, "enchantments", 1);
            else if (slot == 30) openCommunityResources(p, "palette", 1);
            else if (slot == 32) p.sendMessage(PREFIX + tr("Para publicar, haz clic derecho sobre una plantilla local de lore, paleta o encantamientos. Para un kit, abre Herramientas del proyecto → Publicar en Community.", "To publish, right-click a local lore, palette or enchantment preset. To publish a kit, open Project Tools → Publish to Community."));
        } else if (h.kind.equals("community-resources")) {
            if (slot == 45) openCommunityHub(p);
            else if (slot == 47) openCommunityResources(p, h.project, Math.max(1, h.page - 1));
            else if (slot == 51) openCommunityResources(p, h.project, h.page + 1);
            else if (slot == 49) p.sendMessage(PREFIX + tr("En las bibliotecas locales, haz clic derecho en un recurso para publicarlo.", "In local libraries, right-click a resource to publish it."));
            else if (h.entryBySlot.containsKey(slot)) downloadCommunityResource(p, h.project, h.entryBySlot.get(slot), h.page);
        } else if (h.kind.equals("templates")) {
            if (slot == 45) openMain(p);
            else if (slot == 20) openLoreTemplates(p, 0);
            else if (slot == 22) openPaletteTemplates(p, 0, null, 0, -1);
            else if (slot == 24) openEnchantTemplates(p, 0, null, 0, -1);
            else if (slot == 30) openCommunityHub(p);
        } else if (h.kind.equals("enchantment-templates")) {
            int listPage = h.project == null ? h.page : h.subPage;
            if (slot == 45) {
                if (h.project == null) openTemplates(p);
                else openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 47) openEnchantTemplates(p, Math.max(0, listPage - 1), h.project, h.page, h.targetSlot);
            else if (slot == 51) openEnchantTemplates(p, listPage + 1, h.project, h.page, h.targetSlot);
            else if (h.entryBySlot.containsKey(slot)) {
                if (e.isRightClick()) publishLocalResource(p, "enchantments", h.entryBySlot.get(slot), null);
                else applyEnchantPreset(p, h.entryBySlot.get(slot), h.project, h.page, h.targetSlot);
            }
        } else if (h.kind.equals("palette-templates")) {
            int listPage = h.project == null ? h.page : h.subPage;
            if (slot == 45) {
                if (h.project == null) openTemplates(p);
                else openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 47) openPaletteTemplates(p, Math.max(0, listPage - 1), h.project, h.page, h.targetSlot);
            else if (slot == 51) openPaletteTemplates(p, listPage + 1, h.project, h.page, h.targetSlot);
            else if (h.entryBySlot.containsKey(slot)) {
                if (e.isRightClick()) publishLocalResource(p, "palette", h.entryBySlot.get(slot), null);
                else applyPalettePreset(p, h.entryBySlot.get(slot), h.project, h.page, h.targetSlot);
            }
        } else if (h.kind.equals("lore-templates")) {
            int listPage = h.project == null ? h.page : h.subPage;
            if (slot == 45) {
                if (h.project == null) openTemplates(p);
                else openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 47) openLoreTemplates(p, Math.max(0, listPage - 1), h.project, h.page, h.targetSlot);
            else if (slot == 51) openLoreTemplates(p, listPage + 1, h.project, h.page, h.targetSlot);
            else if (h.entryBySlot.containsKey(slot)) {
                if (e.isRightClick()) publishLocalResource(p, "lore", h.entryBySlot.get(slot), null);
                else if (h.project == null) applyLoreTemplateToHand(p, h.entryBySlot.get(slot));
                else applyLoreTemplateToProject(p, h.entryBySlot.get(slot), h.project, h.page, h.targetSlot);
            }
        } else if (h.kind.equals("lore-editor")) {
            if (slot == 45) openItemEditor(p, h.project, h.page, h.targetSlot);
            else if (slot == 47) openLoreEditor(p, h.project, h.page, h.targetSlot, Math.max(0, h.subPage - 1));
            else if (slot == 48) promptLoreInput(p, InputType.LORE_ADD_LINE, h.project, h.page, h.targetSlot, -1,
                "Escribe la nueva línea de lore (MiniMessage/RGB):", "Type the new lore line (MiniMessage/RGB):");
            else if (slot == 49) promptLoreInput(p, InputType.LORE_CLEAR_CONFIRM, h.project, h.page, h.targetSlot, -1,
                "Escribe BORRAR para quitar todo el lore, o -cancelar para conservarlo:", "Type CLEAR to remove all lore, or -cancel to keep it:");
            else if (slot == 51) openLoreEditor(p, h.project, h.page, h.targetSlot, h.subPage + 1);
            else if (h.entryBySlot.containsKey(slot)) {
                int lineIndex = Integer.parseInt(h.entryBySlot.get(slot));
                ItemStack target = getProjectItem(h.project, h.page, h.targetSlot);
                if (target == null || target.getType().isAir()) { openItemEditor(p, h.project, h.page, h.targetSlot); return; }
                List<net.kyori.adventure.text.Component> lines = target.getItemMeta().lore() == null
                    ? new ArrayList<>() : new ArrayList<>(target.getItemMeta().lore());
                if (lineIndex < 0 || lineIndex >= lines.size()) { openLoreEditor(p, h.project, h.page, h.targetSlot, h.subPage); return; }
                if (e.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    lines.add(lineIndex + 1, lines.get(lineIndex));
                    saveLoreLines(p, h.project, h.page, h.targetSlot, lines, h.subPage);
                } else if (e.isShiftClick()) {
                    int moveTo = lineIndex + (e.isRightClick() ? 1 : -1);
                    if (moveTo >= 0 && moveTo < lines.size()) Collections.swap(lines, lineIndex, moveTo);
                    saveLoreLines(p, h.project, h.page, h.targetSlot, lines, h.subPage);
                } else if (e.isRightClick()) {
                    lines.remove(lineIndex);
                    saveLoreLines(p, h.project, h.page, h.targetSlot, lines, h.subPage);
                } else promptLoreInput(p, InputType.LORE_EDIT_LINE, h.project, h.page, h.targetSlot, lineIndex,
                    "Edita la línea " + (lineIndex + 1) + " con MiniMessage/RGB:", "Edit line " + (lineIndex + 1) + " using MiniMessage/RGB:");
            }
        } else if (h.kind.equals("abilities")) {
            if (slot == 45) openItemEditor(p, h.project, h.page, h.targetSlot);
            else if (slot == 20) { if (e.isRightClick()) promptAbilityCooldown(p, h.project, h.page, h.targetSlot, "tnt"); else toggleAbility(p, h.project, h.page, h.targetSlot, tntOnHitKey); }
            else if (slot == 22) { if (e.isRightClick()) promptAbilityCooldown(p, h.project, h.page, h.targetSlot, "lightning"); else toggleAbility(p, h.project, h.page, h.targetSlot, lightningOnHitKey); }
            else if (slot == 24) { if (e.isRightClick()) promptAbilityCooldown(p, h.project, h.page, h.targetSlot, "lifesteal"); else toggleAbility(p, h.project, h.page, h.targetSlot, lifeStealOnHitKey); }
        } else if (h.kind.equals("item-editor")) {
            if (slot == 45) {
                if (h.targetSlot < 0) openEquipment(p, h.project, h.page);
                else openEditor(p, h.project, h.page);
            }
            else if (slot == 15) {
                ItemStack source = getProjectItem(h.project, h.page, h.targetSlot);
                if (source != null && !source.getType().isAir()) {
                    itemClipboard.put(p.getUniqueId(), source.clone());
                    p.sendMessage(PREFIX + tr("Objeto copiado. Haz clic izquierdo en una ranura vacía del proyecto para pegarlo.", "Item copied. Left-click an empty project slot to paste it."));
                }
                openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 16) {
                p.sendMessage(PREFIX + (itemClipboard.containsKey(p.getUniqueId())
                    ? tr("El portapapeles contiene: §b", "Clipboard contains: §b") + prettyKey(itemClipboard.get(p.getUniqueId()).getType().name())
                    : tr("El portapapeles está vacío.", "The clipboard is empty.")));
                openItemEditor(p, h.project, h.page, h.targetSlot);
            } else if (slot == 14) openSpecialProperties(p, h.project, h.page, h.targetSlot);
            else if (slot == 20) openEnchantments(p, h.project, h.page, h.targetSlot, 0);
            else if (slot == 27) openEnchantTemplates(p, 0, h.project, h.page, h.targetSlot);
            else if (slot == 32) openPaletteTemplates(p, 0, h.project, h.page, h.targetSlot);
            else if (slot == 30) openAbilities(p, h.project, h.page, h.targetSlot);
            else if (slot == 22) openLoreTemplates(p, 0, h.project, h.page, h.targetSlot);
            else if (slot == 11) promptInput(p, InputType.ITEM_NAME, h.project, h.page, h.targetSlot, "Escribe el nombre MiniMessage del objeto:", "Type the item's MiniMessage name:");
            else if (slot == 13) openLoreEditor(p, h.project, h.page, h.targetSlot, 0);
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
            else if (slot == 39) openFlags(p, h.project, h.page, h.targetSlot); else if (slot == 41) {
                ItemStack target = getProjectItem(h.project, h.page, h.targetSlot);
                if (target != null && target.getItemMeta() instanceof LeatherArmorMeta) promptInput(p, InputType.LEATHER_COLOR, h.project, h.page, h.targetSlot, "Escribe el color como #RRGGBB o R,G,B:", "Type a color as #RRGGBB or R,G,B:");
                else p.sendMessage(PREFIX + tr("Solo disponible para armadura de cuero.", "Only available for leather armor."));
            } else if (slot == 43) openPotionTypes(p, h.project, h.page, h.targetSlot, 0);
            else if (slot == 24) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(projectFile(h.project));
                ItemStack target = y.getItemStack(storagePath(h.page, h.targetSlot));
                if (target != null) {
                    for (Enchantment enchantment : new ArrayList<>(target.getEnchantments().keySet())) target.removeEnchantment(enchantment);
                    y.set(storagePath(h.page, h.targetSlot), target);
                    saveYaml(y, projectFile(h.project));
                    p.sendMessage(PREFIX + tr("Se quitaron todos los encantamientos del objeto.", "All enchantments were removed from the item."));
                }
                openItemEditor(p, h.project, h.page, h.targetSlot);
            }
        } else if (h.kind.equals("potion-types")) {
            if (slot == 45) openItemEditor(p, h.project, h.page, h.targetSlot);
            else if (slot == 47) openPotionTypes(p, h.project, h.page, h.targetSlot, Math.max(0, h.subPage - 1));
            else if (slot == 49) openPotionEffects(p, h.project, h.page, h.targetSlot, 0);
            else if (slot == 51) openPotionTypes(p, h.project, h.page, h.targetSlot, h.subPage + 1);
            else if (h.entryBySlot.containsKey(slot)) {
                File file = projectFile(h.project);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String itemPath = storagePath(h.page, h.targetSlot);
                ItemStack target = yaml.getItemStack(itemPath);
                if (target != null && target.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType type = PotionType.valueOf(h.entryBySlot.get(slot));
                    potionMeta.setBasePotionType(type);
                    target.setItemMeta(potionMeta);
                    projectService.backupBeforeChange(file);
                    yaml.set(itemPath, target);
                    yaml.set("format-version", 2);
                    yaml.set("updated", System.currentTimeMillis());
                    saveYaml(yaml, file);
                }
                openPotionTypes(p, h.project, h.page, h.targetSlot, h.subPage);
            }
        } else if (h.kind.equals("potion-effects")) {
            if (slot == 45) openPotionTypes(p, h.project, h.page, h.targetSlot, 0);
            else if (slot == 47) openPotionEffects(p, h.project, h.page, h.targetSlot, Math.max(0, h.subPage - 1));
            else if (slot == 51) openPotionEffects(p, h.project, h.page, h.targetSlot, h.subPage + 1);
            else if (h.entryBySlot.containsKey(slot)) {
                File file = projectFile(h.project);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String itemPath = storagePath(h.page, h.targetSlot);
                ItemStack target = yaml.getItemStack(itemPath);
                if (target != null && target.getItemMeta() instanceof PotionMeta potionMeta) {
                    NamespacedKey effectKey = NamespacedKey.fromString(h.entryBySlot.get(slot));
                    PotionEffectType effectType = effectKey == null ? null : Registry.MOB_EFFECT.get(effectKey);
                    if (effectType != null) {
                        PotionEffect active = potionMeta.getCustomEffects().stream()
                            .filter(effect -> effect.getType().getKey().equals(effectType.getKey())).findFirst().orElse(null);
                        if (e.isShiftClick() || (active != null && !e.isRightClick())) {
                            potionMeta.removeCustomEffect(effectType);
                        } else if (active == null) {
                            potionMeta.addCustomEffect(new PotionEffect(effectType, 600, 0), true);
                        } else {
                            int nextAmplifier = active.getAmplifier() >= 4 ? 0 : active.getAmplifier() + 1;
                            potionMeta.addCustomEffect(new PotionEffect(effectType, active.getDuration(), nextAmplifier,
                                active.isAmbient(), active.hasParticles(), active.hasIcon()), true);
                        }
                        target.setItemMeta(potionMeta);
                        projectService.backupBeforeChange(file);
                        yaml.set(itemPath, target);
                        yaml.set("format-version", 2);
                        yaml.set("updated", System.currentTimeMillis());
                        saveYaml(yaml, file);
                    }
                }
                openPotionEffects(p, h.project, h.page, h.targetSlot, h.subPage);
            }
        } else if (h.kind.equals("flags")) {
            if (slot == 45) openItemEditor(p, h.project, h.page, h.targetSlot);
            else if (h.entryBySlot.containsKey(slot)) {
                File file = projectFile(h.project);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String itemPath = storagePath(h.page, h.targetSlot);
                ItemStack target = yaml.getItemStack(itemPath);
                if (target != null && !target.getType().isAir()) {
                    ItemFlag flag = ItemFlag.valueOf(h.entryBySlot.get(slot));
                    ItemMeta meta = target.getItemMeta();
                    if (meta.hasItemFlag(flag)) meta.removeItemFlags(flag);
                    else meta.addItemFlags(flag);
                    target.setItemMeta(meta);
                    projectService.backupBeforeChange(file);
                    yaml.set(itemPath, target);
                    yaml.set("format-version", 2);
                    yaml.set("updated", System.currentTimeMillis());
                    saveYaml(yaml, file);
                }
                openFlags(p, h.project, h.page, h.targetSlot);
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
                ItemStack target = y.getItemStack(storagePath(h.page, h.targetSlot));
                if (target != null) {
                    for (Enchantment enchantment : new ArrayList<>(target.getEnchantments().keySet())) target.removeEnchantment(enchantment);
                    y.set(storagePath(h.page, h.targetSlot), target);
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
                String itemPath = storagePath(h.page, h.targetSlot);
                ItemStack target = y.getItemStack(itemPath);
                if (target == null || target.getType().isAir()) {
                    p.sendMessage(PREFIX + tr("El objeto del proyecto ya no existe.", "The project item no longer exists."));
                    openEditor(p, h.project, h.page);
                    return;
                }
                int current = target.getEnchantmentLevel(enchantment);
                if (e.isShiftClick() && !e.isRightClick()) {
                    promptEnchantLevel(p, h.project, h.page, h.targetSlot, rawKey);
                    return;
                }
                if (e.isShiftClick()) target.removeEnchantment(enchantment);
                else if (e.isRightClick()) {
                    if (current <= 1) target.removeEnchantment(enchantment);
                    else target.addUnsafeEnchantment(enchantment, current - 1);
                } else {
                    int next = current <= 0 ? 1 : Math.min(32767, current + 1);
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
        int selectedProjectSlot = -1;
        for (int slot : e.getRawSlots()) {
            if (slot < e.getView().getTopInventory().getSize()) {
                if (!contains(EDIT_SLOTS, slot)) { e.setCancelled(true); return; }
                if (selectedProjectSlot == -1) selectedProjectSlot = slot;
                else selectedProjectSlot = -2;
            }
        }
        if (selectedProjectSlot >= 0 && e.getWhoClicked() instanceof Player player) {
            final int targetSlot = selectedProjectSlot;
            final Inventory sourceInventory = e.getView().getTopInventory();
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory() == sourceInventory) {
                    ItemStack selected = sourceInventory.getItem(targetSlot);
                    if (selected != null && !selected.getType().isAir()) {
                        savePage(sourceInventory, h);
                        openItemEditor(player, h.project, h.page, targetSlot);
                    }
                }
            });
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

    private boolean isAbilityEnabled(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta() || key == null) return false;
        Byte enabled = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return enabled != null && enabled == (byte) 1;
    }

    private boolean isTntOnHitEnabled(ItemStack stack) {
        return isAbilityEnabled(stack, tntOnHitKey);
    }

    private boolean canTriggerAbility(Player player, String ability, long cooldownMs) {
        long now = System.currentTimeMillis();
        String cooldownKey = player.getUniqueId() + ":" + ability;
        Long last = abilityCooldowns.get(cooldownKey);
        if (last != null && now - last < cooldownMs) return false;
        abilityCooldowns.put(cooldownKey, now);
        return true;
    }

    private void toggleAbility(Player p, String project, int page, int targetSlot, NamespacedKey key) {
        ItemStack target = getProjectItem(project, page, targetSlot);
        if (target != null && !target.getType().isAir()) {
            ItemMeta meta = target.getItemMeta();
            var container = meta.getPersistentDataContainer();
            if (isAbilityEnabled(target, key)) container.remove(key);
            else container.set(key, PersistentDataType.BYTE, (byte) 1);
            target.setItemMeta(meta);
            saveProjectItem(project, page, targetSlot, target);
        }
        openAbilities(p, project, page, targetSlot);
    }

    @EventHandler
    public void onCustomWeaponHit(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getDamager() instanceof Player attacker)
            || !(event.getEntity() instanceof LivingEntity target)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (isAbilityEnabled(weapon, tntOnHitKey)) {
            long cooldown = getAbilityCooldown(weapon, "tnt");
            if (canTriggerAbility(attacker, "tnt-on-hit", cooldown)) {
                int fuseTicks = Math.max(1, Math.min(200, getConfig().getInt("abilities.tnt-on-hit-fuse-ticks", 40)));
                float yield = (float) Math.max(0.0, Math.min(8.0, getConfig().getDouble("abilities.tnt-on-hit-yield", 2.0)));
                TNTPrimed tnt = target.getWorld().spawn(target.getLocation().add(0.0, 0.25, 0.0), TNTPrimed.class);
                tnt.setFuseTicks(fuseTicks);
                tnt.setYield(yield);
                tnt.setSource(attacker);
            }
        }
        if (isAbilityEnabled(weapon, lightningOnHitKey)) {
            long cooldown = getAbilityCooldown(weapon, "lightning");
            if (canTriggerAbility(attacker, "lightning-on-hit", cooldown)) target.getWorld().strikeLightning(target.getLocation());
        }
        if (isAbilityEnabled(weapon, lifeStealOnHitKey)) {
            long cooldown = getAbilityCooldown(weapon, "lifesteal");
            if (canTriggerAbility(attacker, "lifesteal-on-hit", cooldown)) {
                double percent = Math.max(0.0, Math.min(1.0, getConfig().getDouble("abilities.lifesteal-on-hit-percent", 0.25)));
                var maximumHealth = attacker.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health")));
                double maximum = maximumHealth == null ? 20.0 : maximumHealth.getValue();
                double healing = event.getFinalDamage() * percent;
                attacker.setHealth(Math.min(maximum, attacker.getHealth() + healing));
                attacker.sendActionBar(Component.text("+" + String.format(Locale.US, "%.1f", healing) + " ♥"));
            }
        }
    }

    private boolean isEquipmentPlaceholder(ItemStack item) {
        return item != null && item.getType() == Material.GRAY_STAINED_GLASS_PANE
            && item.hasItemMeta() && Objects.equals(item.getItemMeta().getDisplayName(), "§8IxKits empty slot");
    }

    private String storagePath(int page, int targetSlot) {
        if (targetSlot < 0) {
            int index = -targetSlot - 1;
            if (index >= 0 && index < EQUIPMENT_KEYS.length) return "equipment." + EQUIPMENT_KEYS[index];
        }
        return "pages." + page + "." + targetSlot;
    }

    private int equipmentTargetSlot(int inventorySlot) {
        for (int i = 0; i < EQUIP_SLOTS.length; i++) if (EQUIP_SLOTS[i] == inventorySlot) return -(i + 1);
        return 0;
    }

    private int preferredEquipmentSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || material == Material.CARVED_PUMPKIN) return 19;
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) return 21;
        if (name.endsWith("_LEGGINGS")) return 23;
        if (name.endsWith("_BOOTS")) return 25;
        return -1;
    }

    private boolean isEmptyEquipmentSlot(ItemStack item) {
        return item == null || item.getType().isAir() || isEquipmentPlaceholder(item);
    }

    private ItemStack getProjectItem(String project, int page, int slot) {
        return YamlConfiguration.loadConfiguration(projectFile(project)).getItemStack(storagePath(page, slot));
    }

    private void saveProjectItem(String project, int page, int slot, ItemStack item) {
        File file = projectFile(project);
        projectService.backupBeforeChange(file);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(storagePath(page, slot), item);
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
        ensureLoreTemplate(lore, "demonic", "&4✦ &lDemoníaco", "&4✦ &lDemonic",
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&4☠ &lARTEFACTO DEMONÍACO", "&7Forjado en las profundidades.", "",
                "&4◆ &7Rareza: &cMaldita", "&4◆ &7Poder: &fExtremo", "",
                "&c➤ &fQue tiemblen tus enemigos", "&8&m━━━━━━━━━━━━━━━━━━━━"),
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&4☠ &lDEMONIC ARTIFACT", "&7Forged in the depths.", "",
                "&4◆ &7Rarity: &cCursed", "&4◆ &7Power: &fExtreme", "",
                "&c➤ &fLet your enemies tremble", "&8&m━━━━━━━━━━━━━━━━━━━━"));
        ensureLoreTemplate(lore, "cosmic", "&d✧ &lCósmico", "&d✧ &lCosmic",
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&d✦ &lEQUIPO CÓSMICO", "&7Energía nacida entre estrellas.", "",
                "&d▸ &7Clase: &fAstral", "&d▸ &7Rareza: &dCelestial", "",
                "&d➤ &fEl universo está en tus manos", "&8&m━━━━━━━━━━━━━━━━━━━━"),
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&d✦ &lCOSMIC GEAR", "&7Energy born among the stars.", "",
                "&d▸ &7Class: &fAstral", "&d▸ &7Rarity: &dCelestial", "",
                "&d➤ &fThe universe is in your hands", "&8&m━━━━━━━━━━━━━━━━━━━━"));
        ensureLoreTemplate(lore, "frost", "&b❄ &lEscarcha", "&b❄ &lFrost",
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&b❄ &lRELIQUIA DE HIELO", "&7Fría como el vacío invernal.", "",
                "&b▸ &7Afinidad: &fHielo", "&b▸ &7Estado: &fImperturbable", "",
                "&b➤ &fDeja una huella de escarcha", "&8&m━━━━━━━━━━━━━━━━━━━━"),
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&b❄ &lFROST RELIC", "&7Cold as the winter void.", "",
                "&b▸ &7Affinity: &fIce", "&b▸ &7State: &fUnyielding", "",
                "&b➤ &fLeave a trail of frost", "&8&m━━━━━━━━━━━━━━━━━━━━"));
        ensureLoreTemplate(lore, "emerald", "&a❖ &lEsmeralda", "&a❖ &lEmerald",
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&a❖ &lTESORO ESMERALDA", "&7Una pieza de valor excepcional.", "",
                "&a▸ &7Calidad: &fPremium", "&a▸ &7Origen: &2Antiguo", "",
                "&a➤ &fUn tesoro digno de leyenda", "&8&m━━━━━━━━━━━━━━━━━━━━"),
            List.of("&8&m━━━━━━━━━━━━━━━━━━━━", "&a❖ &lEMERALD TREASURE", "&7An item of exceptional value.", "",
                "&a▸ &7Quality: &fPremium", "&a▸ &7Origin: &2Ancient", "",
                "&a➤ &fA treasure worthy of legend", "&8&m━━━━━━━━━━━━━━━━━━━━"));
        saveYaml(lore, loreFile);

        File paletteFile = new File(templatesDir, "palettes.yml");
        YamlConfiguration palettes = YamlConfiguration.loadConfiguration(paletteFile);
        ensurePalette(palettes, "ocean", "Océano", "&b", "&3");
        ensurePalette(palettes, "royal", "Real", "&5", "&d");
        ensurePalette(palettes, "inferno", "Infernal", "&c", "&6");
        ensurePalette(palettes, "gold", "Oro", "&6", "&e");
        ensurePalette(palettes, "emerald", "Esmeralda", "&a", "&2");
        ensurePalette(palettes, "frost", "Escarcha", "#A7E8FF", "#FFFFFF");
        ensurePalette(palettes, "rose", "Rosa", "#FF6FAE", "#FFD1E4");
        ensurePalette(palettes, "shadow", "Sombra", "&8", "&5");
        ensurePalette(palettes, "sunset", "Atardecer", "#FF7A35", "#FFC857");
        ensurePalette(palettes, "void", "Vacío", "#9D4EDD", "#3C096C");
        ensurePalette(palettes, "nature", "Naturaleza", "#62D26F", "#C7F9CC");
        ensurePalette(palettes, "ice", "Hielo", "#5CE1E6", "#DDFBFF");
        ensurePalette(palettes, "ruby", "Rubí", "#FF334F", "#990018");
        ensurePalette(palettes, "sand", "Arena", "#D9B382", "#FFF0C2");
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
        setPresetDefaults(enchants, "god_sword", Map.of("sharpness", 8, "unbreaking", 5, "fire_aspect", 2, "looting", 4, "sweeping_edge", 4, "mending", 1));
        setPresetDefaults(enchants, "archer_bow", Map.of("power", 6, "punch", 2, "flame", 1, "infinity", 1, "unbreaking", 4));
        setPresetDefaults(enchants, "tank_armor", Map.of("protection", 5, "unbreaking", 5, "thorns", 3, "mending", 1));
        setPresetDefaults(enchants, "miner_tool", Map.of("efficiency", 7, "unbreaking", 5, "fortune", 4, "mending", 1));
        setPresetDefaults(enchants, "ocean_trident", Map.of("loyalty", 3, "impaling", 5, "channeling", 1, "unbreaking", 4, "mending", 1));
        setPresetDefaults(enchants, "mace_burst", Map.of("density", 5, "breach", 4, "wind_burst", 2, "unbreaking", 4, "mending", 1));
        setPresetDefaults(enchants, "swift_boots", Map.of("feather_falling", 5, "depth_strider", 4, "soul_speed", 3, "unbreaking", 4, "mending", 1));
        setPresetDefaults(enchants, "fishing_rod", Map.of("luck_of_the_sea", 4, "lure", 4, "unbreaking", 4, "mending", 1));
        saveYaml(enchants, enchantFile);
    }

    private void ensurePalette(YamlConfiguration yaml, String key, String display, String color, String accent) {
        if (!yaml.contains(key + ".display")) yaml.set(key + ".display", display);
        if (!yaml.contains(key + ".color")) yaml.set(key + ".color", color);
        if (!yaml.contains(key + ".accent")) yaml.set(key + ".accent", accent);
    }

    private void setPresetDefaults(YamlConfiguration yaml, String preset, Map<String, Integer> values) {
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            String path = preset + "." + entry.getKey();
            if (!yaml.contains(path)) yaml.set(path, entry.getValue());
        }
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
        String itemPath = storagePath(projectPage, targetSlot);
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
    /**
     * Writes YAML through a temporary file so an interrupted write does not leave
     * the last good project file truncated or half-written.
     */
    private void saveYaml(YamlConfiguration yaml, File file) {
        Path temporary = null;
        try {
            Path destination = file.toPath().toAbsolutePath();
            Path parent = destination.getParent();
            if (parent != null) Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, file.getName() + ".", ".tmp");
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            getLogger().severe("No se pudo guardar " + file.getName() + ": " + ex.getMessage());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { }
            }
        }
    }

    private enum InputType {
        DUPLICATE_PROJECT, RENAME_PROJECT, ITEM_NAME, ITEM_LORE, LORE_ADD_LINE, LORE_EDIT_LINE, LORE_CLEAR_CONFIRM, MODEL_DATA, LEATHER_COLOR, CATALOG_SEARCH, PROJECT_SEARCH, CREATE_PROJECT, PUBLISH_REVISION_ID, PUBLISH_PROJECT_CATEGORY, PUBLISH_PROJECT_DESCRIPTION, BOOK_TITLE, BOOK_AUTHOR, BOOK_PAGES, SKULL_OWNER, ENCHANTMENT_LEVEL, ABILITY_COOLDOWN
    }

    private static final class PendingInput {
        final InputType type;
        final String project;
        final int page;
        final int slot;
        final int lineIndex;
        final String argument;
        PendingInput(InputType type, String project, int page, int slot) {
            this(type, project, page, slot, -1, null);
        }
        PendingInput(InputType type, String project, int page, int slot, int lineIndex) {
            this(type, project, page, slot, lineIndex, null);
        }
        PendingInput(InputType type, String project, int page, int slot, int lineIndex, String argument) {
            this.type = type;
            this.project = project;
            this.page = page;
            this.slot = slot;
            this.lineIndex = lineIndex;
            this.argument = argument;
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