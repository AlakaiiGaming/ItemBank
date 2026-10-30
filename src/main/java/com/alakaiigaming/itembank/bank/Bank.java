package com.alakaiigaming.itembank.bank;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public final class Bank {

    public final UUID id;
    public final String marketKey;
    public final Location block;
    /** Other half of a double chest, or null. */
    public Location otherHalf;
    /** null = server bank */
    public UUID owner;
    public Location sign;
    /** Not saved: the hologram and floating item entities, if any. */
    public UUID hologram;
    public UUID floating;

    /** When the bank was made, millis. 0 if unknown. */
    public long created;
    public final Stats total = new Stats();
    private final Stats today = new Stats();
    public java.time.LocalDate day = java.time.LocalDate.now();
    /** Last few trades, newest last. */
    public final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
    public static final int RECENT = 10;

    /** Running totals for the owner's records. "in" and "out" are from the bank's side. */
    public static final class Stats {
        public long itemsIn;      // players sold to the bank
        public long itemsOut;     // players bought from the bank
        public double moneyOut;   // bank paid sellers (before tax)
        public double moneyIn;    // bank got from buyers (before tax)
        public double tax;

        public void clear() {
            itemsIn = itemsOut = 0;
            moneyOut = moneyIn = tax = 0;
        }
    }

    public Stats today() {
        java.time.LocalDate now = java.time.LocalDate.now();
        if (!now.equals(day)) {
            day = now;
            today.clear();
        }
        return today;
    }

    public void addSale(long items, double money, double tax) {
        for (Stats s : new Stats[]{total, today()}) {
            s.itemsIn += items;
            s.moneyOut += money;
            s.tax += tax;
        }
    }

    public void addPurchase(long items, double money, double tax) {
        for (Stats s : new Stats[]{total, today()}) {
            s.itemsOut += items;
            s.moneyIn += money;
            s.tax += tax;
        }
    }

    public void addRecent(String line) {
        recent.addLast(line);
        while (recent.size() > RECENT) recent.removeFirst();
    }

    /** First part of the id, for commands. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public Bank(UUID id, String marketKey, Location block) {
        this.id = id;
        this.marketKey = marketKey;
        this.block = block;
    }

    public boolean isServer() {
        return owner == null;
    }

    /** The chest's inventory (both halves for a double chest), or null if the chest is gone. */
    public Inventory inventory() {
        Block b = block.getBlock();
        BlockState state = b.getState();
        return state instanceof Container c ? c.getInventory() : null;
    }

    /** Middle of the chest, {@code up} blocks above its base. */
    public Location above(double up) {
        Location a = block.clone().add(0.5, 0, 0.5);
        if (otherHalf != null) {
            Location b = otherHalf.clone().add(0.5, 0, 0.5);
            a = a.add(b).multiply(0.5);
        }
        a.setYaw(0);
        a.setPitch(0);
        return a.add(0, up, 0);
    }

    public static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }
}
