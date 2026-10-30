package com.alakaiigaming.itembank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemBank plugin;
    /** Last message sent to each player, so the bank menu can show it (chat is hidden while a menu is open). */
    private final java.util.Map<java.util.UUID, String> last = new java.util.HashMap<>();

    Messages(ItemBank plugin) {
        this.plugin = plugin;
    }

    public static Component color(String text) {
        Component component = LEGACY.deserialize(text == null ? "" : text);
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public static String fill(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    public void send(CommandSender to, String key, String... replacements) {
        String text = plugin.getConfig().getString("messages." + key);
        if (text == null || text.isEmpty()) return;
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String filled = fill(text, replacements);
        if (to instanceof org.bukkit.entity.Player p) last.put(p.getUniqueId(), filled);
        to.sendMessage(color(prefix + filled));
    }

    /** Takes (and forgets) the last message sent to a player, or null. */
    public String takeLast(java.util.UUID player) {
        return last.remove(player);
    }

    /** iron_ingot -> Iron Ingot */
    public static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String part : key.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public static String pretty(Material material) {
        return pretty(material.getKey().getKey());
    }

    public static String number(double v) {
        return String.format(Locale.ROOT, "%,.2f", v);
    }
}
