package com.alakaiigaming.itembank.market;

import com.alakaiigaming.itembank.ItemBank;
import com.alakaiigaming.itembank.Settings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MarketManager implements PriceRules {

    private final ItemBank plugin;
    private final File file;

    private final Map<String, Market> markets = new LinkedHashMap<>();
    private final Map<Material, String> itemToKey = new HashMap<>();
    private final Map<String, Material> items = new HashMap<>();
    private final Map<String, Material> blocks = new HashMap<>();

    private double index = 1;
    private final Inflation inflation = new Inflation();
    private SellGuard guard;
    private LocalDate lastUpdate;
    /** All tax ever collected (paid to the receiver or burned). */
    public double taxCollected;

    /** player -> material -> {sold today, bought today} */
    private final Map<UUID, Map<String, long[]>> traded = new HashMap<>();

    public MarketManager(ItemBank plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "markets.yml");
    }

    private Settings s() {
        return plugin.settings();
    }

    public SellGuard guard() {
        if (guard == null) guard = new SellGuard(s().guard);
        return guard;
    }

    // PriceRules

    @Override public double spread() { return s().spread; }
    @Override public double minMultiplier() { return s().minMultiplier; }
    @Override public double maxMultiplier() { return s().maxMultiplier; }
    @Override public boolean curve() { return s().curve; }
    @Override public double index() { return index; }

    public void load() {
        YamlConfiguration data = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        Map<String, Market> old = new HashMap<>(markets);
        markets.clear();
        itemToKey.clear();
        items.clear();
        blocks.clear();

        ConfigurationSection mats = plugin.getConfig().getConfigurationSection("materials");
        if (mats != null) {
            for (String raw : mats.getKeys(false)) {
                String key = raw.toLowerCase(Locale.ROOT);
                Material item = Material.matchMaterial(key);
                if (item == null || !item.isItem()) {
                    plugin.getLogger().warning("Unknown material in config: " + raw);
                    continue;
                }
                ConfigurationSection m = mats.getConfigurationSection(raw);
                if (m == null) continue;
                long normal = Math.max(1, m.getLong("normal-stock", 100));
                long start = Math.max(0, m.getLong("start-stock", 0));
                Market market = new Market(key, Math.max(0.0001, m.getDouble("base", 1)), normal,
                        m.getDouble("step", normal / 2.0), m.getLong("max-stock", normal * 4), start, this);

                if (m.isSet("max-payout")) market.payoutCap = Math.max(0, m.getDouble("max-payout"));
                Market previous = old.get(key);
                if (previous != null) {
                    market.stock = previous.stock;
                    market.frozen = previous.frozen;
                    market.frozenPrice = previous.frozenPrice;
                    market.disabled = previous.disabled;
                    market.lastDailyPrice = previous.lastDailyPrice;
                    market.carry = previous.carry;
                } else if (data.isConfigurationSection("markets." + key)) {
                    ConfigurationSection d = data.getConfigurationSection("markets." + key);
                    market.stock = d.getLong("stock", start);
                    market.frozen = d.getBoolean("frozen", false);
                    market.frozenPrice = d.getDouble("frozen-price", Double.NaN);
                    market.disabled = d.getBoolean("disabled", false);
                    market.lastDailyPrice = d.getDouble("last-daily-price", Double.NaN);
                }
                markets.put(key, market);
                items.put(key, item);
                itemToKey.put(item, key);

                String blockName = m.getString("block");
                if (blockName != null) {
                    Material block = Material.matchMaterial(blockName);
                    if (block == null || !block.isItem()) {
                        plugin.getLogger().warning("Unknown block for " + key + ": " + blockName);
                    } else {
                        blocks.put(key, block);
                        itemToKey.put(block, key);
                    }
                }
            }
        }

        if (old.isEmpty()) {
            index = data.getDouble("index", 1);
            inflation.issued = data.getDouble("issued", 0);
            inflation.history.clear();
            for (Double d : data.getDoubleList("supply-history")) inflation.history.add(d);
            inflation.baseline = data.getDouble("supply-baseline", Double.NaN);
            inflation.lastTotal = data.getDouble("last-money-supply", Double.NaN);
            inflation.lastPlayers = data.getInt("last-active-players", 0);
            taxCollected = data.getDouble("tax-collected", 0);
            ConfigurationSection closed = data.getConfigurationSection("closed-until");
            if (closed != null) {
                for (String k : closed.getKeys(false)) guard().closedUntil.put(k, closed.getLong(k));
            }
            String last = data.getString("last-update");
            lastUpdate = last == null ? LocalDate.now() : LocalDate.parse(last);
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("index", index);
        data.set("issued", inflation.issued);
        data.set("supply-history", inflation.history);
        if (!Double.isNaN(inflation.baseline)) data.set("supply-baseline", inflation.baseline);
        if (!Double.isNaN(inflation.lastTotal)) data.set("last-money-supply", inflation.lastTotal);
        data.set("last-active-players", inflation.lastPlayers);
        data.set("tax-collected", taxCollected);
        data.set("last-update", lastUpdate == null ? null : lastUpdate.toString());
        for (Map.Entry<String, Long> e : guard().closedUntil.entrySet()) data.set("closed-until." + e.getKey(), e.getValue());
        for (Market m : markets.values()) {
            String p = "markets." + m.key + ".";
            data.set(p + "stock", m.stock);
            data.set(p + "frozen", m.frozen);
            if (!Double.isNaN(m.frozenPrice)) data.set(p + "frozen-price", m.frozenPrice);
            data.set(p + "disabled", m.disabled);
            if (!Double.isNaN(m.lastDailyPrice)) data.set(p + "last-daily-price", m.lastDailyPrice);
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't save markets.yml: " + e.getMessage());
        }
    }

    public Market get(String key) {
        return key == null ? null : markets.get(key.toLowerCase(Locale.ROOT));
    }

    public Map<String, Market> all() {
        return markets;
    }

    public Material itemOf(String key) {
        return items.get(key);
    }

    /** The 9-to-1 block for this material, or null. */
    public Material blockOf(String key) {
        return blocks.get(key);
    }

    /** Money this plugin just created (negative = removed). Kept out of the inflation check. */
    public void recordCreated(double amount) {
        inflation.record(amount);
    }

    public Inflation inflation() {
        return inflation;
    }

    /** Total money and player count. */
    public record Supply(double total, int players) {}

    /** Adds up the balances of players seen in the last active-days. Can take a moment on big servers. */
    public Supply measureMoneySupply() {
        if (plugin.money() == null) return new Supply(Double.NaN, 0);
        long since = System.currentTimeMillis() - s().activeDays * 86_400_000L;
        double total = 0;
        int players = 0;
        for (org.bukkit.OfflinePlayer p : plugin.getServer().getOfflinePlayers()) {
            try {
                if (!p.isOnline() && p.getLastSeen() < since) continue;
                double b = plugin.money().balance(p);
                if (Double.isNaN(b)) continue;
                total += b;
                players++;
            } catch (RuntimeException ignored) {
            }
        }
        return new Supply(total, players);
    }

    /** Makes today's money supply the new normal for today's prices. */
    public void rebaseInflation() {
        Supply supply = measureMoneySupply();
        if (!Double.isNaN(supply.total())) {
            inflation.lastTotal = supply.total();
            inflation.lastPlayers = supply.players();
        }
        inflation.rebase(index);
        save();
    }

    public void setIndex(double value) {
        index = Math.max(s().minIndex, Math.min(s().maxIndex, value));
        inflation.rebase(index);
        for (Market m : markets.values()) m.lastDailyPrice = m.price();
        save();
        plugin.banks().refreshAll();
    }

    // daily limits

    public long tradedToday(UUID player, String key, boolean selling) {
        long[] t = traded.getOrDefault(player, Map.of()).get(key);
        return t == null ? 0 : t[selling ? 0 : 1];
    }

    public void addTraded(UUID player, String key, boolean selling, long amount) {
        traded.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(key, k -> new long[2])[selling ? 0 : 1] += amount;
    }

    // daily update

    /** Runs stock settling and the daily update if it's due. Called every minute. */
    public void tick() {
        if (s().limitedStock) {
            for (Market m : markets.values()) {
                if (m.settle(s().settleSurplus, s().settleShortage) != 0) plugin.banks().refreshMarket(m.key);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (lastUpdate == null) lastUpdate = now.toLocalDate();
        if (now.toLocalDate().isAfter(lastUpdate) && !now.toLocalTime().isBefore(s().updateTime)) {
            dailyUpdate();
        }
    }

    /** Returns the price change applied, e.g. 0.012 for +1.2%. */
    public double dailyUpdate() {
        lastUpdate = LocalDate.now();
        traded.clear();

        double factor = 1;
        Supply supply = measureMoneySupply();
        double total = supply.total();
        if (!Double.isNaN(total)) {
            if (s().inflation) {
                factor = inflation.dailyFactor(total, supply.players(), s().compareDays, s().maxDailyChange, index);
                double next = Math.max(s().minIndex, Math.min(s().maxIndex, index * factor));
                factor = next / index;
                index = next;
            } else {
                inflation.lastTotal = total;
                inflation.lastPlayers = supply.players();
            }
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "Daily check: %,.2f money across %d active players (%+.1f%% per player over %d days, not counting banks). Prices %+.2f%%, index %.3f.",
                    total, supply.players(), inflation.supplyChange() * 100, Math.max(0, inflation.history.size() - 1), (factor - 1) * 100, index));
            if (total - inflation.issued <= 0) {
                plugin.getLogger().warning("Banks have paid out more money than players hold, so inflation can't be measured. "
                        + "Run /ibank inflation reset to treat today's money as normal.");
            }
        }
        for (Market m : markets.values()) m.lastDailyPrice = m.price();
        save();
        plugin.getServer().getPluginManager().callEvent(
                new com.alakaiigaming.itembank.api.DailyUpdateEvent(total, factor, index));
        plugin.banks().refreshAll();
        return factor - 1;
    }

    public List<String> keys() {
        return List.copyOf(markets.keySet());
    }
}
