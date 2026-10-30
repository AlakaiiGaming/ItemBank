package com.alakaiigaming.itembank.bank;

import com.alakaiigaming.itembank.ItemBank;
import com.alakaiigaming.itembank.Messages;
import com.alakaiigaming.itembank.Settings;
import com.alakaiigaming.itembank.market.Market;
import com.alakaiigaming.itembank.api.TaxCollectedEvent;
import com.alakaiigaming.itembank.market.SellGuard;
import com.alakaiigaming.itembank.market.Tax;
import com.alakaiigaming.itembank.money.Money;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class TradeService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ItemBank plugin;
    private final File log;

    public TradeService(ItemBank plugin) {
        this.plugin = plugin;
        this.log = new File(plugin.getDataFolder(), "transactions.log");
    }

    private Settings s() {
        return plugin.settings();
    }

    public long playerStock(Bank bank) {
        Inventory inv = bank.inventory();
        if (inv == null) return 0;
        return Items.count(inv, plugin.markets().itemOf(bank.marketKey), plugin.markets().blockOf(bank.marketKey));
    }

    /** How many the player could sell right now, for the menu. */
    public long sellable(Player p, Bank bank, int requested) {
        Market m = plugin.markets().get(bank.marketKey);
        Material item = plugin.markets().itemOf(bank.marketKey);
        long n = Items.count(p.getInventory(), item, plugin.markets().blockOf(bank.marketKey));
        if (requested != Settings.ALL) n = Math.min(n, requested);
        n = Math.min(n, limitLeft(p, bank.marketKey, true));
        if (bank.isServer()) {
            n = Math.min(n, m.room());
            n = plugin.markets().guard().allowed(p.getUniqueId(), m, n, System.currentTimeMillis());
        } else {
            Inventory inv = bank.inventory();
            n = inv == null ? 0 : Math.min(n, Items.room(inv, item));
        }
        return Math.max(0, n);
    }

    /** How many the player could buy right now (before money), for the menu. */
    public long buyable(Player p, Bank bank, int requested) {
        Market m = plugin.markets().get(bank.marketKey);
        Material item = plugin.markets().itemOf(bank.marketKey);
        long n = Items.room(p.getInventory(), item);
        if (requested != Settings.ALL) n = Math.min(n, requested);
        n = Math.min(n, limitLeft(p, bank.marketKey, false));
        long available = bank.isServer() ? (s().limitedStock ? m.stock : Long.MAX_VALUE) : playerStock(bank);
        return Math.max(0, Math.min(n, available));
    }

    private long limitLeft(Player p, String key, boolean selling) {
        long limit = selling ? s().sellLimit : s().buyLimit;
        if (limit <= 0 || p.hasPermission("itembank.bypass.limit")) return Long.MAX_VALUE;
        return Math.max(0, limit - plugin.markets().tradedToday(p.getUniqueId(), key, selling));
    }

    public void sell(Player p, Bank bank, int requested) {
        Money eco = plugin.money();
        Messages msg = plugin.messages();
        Market m = plugin.markets().get(bank.marketKey);
        if (eco == null) { msg.send(p, "no-economy"); return; }
        if (m == null) return;
        String name = Messages.pretty(bank.marketKey);
        if (m.disabled) { msg.send(p, "disabled", "%material%", name); return; }
        if (p.getUniqueId().equals(bank.owner)) { msg.send(p, "own-bank"); return; }

        Material item = plugin.markets().itemOf(bank.marketKey);
        Material block = plugin.markets().blockOf(bank.marketKey);
        long have = Items.count(p.getInventory(), item, block);
        if (have == 0) { msg.send(p, "not-enough-items", "%material%", name); return; }
        if (limitLeft(p, bank.marketKey, true) == 0) { msg.send(p, "limit-reached", "%material%", name); return; }

        SellGuard guard = plugin.markets().guard();
        long now = System.currentTimeMillis();
        if (bank.isServer()) {
            long closed = guard.closedFor(m.key, now);
            if (closed > 0) {
                msg.send(p, "selling-closed", "%material%", name, "%time%", SellGuard.time(closed));
                return;
            }
            if (guard.allowed(p.getUniqueId(), m, 1, now) == 0 && m.room() > 0) {
                msg.send(p, "sold-a-lot", "%material%", name, "%time%", SellGuard.time(guard.playerWait(p.getUniqueId(), m.key, now)));
                return;
            }
        }

        long n = sellable(p, bank, requested);
        if (n == 0) {
            msg.send(p, bank.isServer() ? "bank-full" : "chest-full", "%material%", name);
            return;
        }
        double gross = m.sellTotal(n);
        Tax.Split split = tax().sell(gross, bank.isServer());

        if (bank.isServer()) {
            drop(p, item, Items.take(p.getInventory(), item, block, n));
            if (!eco.deposit(p, split.player())) {
                drop(p, item, Items.give(p.getInventory(), item, n));
                msg.send(p, "no-economy");
                return;
            }
            m.stock += n;
            if (guard.record(p.getUniqueId(), m, n, gross, now) && plugin.settings().announceClose) {
                String text = plugin.getConfig().getString("messages.closed-announce", "");
                if (!text.isEmpty()) {
                    Bukkit.broadcast(Messages.color(plugin.getConfig().getString("messages.prefix", "")
                            + Messages.fill(text, "%material%", name, "%time%", SellGuard.time(guard.rules().closeMillis))));
                }
            }
        } else {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(bank.owner);
            Inventory chest = bank.inventory();
            if (chest == null) return;
            if (!eco.has(owner, split.owner()) || !eco.withdraw(owner, split.owner())) {
                msg.send(p, "owner-broke");
                return;
            }
            drop(p, item, Items.take(p.getInventory(), item, block, n));
            Items.give(chest, item, n);
            eco.deposit(p, split.player());
        }
        double created = split.created() - payTax(eco, p, split.tax(), bank, true);
        plugin.markets().recordCreated(created);

        bank.addSale(n, gross, split.tax());
        plugin.markets().addTraded(p.getUniqueId(), bank.marketKey, true, n);
        if (split.tax() > 0) {
            msg.send(p, "sold-taxed", "%amount%", String.valueOf(n), "%material%", name,
                    "%money%", eco.format(split.player()), "%tax%", eco.format(split.tax()));
        } else {
            msg.send(p, "sold", "%amount%", String.valueOf(n), "%material%", name, "%money%", eco.format(split.player()));
        }
        log(p, "SELL", n, bank, split.player(), split.tax(), m);
        after(bank);
    }

    public void buy(Player p, Bank bank, int requested) {
        Money eco = plugin.money();
        Messages msg = plugin.messages();
        Market m = plugin.markets().get(bank.marketKey);
        if (eco == null) { msg.send(p, "no-economy"); return; }
        if (m == null) return;
        String name = Messages.pretty(bank.marketKey);
        if (m.disabled) { msg.send(p, "disabled", "%material%", name); return; }
        if (p.getUniqueId().equals(bank.owner)) { msg.send(p, "own-bank"); return; }

        Material item = plugin.markets().itemOf(bank.marketKey);
        Material block = plugin.markets().blockOf(bank.marketKey);
        long available = bank.isServer() ? (s().limitedStock ? m.stock : Long.MAX_VALUE) : playerStock(bank);
        if (available <= 0) { msg.send(p, "out-of-stock", "%material%", name); return; }
        if (Items.room(p.getInventory(), item) == 0) { msg.send(p, "inventory-full"); return; }
        if (limitLeft(p, bank.marketKey, false) == 0) { msg.send(p, "limit-reached", "%material%", name); return; }

        long n = buyable(p, bank, requested);
        double balance = eco.balance(p);
        if (buyCost(bank, n) > balance) n = affordable(bank, n, balance);
        if (n <= 0) { msg.send(p, "not-enough-money"); return; }
        double cost = m.buyTotal(n);
        Tax.Split split = tax().buy(cost, bank.isServer());

        if (!eco.withdraw(p, split.player())) {
            msg.send(p, "not-enough-money");
            return;
        }
        if (bank.isServer()) {
            m.stock -= n;
        } else {
            Inventory chest = bank.inventory();
            if (chest == null) { eco.deposit(p, split.player()); return; }
            long change = Items.take(chest, item, block, n);
            if (change > 0) dropAt(bank.block.clone().add(0.5, 1, 0.5), item, change);   // owner's change, chest was full
            eco.deposit(Bukkit.getOfflinePlayer(bank.owner), split.owner());
        }
        drop(p, item, Items.give(p.getInventory(), item, n));
        double created = split.created() - payTax(eco, p, split.tax(), bank, false);
        plugin.markets().recordCreated(created);

        bank.addPurchase(n, cost, split.tax());
        plugin.markets().addTraded(p.getUniqueId(), bank.marketKey, false, n);
        if (split.tax() > 0) {
            msg.send(p, "bought-taxed", "%amount%", String.valueOf(n), "%material%", name,
                    "%money%", eco.format(split.player()), "%tax%", eco.format(split.tax()));
        } else {
            msg.send(p, "bought", "%amount%", String.valueOf(n), "%material%", name, "%money%", eco.format(split.player()));
        }
        log(p, "BUY", n, bank, split.player(), split.tax(), m);
        after(bank);
    }

    private Tax tax() {
        return s().tax;
    }

    /** What the player gets for selling n here, after tax. */
    public double sellPayout(Bank bank, long n) {
        Market m = plugin.markets().get(bank.marketKey);
        return tax().sell(m.sellTotal(n), bank.isServer()).player();
    }

    /** What the player pays for buying n here, with tax. */
    public double buyCost(Bank bank, long n) {
        Market m = plugin.markets().get(bank.marketKey);
        return tax().buy(m.buyTotal(n), bank.isServer()).player();
    }

    /**
     * Pays the tax to the receiver. Returns money that couldn't be paid out after all
     * (so it was burned and the caller can count it as removed).
     */
    private double payTax(Money eco, Player payer, double amount, Bank bank, boolean selling) {
        if (amount <= 0) return 0;
        UUID receiver = tax().receiver;
        double lost = 0;
        if (receiver != null && !eco.deposit(Bukkit.getOfflinePlayer(receiver), amount)) {
            plugin.getLogger().warning("Couldn't pay " + amount + " tax to " + receiver + ". It was removed instead.");
            lost = amount;
            receiver = null;
        }
        plugin.markets().taxCollected += amount;
        Bukkit.getPluginManager().callEvent(new TaxCollectedEvent(payer, receiver, amount, bank.marketKey, selling, bank.owner));
        return lost;
    }
    /** Largest amount up to {@code max} this player can afford here right now, for the menu. */
    public long affordableNow(Player p, Bank bank, long max) {
        Money eco = plugin.money();
        if (eco == null || max <= 0) return 0;
        double balance = eco.balance(p);
        return buyCost(bank, max) <= balance ? max : affordable(bank, max, balance);
    }

    /** Largest amount up to {@code max} the player can pay for. */
    private long affordable(Bank bank, long max, double balance) {
        long lo = 0, hi = max;
        while (lo < hi) {
            long mid = (lo + hi + 1) / 2;
            if (buyCost(bank, mid) <= balance) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private static void drop(Player p, Material item, long amount) {
        dropAt(p.getLocation(), item, amount);
    }

    private static void dropAt(org.bukkit.Location at, Material item, long amount) {
        while (amount > 0) {
            int n = (int) Math.min(amount, item.getMaxStackSize());
            at.getWorld().dropItemNaturally(at, new ItemStack(item, n));
            amount -= n;
        }
    }

    private void after(Bank bank) {
        if (bank.isServer()) plugin.banks().refreshMarket(bank.marketKey);
        else plugin.banks().refresh(bank);
    }

    private void log(Player p, String action, long n, Bank bank, double money, double tax, Market m) {
        long stock = bank.isServer() ? m.stock : playerStock(bank);
        String when = LocalDateTime.now().format(TIME);
        bank.addRecent(String.format(java.util.Locale.ROOT, "%s %s %s %d for %.2f%s",
                when.substring(5, 16), p.getName(), action.equals("SELL") ? "sold" : "bought", n, money,
                tax > 0 ? String.format(java.util.Locale.ROOT, " (tax %.2f)", tax) : ""));
        String line = String.format(java.util.Locale.ROOT, "%s %s %s %d %s for %.2f, tax %.2f, at %s bank %s, stock now %d%n",
                when, p.getName(), action, n, bank.marketKey, money, tax,
                bank.isServer() ? "server" : Bukkit.getOfflinePlayer(bank.owner).getName() + "'s", bank.shortId(), stock);
        try (FileWriter w = new FileWriter(log, true)) {
            w.write(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't write transactions.log: " + e.getMessage());
        }
    }
}
