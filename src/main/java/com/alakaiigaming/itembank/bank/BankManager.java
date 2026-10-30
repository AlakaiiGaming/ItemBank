package com.alakaiigaming.itembank.bank;

import com.alakaiigaming.itembank.ItemBank;
import com.alakaiigaming.itembank.Messages;
import com.alakaiigaming.itembank.market.Market;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BankManager {

    private final ItemBank plugin;
    private final File file;

    private final Map<UUID, Bank> banks = new LinkedHashMap<>();
    /** chest halves and signs -> bank */
    private final Map<String, Bank> byBlock = new HashMap<>();
    private final NamespacedKey tag;

    public BankManager(ItemBank plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "banks.yml");
        this.tag = new NamespacedKey(plugin, "bank");
    }

    /**
     * Finds this bank's display entity of one kind. Removes any extras (and untagged leftovers sitting
     * right on the spot), so there's never more than one.
     */
    private <T extends Entity> T own(Bank bank, Class<T> type, String kind, Location at) {
        String id = bank.id + ":" + kind;
        T keep = null;
        for (Entity e : at.getWorld().getNearbyEntities(at, 1.5, 2.5, 1.5)) {
            if (!type.isInstance(e)) continue;
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (id.equals(v) && keep == null && e.isValid()) {
                keep = type.cast(e);
            } else if (id.equals(v) || (v == null && e.getLocation().distanceSquared(at) < 0.01)) {
                e.remove();
            }
        }
        return keep;
    }

    public Collection<Bank> all() {
        return banks.values();
    }

    public Bank at(Block block) {
        return byBlock.get(Bank.key(block.getLocation()));
    }

    // create / remove

    public Bank create(Block chest, String marketKey, UUID owner) {
        Bank bank = new Bank(UUID.randomUUID(), marketKey, chest.getLocation());
        bank.owner = owner;
        bank.created = System.currentTimeMillis();
        bank.otherHalf = otherHalf(chest);
        add(bank);
        placeSign(bank);
        refresh(bank);
        save();
        return bank;
    }

    public void remove(Bank bank) {
        banks.remove(bank.id);
        byBlock.values().removeIf(b -> b == bank);
        removeDisplays(bank);
        if (bank.sign != null) {
            Block s = bank.sign.getBlock();
            if (s.getState() instanceof Sign) s.setType(Material.AIR);
        }
        save();
    }

    private void add(Bank bank) {
        banks.put(bank.id, bank);
        byBlock.put(Bank.key(bank.block), bank);
        if (bank.otherHalf != null) byBlock.put(Bank.key(bank.otherHalf), bank);
        if (bank.sign != null) byBlock.put(Bank.key(bank.sign), bank);
    }

    public static Location otherHalf(Block chest) {
        BlockState state = chest.getState();
        if (!(state instanceof Chest c)) return null;
        InventoryHolder holder = c.getInventory().getHolder();
        if (!(holder instanceof DoubleChest dc)) return null;
        for (InventoryHolder side : new InventoryHolder[]{dc.getLeftSide(), dc.getRightSide()}) {
            if (side instanceof Chest half && !half.getBlock().equals(chest)) return half.getLocation();
        }
        return null;
    }

    /** Re-checks a chest after a neighbor was placed, so a bank grown into a double chest stays whole. */
    public void recheckHalves(Bank bank) {
        Location other = otherHalf(bank.block.getBlock());
        if (other == null && bank.otherHalf == null) return;
        if (bank.otherHalf != null) byBlock.remove(Bank.key(bank.otherHalf));
        bank.otherHalf = other;
        if (other != null) byBlock.put(Bank.key(other), bank);
        save();
    }

    // display

    private boolean signs() {
        return "sign".equals(plugin.settings().displayType);
    }

    private boolean holograms() {
        return "hologram".equals(plugin.settings().displayType);
    }

    private void placeSign(Bank bank) {
        if (!signs()) return;
        Block chest = bank.block.getBlock();
        BlockData data = chest.getBlockData();
        if (!(data instanceof Directional d)) return;
        BlockFace face = d.getFacing();
        Block front = chest.getRelative(face);
        if (!front.getType().isAir()) return;
        front.setType(Material.OAK_WALL_SIGN, false);
        if (front.getBlockData() instanceof WallSign ws) {
            ws.setFacing(face);
            front.setBlockData(ws, false);
        }
        bank.sign = front.getLocation();
        byBlock.put(Bank.key(bank.sign), bank);
    }

    public void refreshMarket(String key) {
        for (Bank b : banks.values()) if (b.marketKey.equals(key)) refresh(b);
    }

    public void refreshAll() {
        for (Bank b : banks.values()) refresh(b);
    }

    public void refresh(Bank bank) {
        if (!bank.block.isWorldLoaded() || !bank.block.getWorld().isChunkLoaded(bank.block.getBlockX() >> 4, bank.block.getBlockZ() >> 4)) {
            return;
        }
        Market market = plugin.markets().get(bank.marketKey);
        if (market == null) return;

        if (signs() && bank.sign != null && bank.sign.getBlock().getState() instanceof Sign sign) {
            List<String> lines = plugin.settings().signLines;
            for (int i = 0; i < 4; i++) {
                String line = i < lines.size() ? fill(lines.get(i), bank, market) : "";
                sign.getSide(Side.FRONT).line(i, Messages.color(line));
            }
            sign.setWaxed(true);
            sign.update(true, false);
        }

        if (holograms()) {
            Component text = Component.empty();
            List<String> lines = plugin.settings().hologramLines;
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) text = text.append(Component.newline());
                text = text.append(Messages.color(fill(lines.get(i), bank, market)));
            }
            double up = plugin.settings().floatingItem
                    ? plugin.settings().floatingHeight + plugin.settings().floatingSize / 2 + 0.45 : 1.6;
            Location at = bank.above(up);
            TextDisplay existing = own(bank, TextDisplay.class, "text", at);
            if (existing != null) {
                existing.text(text);
                bank.hologram = existing.getUniqueId();
            } else {
                Component finalText = text;
                TextDisplay td = bank.block.getWorld().spawn(at, TextDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setBillboard(Display.Billboard.CENTER);
                    e.text(finalText);
                    e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, bank.id + ":text");
                });
                bank.hologram = td.getUniqueId();
            }
        } else {
            remove(bank.hologram);
            bank.hologram = null;
        }

        if (plugin.settings().floatingItem) {
            Location at = bank.above(plugin.settings().floatingHeight);
            ItemDisplay existing = own(bank, ItemDisplay.class, "item", at);
            if (existing != null) {
                bank.floating = existing.getUniqueId();
            } else {
                Material item = plugin.markets().itemOf(bank.marketKey);
                if (item != null) {
                    float size = plugin.settings().floatingSize;
                    ItemDisplay d = bank.block.getWorld().spawn(at, ItemDisplay.class, e -> {
                        e.setPersistent(false);
                        e.setItemStack(new ItemStack(item));
                        e.setTransformation(spin(0, size));
                        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, bank.id + ":item");
                    });
                    bank.floating = d.getUniqueId();
                }
            }
        } else {
            remove(bank.floating);
            bank.floating = null;
        }
    }

    private static Transformation spin(float angle, float size) {
        return new Transformation(new Vector3f(), new AxisAngle4f(angle, 0, 1, 0),
                new Vector3f(size, size, size), new AxisAngle4f());
    }

    private long spinTicks;

    /** Called every 10 ticks. Each step is short enough that the client animates it smoothly. */
    public void spinAll() {
        double seconds = plugin.settings().spinSeconds;
        if (!plugin.settings().floatingItem || seconds <= 0) return;
        spinTicks += 10;
        float angle = (float) ((spinTicks / 20.0 / seconds * Math.PI * 2) % (Math.PI * 2));
        float size = plugin.settings().floatingSize;
        for (Bank b : banks.values()) {
            if (b.floating == null) continue;
            if (Bukkit.getEntity(b.floating) instanceof ItemDisplay d) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(10);
                d.setTransformation(spin(angle, size));
            }
        }
    }

    private static void remove(UUID entity) {
        if (entity == null) return;
        Entity e = Bukkit.getEntity(entity);
        if (e != null) e.remove();
    }

    private String fill(String line, Bank bank, Market market) {
        String stock;
        if (bank.isServer()) {
            stock = plugin.settings().limitedStock ? String.valueOf(market.stock) : "∞";
        } else {
            stock = String.valueOf(plugin.trades().playerStock(bank));
        }
        return Messages.fill(line,
                "%material%", Messages.pretty(bank.marketKey),
                "%sell%", Messages.number(market.sellPrice()),
                "%buy%", Messages.number(market.buyPrice()),
                "%stock%", stock);
    }

    private void removeDisplays(Bank bank) {
        remove(bank.hologram);
        remove(bank.floating);
        bank.hologram = null;
        bank.floating = null;
    }

    public void chunkLoaded(Chunk chunk) {
        for (Bank b : banks.values()) {
            if (b.block.getWorld().equals(chunk.getWorld())
                    && b.block.getBlockX() >> 4 == chunk.getX() && b.block.getBlockZ() >> 4 == chunk.getZ()) {
                refresh(b);
            }
        }
    }

    /** Swaps display type after a reload: adds missing signs, rebuilds floating items and holograms. */
    public void redisplayAll() {
        for (Bank b : banks.values()) {
            removeDisplays(b);
            if (signs() && b.sign == null) placeSign(b);
            refresh(b);
        }
        save();
    }

    public void clearDisplays() {
        for (Bank b : banks.values()) removeDisplays(b);
    }

    // storage

    public void load() {
        banks.clear();
        byBlock.clear();
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = data.getConfigurationSection("banks");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            try {
                Location block = loc(c.getString("block"));
                if (block == null) {
                    plugin.getLogger().warning("Bank " + id + " is in a world that isn't loaded. Skipping it for now.");
                    continue;
                }
                Bank bank = new Bank(UUID.fromString(id), c.getString("material", ""), block);
                bank.otherHalf = loc(c.getString("other-half"));
                bank.sign = loc(c.getString("sign"));
                String owner = c.getString("owner");
                bank.owner = owner == null ? null : UUID.fromString(owner);
                bank.created = c.getLong("created", 0);
                read(c.getConfigurationSection("stats.total"), bank.total);
                String day = c.getString("stats.day");
                if (day != null && day.equals(java.time.LocalDate.now().toString())) read(c.getConfigurationSection("stats.today"), bank.today());
                bank.recent.addAll(c.getStringList("recent"));
                add(bank);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping bad bank entry " + id);
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Bank b : banks.values()) {
            String p = "banks." + b.id + ".";
            data.set(p + "material", b.marketKey);
            data.set(p + "block", str(b.block));
            data.set(p + "other-half", str(b.otherHalf));
            data.set(p + "sign", str(b.sign));
            data.set(p + "owner", b.owner == null ? null : b.owner.toString());
            if (b.created > 0) data.set(p + "created", b.created);
            write(data, p + "stats.total.", b.total);
            data.set(p + "stats.day", b.day.toString());
            write(data, p + "stats.today.", b.today());
            data.set(p + "recent", new ArrayList<>(b.recent));
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't save banks.yml: " + e.getMessage());
        }
    }

    private static void read(ConfigurationSection c, Bank.Stats s) {
        if (c == null) return;
        s.itemsIn = c.getLong("items-in");
        s.itemsOut = c.getLong("items-out");
        s.moneyOut = c.getDouble("money-out");
        s.moneyIn = c.getDouble("money-in");
        s.tax = c.getDouble("tax");
    }

    private static void write(YamlConfiguration data, String p, Bank.Stats s) {
        data.set(p + "items-in", s.itemsIn);
        data.set(p + "items-out", s.itemsOut);
        data.set(p + "money-out", s.moneyOut);
        data.set(p + "money-in", s.moneyIn);
        data.set(p + "tax", s.tax);
    }

    public List<Bank> ownedBy(UUID owner) {
        List<Bank> out = new ArrayList<>();
        for (Bank b : banks.values()) if (java.util.Objects.equals(b.owner, owner)) out.add(b);
        return out;
    }

    /** By the short id shown in /ibank list. */
    public Bank byShortId(String id) {
        for (Bank b : banks.values()) if (b.shortId().equalsIgnoreCase(id) || b.id.toString().equalsIgnoreCase(id)) return b;
        return null;
    }

    private static String str(Location l) {
        return l == null ? null : l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static Location loc(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length != 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    public List<Bank> ofMarket(String key) {
        List<Bank> out = new ArrayList<>();
        for (Bank b : banks.values()) if (b.marketKey.equals(key)) out.add(b);
        return out;
    }
}
