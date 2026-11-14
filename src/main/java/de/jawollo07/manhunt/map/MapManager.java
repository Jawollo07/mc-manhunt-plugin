package de.jawollo07.manhunt.map;

import org.bukkit.Bukkit;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Creates isolated, disposable copies of map templates.
 *
 * All public methods must be invoked on the Paper server thread because world
 * creation, lookup and unloading are Bukkit operations. File copying/deletion
 * is deliberately performed in the same call so a partially copied world can
 * never be loaded by another task.
 */
public final class MapManager {
    private static final Pattern TEMPLATE_ID = Pattern.compile("[a-zA-Z0-9_-]{1,48}");

    private final JavaPlugin plugin;
    private final Path worldContainer;
    private final Map<UUID, LoadedMap> instances = new ConcurrentHashMap<>();

    public MapManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
    }

    /**
     * Copies a world folder from the server world container and loads the copy.
     * The template must be an unloaded world folder, not the currently active
     * default world or another loaded world.
     */
    public MapInstance createWorkingCopy(String templateId) throws IOException {
        requireServerThread();
        validateTemplateId(templateId);

        Path template = worldContainer.resolve(templateId).normalize();
        if (!template.getParent().equals(worldContainer)) {
            throw new IllegalArgumentException("Template path is outside the world container");
        }
        if (!Files.isDirectory(template, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(template)) {
            throw new IllegalArgumentException("Map template does not exist or is not a directory: " + templateId);
        }
        if (Bukkit.getWorld(templateId) != null) {
            throw new IllegalStateException("Map template must be unloaded before it can be copied: " + templateId);
        }
        if (!Files.isRegularFile(template.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Map template is missing level.dat: " + templateId);
        }

        UUID instanceId = UUID.randomUUID();
        String worldName = "manhunt_" + templateId.toLowerCase(Locale.ROOT) + "_"
                + instanceId.toString().replace("-", "").substring(0, 12);
        Path destination = worldContainer.resolve(worldName).normalize();
        if (!destination.getParent().equals(worldContainer) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Temporary world destination already exists or is invalid: " + worldName);
        }

        copyWorldFolder(template, destination);
        boolean loaded = false;
        try {
            World world = Bukkit.createWorld(new WorldCreator(worldName));
            if (world == null) {
                throw new IOException("Paper could not load temporary world: " + worldName);
            }
            loaded = true;
            MapInstance instance = new MapInstance(instanceId, templateId, worldName, Instant.now());
            instances.put(instanceId, new LoadedMap(instance, destination, world.getUID()));
            plugin.getLogger().info("Created temporary map " + worldName + " from template " + templateId);
            return instance;
        } catch (RuntimeException | IOException exception) {
            if (loaded) {
                Bukkit.unloadWorld(worldName, false);
            }
            deleteRecursively(destination);
            throw exception;
        }
    }

    public World getWorld(MapInstance instance) {
        requireServerThread();
        LoadedMap loaded = requireTracked(instance);
        World world = Bukkit.getWorld(loaded.worldUid());
        if (world == null) {
            throw new IllegalStateException("Temporary map is no longer loaded: " + instance.worldName());
        }
        return world;
    }

    /**
     * Unloads and deletes a managed temporary world. Refuses to delete while
     * players are still inside it; callers must restore/teleport them first.
     */
    public void deleteWorkingCopy(MapInstance instance) throws IOException {
        requireServerThread();
        LoadedMap loaded = requireTracked(instance);
        World world = Bukkit.getWorld(loaded.worldUid());
        if (world != null) {
            if (!world.getPlayers().isEmpty()) {
                throw new IllegalStateException("Cannot delete temporary map while players are still inside it");
            }
            if (!Bukkit.unloadWorld(world, true)) {
                throw new IllegalStateException("Paper refused to unload temporary world: " + instance.worldName());
            }
        }

        deleteRecursively(loaded.folder());
        instances.remove(instance.id(), loaded);
        plugin.getLogger().info("Deleted temporary map " + instance.worldName());
    }

    public int getActiveInstanceCount() {
        return instances.size();
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
        if (templateId == null || !TEMPLATE_ID.matcher(templateId).matches()
                || templateId.equals(".") || templateId.equals("..")) {
            throw new IllegalArgumentException("Invalid map template id");
        }
    }

    private static void copyWorldFolder(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Symbolic links are not allowed in map templates: " + directory);
                }
                Path target = destination.resolve(source.relativize(directory));
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Symbolic links are not allowed in map templates: " + file);
                }
                String name = file.getFileName().toString();
                // These files identify/lock the original world folder and must
                // not be reused by its disposable copy.
                if (!name.equals("session.lock") && !name.equals("uid.dat")) {
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
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MapManager must be called from the Paper server thread");
        }
    }

    private record LoadedMap(MapInstance instance, Path folder, UUID worldUid) { }
}
