package de.jawollo07.manhunt.map;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Manages disposable map copies and supports legacy plus Paper 26.1+ world storage. */
public final class MapManager {
    private static final Pattern LEGACY_ID = Pattern.compile("[a-zA-Z0-9_.-]{1,48}");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,48}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9/._-]{1,48}");

    private final JavaPlugin plugin;
    private final Path worldContainer;
    private final Map<UUID, LoadedMap> instances = new ConcurrentHashMap<>();
    private final Set<String> editorLoadedTemplates = new HashSet<>();

    public MapManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
    }

    public MapInstance createWorkingCopy(String templateId) throws IOException {
        requireServerThread();
        validateTemplateId(templateId);
        TemplateLocation template = resolveTemplate(templateId);
        if (template != null) template = canonicalizeDimensionFolder(template);
        if (template == null || !isValidWorldFolder(template.folder())) {
            throw new IllegalArgumentException("Map template does not exist or is invalid: " + templateId);
        }
        if (getLoadedTemplateWorld(templateId) != null) {
            throw new IllegalStateException("Map template must be unloaded before it can be copied: " + templateId);
        }

        UUID instanceId = UUID.randomUUID();
        String suffix = instanceId.toString().replace("-", "").substring(0, 12);
        String worldName;
        Path destination;
        WorldCreator creator;
        if (template.dimensionKey() != null) {
            NamespacedKey instanceKey = new NamespacedKey("manhunt", "instance_" + suffix);
            worldName = instanceKey.toString();
            destination = dimensionFolder(instanceKey);
            creator = WorldCreator.ofKey(instanceKey);
        } else {
            worldName = "manhunt_" + templateId.toLowerCase(Locale.ROOT) + "_" + suffix;
            destination = worldContainer.resolve(worldName).normalize();
            creator = new WorldCreator(worldName);
        }

        if (!isSafeDestination(destination) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Temporary world destination already exists or is invalid: " + destination);
        }

        boolean loaded = false;
        try {
            copyWorldFolder(template.folder(), destination);
            World world = Bukkit.createWorld(creator);
            if (world == null) throw new IOException("Paper could not load temporary world: " + worldName);
            loaded = true;
            MapInstance instance = new MapInstance(instanceId, templateId, world.getName(), Instant.now());
            instances.put(instanceId, new LoadedMap(instance, destination, world.getUID()));
            plugin.getLogger().info("Created temporary map " + world.getName() + " from template " + templateId);
            return instance;
        } catch (RuntimeException | IOException exception) {
            if (loaded) {
                World world = template.dimensionKey() == null
                        ? Bukkit.getWorld(worldName) : Bukkit.getWorld(creator.key());
                if (world != null) Bukkit.unloadWorld(world, false);
            }
            deleteRecursively(destination);
            throw exception;
        }
    }

    public World getWorld(MapInstance instance) {
        requireServerThread();
        LoadedMap loaded = requireTracked(instance);
        World world = Bukkit.getWorld(loaded.worldUid());
        if (world == null) throw new IllegalStateException("Temporary map is no longer loaded: " + instance.worldName());
        return world;
    }

    public void deleteWorkingCopy(MapInstance instance) throws IOException {
        requireServerThread();
        LoadedMap loaded = requireTracked(instance);
        World world = Bukkit.getWorld(loaded.worldUid());
        if (world != null) {
            if (!world.getPlayers().isEmpty()) throw new IllegalStateException("Cannot delete temporary map while players are still inside it");
            if (!Bukkit.unloadWorld(world, true)) throw new IllegalStateException("Paper refused to unload temporary world: " + instance.worldName());
        }
        deleteRecursively(loaded.folder());
        instances.remove(instance.id(), loaded);
        plugin.getLogger().info("Deleted temporary map " + instance.worldName());
    }

    public void deleteAllWorkingCopies() throws IOException {
        requireServerThread();
        IOException failure = null;
        for (LoadedMap loaded : List.copyOf(instances.values())) {
            try {
                deleteWorkingCopy(loaded.instance());
            } catch (IOException | RuntimeException exception) {
                if (failure == null) failure = new IOException("One or more temporary maps could not be deleted");
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    /** Finds legacy worlds in the server root and namespaced worlds below the primary world's dimensions folder. */
    public List<MapTemplate> listTemplates() throws IOException {
        requireServerThread();
        Map<String, TemplateLocation> found = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        if (Files.isDirectory(worldContainer, LinkOption.NOFOLLOW_LINKS)) {
            try (var entries = Files.list(worldContainer)) {
                for (Path folder : entries
                        .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(path -> !path.getFileName().toString().startsWith("manhunt_"))
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .filter(MapManager::isValidWorldFolder).toList()) {
                    String id = folder.getFileName().toString();
                    if (LEGACY_ID.matcher(id).matches()) found.put(id, new TemplateLocation(id, folder, null));
                }
            }
        }

        Path dimensions = dimensionsFolder();
        if (Files.isDirectory(dimensions, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(dimensions)) {
            try (var firstLevel = Files.list(dimensions)) {
                for (Path first : firstLevel
                        .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> !Files.isSymbolicLink(path)).toList()) {
                    String firstName = first.getFileName().toString();

                    // Also recognize a one-level dimensions/<world> folder used by some migrated setups.
                    if (isValidWorldFolder(first) && KEY.matcher(firstName).matches()) {
                        NamespacedKey key = NamespacedKey.minecraft(firstName);
                        found.put(key.toString(), new TemplateLocation(key.toString(), first, key));
                        continue;
                    }

                    if (!NAMESPACE.matcher(firstName).matches()) continue;
                    try (var secondLevel = Files.list(first)) {
                        for (Path folder : secondLevel
                                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                                .filter(path -> !Files.isSymbolicLink(path))
                                .filter(MapManager::isValidWorldFolder).toList()) {
                            String keyName = folder.getFileName().toString();
                            if (!KEY.matcher(keyName).matches()) continue;
                            NamespacedKey key = new NamespacedKey(firstName, keyName);
                            found.put(key.toString(), new TemplateLocation(key.toString(), folder, key));
                        }
                    }
                }
            }
        }

        List<MapTemplate> templates = new ArrayList<>();
        found.values().stream()
                .sorted(Comparator.comparing(location -> location.id().toLowerCase(Locale.ROOT)))
                .forEach(location -> templates.add(inspectTemplate(location)));
        return List.copyOf(templates);
    }

    public MapTemplate inspectTemplate(String templateId) {
        requireServerThread();
        validateTemplateId(templateId);
        TemplateLocation location = resolveTemplate(templateId);
        if (location == null || !isValidWorldFolder(location.folder())) {
            return new MapTemplate(templateId, false, getLoadedTemplateWorld(templateId) != null,
                    "Weltordner fehlt oder enthält keine gültige level.dat");
        }
        boolean loaded = getLoadedTemplateWorld(templateId) != null;
        return new MapTemplate(templateId, true, loaded,
                loaded ? "Welt ist geladen – vor Spielstart entladen" : "Bereit");
    }

    public World loadTemplateForEditing(String templateId) throws IOException {
        requireServerThread();
        MapTemplate template = inspectTemplate(templateId);
        if (!template.valid()) throw new IllegalArgumentException("Invalid map template: " + template.status());
        World existing = getLoadedTemplateWorld(templateId);
        if (existing != null) return existing;

        TemplateLocation location = resolveTemplate(templateId);
        location = canonicalizeDimensionFolder(location);
        WorldCreator creator = location.dimensionKey() == null
                ? new WorldCreator(templateId) : WorldCreator.ofKey(location.dimensionKey());
        World world = Bukkit.createWorld(creator);
        if (world == null) throw new IOException("Paper could not load map template: " + templateId);
        editorLoadedTemplates.add(templateId.toLowerCase(Locale.ROOT));
        plugin.getLogger().info("Loaded map template for editing: " + templateId);
        return world;
    }

    public boolean unloadTemplateAfterEditing(String templateId) {
        requireServerThread();
        validateTemplateId(templateId);
        String key = templateId.toLowerCase(Locale.ROOT);
        if (!editorLoadedTemplates.contains(key)) return false;
        World world = getLoadedTemplateWorld(templateId);
        if (world == null) {
            editorLoadedTemplates.remove(key);
            return true;
        }
        if (!world.getPlayers().isEmpty()) throw new IllegalStateException("Players are still inside template world: " + templateId);
        world.save();
        if (!Bukkit.unloadWorld(world, true)) throw new IllegalStateException("Paper refused to unload template world: " + templateId);
        editorLoadedTemplates.remove(key);
        plugin.getLogger().info("Unloaded edited map template: " + templateId);
        return true;
    }

    public boolean isLoadedForEditing(String templateId) {
        requireServerThread();
        return templateId != null && editorLoadedTemplates.contains(templateId.toLowerCase(Locale.ROOT));
    }

    public World getLoadedTemplateWorld(String templateId) {
        requireServerThread();
        if (templateId == null || templateId.isBlank()) return null;
        if (templateId.contains(":")) {
            NamespacedKey key = parseDimensionKey(templateId);
            return key == null ? null : Bukkit.getWorld(key);
        }
        return Bukkit.getWorld(templateId);
    }

    public boolean isTemplateWorld(World world, String templateId) {
        requireServerThread();
        if (world == null || templateId == null) return false;
        if (templateId.contains(":")) {
            NamespacedKey key = parseDimensionKey(templateId);
            return key != null && world.getKey().equals(key);
        }
        return world.getName().equalsIgnoreCase(templateId);
    }

    public int getActiveInstanceCount() {
        return instances.size();
    }

    private TemplateLocation resolveTemplate(String templateId) {
        if (templateId.contains(":")) {
            NamespacedKey key = parseDimensionKey(templateId);
            if (key == null) return null;
            Path folder = dimensionFolder(key);
            if (isValidWorldFolder(folder)) return new TemplateLocation(key.toString(), folder, key);
            if ("minecraft".equals(key.getNamespace())) {
                Path oneLevel = dimensionsFolder().resolve(key.getKey()).normalize();
                if (isValidWorldFolder(oneLevel)) return new TemplateLocation(key.toString(), oneLevel, key);
            }
            return null;
        }

        Path legacy = worldContainer.resolve(templateId).normalize();
        if (isSafeWorldPath(legacy) && isValidWorldFolder(legacy)) return new TemplateLocation(templateId, legacy, null);

        NamespacedKey key = NamespacedKey.minecraft(templateId);
        Path modern = dimensionFolder(key);
        if (isValidWorldFolder(modern)) return new TemplateLocation(key.toString(), modern, key);
        Path oneLevel = dimensionsFolder().resolve(templateId).normalize();
        if (isValidWorldFolder(oneLevel)) return new TemplateLocation(key.toString(), oneLevel, key);
        return null;
    }

    /**
     * Paper 26.1+ expects dimensions/<namespace>/<key>. Move an older
     * one-level dimensions/<key> folder into that canonical location before
     * asking Bukkit to load it.
     */
    private TemplateLocation canonicalizeDimensionFolder(TemplateLocation location) throws IOException {
        if (location == null || location.dimensionKey() == null) return location;
        Path canonical = dimensionFolder(location.dimensionKey());
        if (location.folder().equals(canonical)) return location;
        if (Files.exists(canonical, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Both legacy and namespaced folders exist for template " + location.id());
        }
        Files.createDirectories(canonical.getParent());
        Files.move(location.folder(), canonical);
        plugin.getLogger().info("Migrated map template " + location.id() + " to Paper's namespaced dimension folder");
        return new TemplateLocation(location.id(), canonical, location.dimensionKey());
    }

    private Path dimensionsFolder() {
        World primary = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        Path levelFolder = primary == null ? worldContainer.resolve("world") : primary.getWorldFolder().toPath();
        return levelFolder.toAbsolutePath().normalize().resolve("dimensions").normalize();
    }

    private Path dimensionFolder(NamespacedKey key) {
        return dimensionsFolder().resolve(key.getNamespace()).resolve(key.getKey()).normalize();
    }

    private static NamespacedKey parseDimensionKey(String value) {
        try {
            return NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isSafeWorldPath(Path folder) {
        Path normalized = folder.toAbsolutePath().normalize();
        return normalized.startsWith(worldContainer) && !normalized.equals(worldContainer);
    }

    private boolean isSafeDestination(Path folder) {
        Path normalized = folder.toAbsolutePath().normalize();
        return normalized.startsWith(worldContainer) && !normalized.equals(worldContainer)
                && !normalized.equals(dimensionsFolder());
    }

    private static boolean isValidWorldFolder(Path folder) {
        return Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(folder)
                && Files.isRegularFile(folder.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS);
    }

    private LoadedMap requireTracked(MapInstance instance) {
        Objects.requireNonNull(instance, "instance");
        LoadedMap loaded = instances.get(instance.id());
        if (loaded == null || !loaded.instance().equals(instance)) {
            throw new IllegalArgumentException("Map instance is not managed by this MapManager");
        }
        return loaded;
    }

    private static void validateTemplateId(String templateId) {
        if (templateId == null || !(LEGACY_ID.matcher(templateId).matches()
                || templateId.matches("[a-z0-9_.-]{1,48}:[a-z0-9/._-]{1,48}"))) {
            throw new IllegalArgumentException("Invalid map template id; use a world name or namespace:key");
        }
    }

    private static void copyWorldFolder(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(directory)) throw new IOException("Symbolic links are not allowed in map templates: " + directory);
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) throw new IOException("Symbolic links are not allowed in map templates: " + file);
                String relative = source.relativize(file).toString().replace('\\', '/');
                String name = file.getFileName().toString();
                if (!name.equals("session.lock") && !name.equals("uid.dat")
                        && !relative.equals("data/paper/metadata.dat")) {
                    Files.copy(file, destination.resolve(source.relativize(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("MapManager must be called from the Paper server thread");
    }

    private record TemplateLocation(String id, Path folder, NamespacedKey dimensionKey) { }
    private record LoadedMap(MapInstance instance, Path folder, UUID worldUid) { }
}