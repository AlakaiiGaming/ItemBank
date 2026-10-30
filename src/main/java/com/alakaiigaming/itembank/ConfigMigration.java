package com.alakaiigaming.itembank;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

/** Moves configs made by earlier test builds to the balanced defaults. Only changes values nobody edited. */
final class ConfigMigration {

    private ConfigMigration() {
    }

    /** material -> {old base, new base} */
    private static final Map<String, double[]> BASES = Map.ofEntries(
            Map.entry("coal", new double[]{2, 0.5}),
            Map.entry("copper_ingot", new double[]{2, 0.25}),
            Map.entry("redstone", new double[]{1.5, 0.4}),
            Map.entry("lapis_lazuli", new double[]{3, 0.75}),
            Map.entry("quartz", new double[]{2, 0.4}),
            Map.entry("amethyst_shard", new double[]{4, 0.5}),
            Map.entry("iron_ingot", new double[]{8, 2}),
            Map.entry("gold_ingot", new double[]{20, 4}),
            Map.entry("emerald", new double[]{40, 8}),
            Map.entry("diamond", new double[]{100, 30}),
            Map.entry("netherite_ingot", new double[]{800, 400}));

    static void run(ItemBank plugin) {
        FileConfiguration c = plugin.getConfig();
        if (!c.isSet("materials")) return;   // fresh config, nothing to move
        if (c.getInt("config-version", 1) < 2) toTwo(plugin, c);
        if (c.getInt("config-version", 1) < 3) toThree(plugin, c);
        if (c.getInt("config-version", 1) < 4) toFour(plugin, c);
    }

    /** Stock settling, per-player inflation, index limits, a looser everyone cap. */
    private static void toFour(ItemBank plugin, FileConfiguration c) {
        org.bukkit.configuration.Configuration defaults = c.getDefaults();
        if (defaults instanceof ConfigurationSection d) {
            copySection(c, d, "stock-settling");
            if (!c.isSet("inflation.active-days")) {
                c.set("inflation.active-days", d.get("inflation.active-days"));
                c.setComments("inflation.active-days", d.getComments("inflation.active-days"));
            }
            if (!c.isSet("inflation.index-limits")) {
                c.set("inflation.index-limits.min", d.get("inflation.index-limits.min"));
                c.set("inflation.index-limits.max", d.get("inflation.index-limits.max"));
                c.setComments("inflation.index-limits", d.getComments("inflation.index-limits"));
            }
            c.setComments("inflation", d.getComments("inflation"));
            c.setComments("sell-off-protection.everyone", d.getComments("sell-off-protection.everyone"));
            c.setComments("sell-off-protection.everyone.max-percent", d.getComments("sell-off-protection.everyone.max-percent"));
            if (c.getStringList("messages.stats").stream().noneMatch(l -> l.contains("%players%"))) {
                c.set("messages.stats", d.getStringList("messages.stats"));
            }
        }
        if (c.getDouble("sell-off-protection.everyone.max-percent") == 75) c.set("sell-off-protection.everyone.max-percent", 200);
        c.set("config-version", 4);
        plugin.getLogger().info("Added stock settling and inflation limits to config.yml.");
    }

    private static void copySection(FileConfiguration c, ConfigurationSection d, String section) {
        if (c.isSet(section)) return;
        c.set(section, null);
        c.setComments(section, d.getComments(section));
        for (String key : d.getConfigurationSection(section).getKeys(true)) {
            String path = section + "." + key;
            if (d.isConfigurationSection(path)) continue;
            c.set(path, d.get(path));
            c.setComments(path, d.getComments(path));
            c.setInlineComments(path, d.getInlineComments(path));
        }
    }

    /** Adds the tax and player bank sections with their comments. */
    private static void toThree(ItemBank plugin, FileConfiguration c) {
        org.bukkit.configuration.Configuration defaults = c.getDefaults();
        if (defaults instanceof ConfigurationSection d) {
            for (String section : new String[]{"tax", "player-banks"}) {
                if (c.isSet(section)) continue;
                c.set(section, null);
                c.setComments(section, d.getComments(section));
                for (String key : d.getConfigurationSection(section).getKeys(true)) {
                    String path = section + "." + key;
                    if (d.isConfigurationSection(path)) continue;
                    c.set(path, d.get(path));
                    c.setInlineComments(path, d.getInlineComments(path));
                }
            }
            if ("&e/ibank prices &7| &e/ibank create <material> [player] &7| &e/ibank remove".equals(c.getString("messages.usage"))) {
                c.set("messages.usage", d.getString("messages.usage"));
            }
            c.setComments("inflation", d.getComments("inflation"));
            c.setInlineComments("inflation.max-change-percent", d.getInlineComments("inflation.max-change-percent"));
            c.setInlineComments("inflation.compare-days", d.getInlineComments("inflation.compare-days"));
        }
        java.util.List<String> info = new java.util.ArrayList<>(c.getStringList("messages.menu-info"));
        if (!info.isEmpty() && info.stream().noneMatch(l -> l.contains("%tax%"))) {
            info.add("&7Tax: &f%tax%");
            c.set("messages.menu-info", info);
        }
        c.set("config-version", 3);
        plugin.getLogger().info("Added tax and player bank settings to config.yml.");
    }

    private static void toTwo(ItemBank plugin, FileConfiguration c) {
        if (c.getDouble("price-limits.max") == 4) c.set("price-limits.max", 2.0);
        c.set("sell-off-protection.per-player.max-payout", null);
        if (c.getDouble("sell-off-protection.per-player.max-payout-per-material") == 6000) {
            c.set("sell-off-protection.per-player.max-payout-per-material", 0);
        }
        double everyone = c.getDouble("sell-off-protection.everyone.max-percent");
        if (everyone == 50 || everyone == 100) c.set("sell-off-protection.everyone.max-percent", 75);
        if (c.getLong("sell-off-protection.everyone.close-minutes") == 120) c.set("sell-off-protection.everyone.close-minutes", 60);

        ConfigurationSection mats = c.getConfigurationSection("materials");
        if (mats != null) {
            for (String key : mats.getKeys(false)) {
                double[] b = BASES.get(key);
                ConfigurationSection m = mats.getConfigurationSection(key);
                if (b == null || m == null || m.getDouble("base") != b[0]) continue;
                m.set("base", b[1]);
                m.set("step", m.getLong("normal-stock"));
                if (key.equals("netherite_ingot") && m.getDouble("max-payout") == 20000) m.set("max-payout", null);
            }
        }
        c.set("config-version", 2);
        plugin.getLogger().info("Updated config.yml to the balanced prices and sell limits.");
    }
}
