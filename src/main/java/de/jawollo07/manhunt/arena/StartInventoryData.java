package de.jawollo07.manhunt.arena;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Versioned, Bukkit-aware serialization for an arena team's starting inventory. */
public final class StartInventoryData {
    private static final int VERSION = 1;

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack[] extra;

    public StartInventoryData(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra) {
        this.contents = copy(contents);
        this.armor = copy(armor);
        this.extra = copy(extra);
    }

    public ItemStack[] contents() { return copy(contents); }
    public ItemStack[] armor() { return copy(armor); }
    public ItemStack[] extra() { return copy(extra); }

    public String encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(VERSION);
            output.writeObject(contents);
            output.writeObject(armor);
            output.writeObject(extra);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static StartInventoryData decode(String encoded) throws IOException {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            int version = input.readInt();
            if (version != VERSION) throw new IOException("Unsupported start inventory version: " + version);
            return new StartInventoryData((ItemStack[]) input.readObject(),
                    (ItemStack[]) input.readObject(), (ItemStack[]) input.readObject());
        } catch (ClassNotFoundException | ClassCastException | IllegalArgumentException exception) {
            throw new IOException("Could not decode team start inventory", exception);
        }
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }
}
