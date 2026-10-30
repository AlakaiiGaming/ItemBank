package com.alakaiigaming.itembank;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Settings {

    public static final int ALL = -1;

    public boolean limitedStock;
    public double spread;
    public boolean curve;
    public double minMultiplier;
    public double maxMultiplier;

    public boolean inflation;
    public double maxDailyChange;
    public int compareDays;
    public LocalTime updateTime;
    public int activeDays;
    public double minIndex;
    public double maxIndex;

    /** Share of the stock gap closed per minute. */
    public double settleSurplus;
    public double settleShortage;

    public long sellLimit;
    public long buyLimit;

    public final com.alakaiigaming.itembank.market.SellGuard.Rules guard = new com.alakaiigaming.itembank.market.SellGuard.Rules();
    public boolean announceClose;

    public final List<Integer> menuAmounts = new ArrayList<>();

    public boolean floatingItem;
    public double floatingHeight;
    public float floatingSize;
    public double spinSeconds;

    public final com.alakaiigaming.itembank.market.Tax tax = new com.alakaiigaming.itembank.market.Tax();

    public int maxPlayerBanks;
    public double createCost;
    public boolean checkProtection;
    public List<String> playerBankMaterials;

    public String displayType;
    public List<String> signLines;
    public List<String> hologramLines;

    void load(FileConfiguration c, java.util.logging.Logger log) {
        limitedStock = !"unlimited".equalsIgnoreCase(c.getString("stock", "limited"));
        spread = clamp(c.getDouble("spread-percent", 12), 0, 100) / 100.0;
        curve = !"flat".equalsIgnoreCase(c.getString("bulk-pricing", "curve"));
        minMultiplier = Math.max(0, c.getDouble("price-limits.min", 0.25));
        maxMultiplier = Math.max(minMultiplier, c.getDouble("price-limits.max", 2.0));

        inflation = c.getBoolean("inflation.enabled", true);
        maxDailyChange = clamp(c.getDouble("inflation.max-change-percent", 3), 0, 100) / 100.0;
        compareDays = Math.max(1, c.getInt("inflation.compare-days", 7));
        try {
            updateTime = LocalTime.parse(c.getString("inflation.update-time", "00:00"));
        } catch (Exception e) {
            log.warning("inflation.update-time isn't a valid time (use HH:mm). Using 00:00.");
            updateTime = LocalTime.MIDNIGHT;
        }

        activeDays = Math.max(1, c.getInt("inflation.active-days", 30));
        minIndex = Math.max(0.01, c.getDouble("inflation.index-limits.min", 0.5));
        maxIndex = Math.max(minIndex, c.getDouble("inflation.index-limits.max", 10));

        settleSurplus = perMinute(c.getDouble("stock-settling.sell-surplus-percent", 50));
        settleShortage = perMinute(c.getDouble("stock-settling.restock-percent", 0));

        sellLimit = Math.max(0, c.getLong("limits.sell", 0));
        buyLimit = Math.max(0, c.getLong("limits.buy", 0));

        guard.enabled = c.getBoolean("sell-off-protection.enabled", true);
        guard.windowMillis = Math.max(1, c.getLong("sell-off-protection.window-minutes", 60)) * 60_000L;
        guard.playerMaxPayout = Math.max(0, c.getDouble("sell-off-protection.per-player.max-payout-per-material", 0));
        guard.playerItemsPercent = Math.max(0, c.getDouble("sell-off-protection.per-player.max-items-percent", 25));
        guard.playerMaxItems = Math.max(0, c.getLong("sell-off-protection.per-player.max-items", 0));
        guard.everyonePercent = Math.max(0, c.getDouble("sell-off-protection.everyone.max-percent", 75));
        guard.closeMillis = Math.max(1, c.getLong("sell-off-protection.everyone.close-minutes", 60)) * 60_000L;
        announceClose = c.getBoolean("sell-off-protection.everyone.announce", true);

        tax.enabled = c.getBoolean("tax.enabled", false);
        tax.maxRate = clamp(c.getDouble("tax.max-percent", 50), 0, 100) / 100.0;
        tax.sellRate = tax.clampRate(c.getDouble("tax.sell-percent", 5) / 100.0);
        tax.buyRate = tax.clampRate(c.getDouble("tax.buy-percent", 5) / 100.0);
        tax.playerBanks = c.getBoolean("tax.player-banks", true);
        tax.receiver = player(c.getString("tax.receiver", "none"), log);

        maxPlayerBanks = c.getInt("player-banks.max-per-player", 3);
        createCost = Math.max(0, c.getDouble("player-banks.create-cost", 0));
        checkProtection = c.getBoolean("player-banks.check-protection", true);
        playerBankMaterials = new ArrayList<>();
        for (String m : c.getStringList("player-banks.materials")) playerBankMaterials.add(m.toLowerCase(Locale.ROOT));

        menuAmounts.clear();
        for (Object o : c.getList("menu-amounts", List.of(1, 16, 64, "all"))) {
            String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
            if (s.equals("all")) {
                menuAmounts.add(ALL);
            } else {
                try {
                    int n = Integer.parseInt(s);
                    if (n > 0) menuAmounts.add(n);
                } catch (NumberFormatException e) {
                    log.warning("Skipping menu amount '" + s + "'.");
                }
            }
            if (menuAmounts.size() == 4) break;
        }
        if (menuAmounts.isEmpty()) menuAmounts.addAll(List.of(1, 16, 64, ALL));

        displayType = c.getString("display.type", "sign").toLowerCase(Locale.ROOT);
        floatingItem = c.getBoolean("display.floating-item.enabled", true);
        floatingHeight = c.getDouble("display.floating-item.height", 1.5);
        floatingSize = (float) Math.max(0.1, c.getDouble("display.floating-item.size", 0.6));
        spinSeconds = c.getDouble("display.floating-item.seconds-per-spin", 4);   // 0 = no spin
        signLines = c.getStringList("display.lines");
        hologramLines = c.getStringList("display.hologram-lines");
    }

    /** A player name or UUID from the config. null for none or unknown. */
    @SuppressWarnings("deprecation")
    static java.util.UUID player(String value, java.util.logging.Logger log) {
        if (value == null) return null;
        value = value.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("none")) return null;
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }
        org.bukkit.OfflinePlayer p = org.bukkit.Bukkit.getOfflinePlayerIfCached(value);
        if (p == null) {
            log.warning("tax.receiver: no player called " + value + " has joined. Tax won't go to anyone until they do.");
            return null;
        }
        return p.getUniqueId();
    }

    /** Turns "x% of the gap per day" into the share to close each minute. */
    private static double perMinute(double percentPerDay) {
        double p = clamp(percentPerDay, 0, 100) / 100.0;
        if (p >= 1) return 1;
        return 1 - Math.pow(1 - p, 1.0 / 1440);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
