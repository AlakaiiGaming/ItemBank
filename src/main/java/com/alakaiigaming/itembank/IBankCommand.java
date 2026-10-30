package com.alakaiigaming.itembank;

import com.alakaiigaming.itembank.bank.Bank;
import com.alakaiigaming.itembank.market.Market;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class IBankCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("prices", "create", "remove", "removeall", "list", "info", "tax", "stats",
            "inflation", "setowner", "stock", "freeze", "disable", "reopen", "update", "reload");

    private final ItemBank plugin;

    IBankCommand(ItemBank plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private boolean need(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        msg().send(s, "no-permission");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "prices";
        switch (sub) {
            case "prices" -> prices(sender);
            case "create" -> create(sender, args);
            case "remove" -> remove(sender, args);
            case "removeall" -> removeAll(sender, args);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "tax" -> tax(sender, args);
            case "stats" -> stats(sender);
            case "inflation" -> inflation(sender, args);
            case "setowner" -> setOwner(sender, args);
            case "stock" -> stock(sender, args);
            case "freeze" -> toggle(sender, args, true);
            case "disable" -> toggle(sender, args, false);
            case "reopen" -> {
                if (!need(sender, "itembank.admin")) return true;
                Market m = args.length >= 2 ? plugin.markets().get(args[1]) : null;
                if (m == null) {
                    msg().send(sender, "unknown-material", "%material%", args.length >= 2 ? args[1] : "?");
                    return true;
                }
                plugin.markets().guard().reopen(m.key);
                plugin.markets().save();
                msg().send(sender, "reopened", "%material%", Messages.pretty(m.key));
            }
            case "update" -> {
                if (!need(sender, "itembank.admin")) return true;
                double change = plugin.markets().dailyUpdate();
                msg().send(sender, "updated", "%change%", String.format(Locale.ROOT, "%+.2f%%", change * 100));
            }
            case "reload" -> {
                if (!need(sender, "itembank.admin")) return true;
                plugin.reload();
                msg().send(sender, "reloaded");
            }
            default -> msg().send(sender, "usage");
        }
        return true;
    }

    private void prices(CommandSender sender) {
        msg().send(sender, "prices-header");
        String line = plugin.getConfig().getString("messages.prices-line", "%material%: %sell% / %buy%");
        for (Market m : plugin.markets().all().values()) {
            String change = Double.isNaN(m.lastDailyPrice) || m.lastDailyPrice <= 0 ? "&7-"
                    : String.format(Locale.ROOT, "%s%+.1f%%", m.price() >= m.lastDailyPrice ? "&a" : "&c", (m.price() / m.lastDailyPrice - 1) * 100);
            String stock = plugin.settings().limitedStock ? String.valueOf(m.stock) : "∞";
            if (m.disabled) stock += " &8(off)";
            sender.sendMessage(Messages.color(Messages.fill(line,
                    "%material%", Messages.pretty(m.key),
                    "%sell%", Messages.number(m.sellPrice()),
                    "%buy%", Messages.number(m.buyPrice()),
                    "%stock%", stock,
                    "%change%", change)));
        }
    }

    private Block target(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg().send(sender, "not-a-chest");
            return null;
        }
        Block b = p.getTargetBlockExact(6);
        if (b == null) {
            msg().send(sender, "not-a-chest");
            return null;
        }
        Bank bank = plugin.banks().at(b);
        if (bank != null) return bank.block.getBlock();
        if (!(b.getState() instanceof Chest)) {
            msg().send(sender, "not-a-chest");
            return null;
        }
        return b;
    }

    private Bank looking(CommandSender sender) {
        Block b = target(sender);
        if (b == null) return null;
        Bank bank = plugin.banks().at(b);
        if (bank == null) msg().send(sender, "not-a-bank");
        return bank;
    }

    private void create(CommandSender sender, String[] args) {
        boolean server = sender.hasPermission("itembank.create");
        boolean own = sender.hasPermission("itembank.create.own");
        if (!server && !own && !sender.hasPermission("itembank.create.player")) {
            msg().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "usage");
            return;
        }
        Market m = plugin.markets().get(args[1]);
        if (m == null) {
            msg().send(sender, "unknown-material", "%material%", args[1]);
            return;
        }
        UUID owner = null;
        boolean forSelf = false;
        if (args.length >= 3) {
            if (!need(sender, "itembank.create.player")) return;
            OfflinePlayer op = findPlayer(args[2]);
            if (op == null) {
                msg().send(sender, "player-not-found");
                return;
            }
            owner = op.getUniqueId();
        } else if (!server) {
            // a player making their own bank
            if (!(sender instanceof Player p)) return;
            owner = p.getUniqueId();
            forSelf = true;
        }
        Block b = target(sender);
        if (b == null) return;
        if (plugin.banks().at(b) != null) {
            msg().send(sender, "already-bank");
            return;
        }
        if (forSelf && !ownBankAllowed((Player) sender, m, b)) return;

        plugin.banks().create(b, m.key, owner);
        msg().send(sender, owner == null ? "created" : "created-player", "%material%", Messages.pretty(m.key),
                "%owner%", owner == null ? "" : name(owner));
    }

    /** Checks and charges a player making their own bank. */
    private boolean ownBankAllowed(Player p, Market m, Block b) {
        Settings s = plugin.settings();
        if (!s.playerBankMaterials.isEmpty() && !s.playerBankMaterials.contains(m.key)) {
            msg().send(p, "material-not-allowed", "%material%", Messages.pretty(m.key));
            return false;
        }
        boolean unlimited = p.hasPermission("itembank.create.unlimited") || p.hasPermission("itembank.admin");
        int have = plugin.banks().ownedBy(p.getUniqueId()).size();
        if (!unlimited && s.maxPlayerBanks >= 0 && have >= s.maxPlayerBanks) {
            msg().send(p, "too-many-banks", "%max%", String.valueOf(s.maxPlayerBanks));
            return false;
        }
        if (s.checkProtection && !com.alakaiigaming.itembank.bank.ProtectionCheck.canUse(p, b)) {
            msg().send(p, "not-your-chest");
            return false;
        }
        if (s.createCost > 0) {
            var eco = plugin.money();
            if (eco == null) {
                msg().send(p, "no-economy");
                return false;
            }
            if (!eco.has(p, s.createCost) || !eco.withdraw(p, s.createCost)) {
                msg().send(p, "create-cost", "%money%", eco.format(s.createCost));
                return false;
            }
            plugin.markets().recordCreated(-s.createCost);
            msg().send(p, "create-paid", "%money%", eco.format(s.createCost));
        }
        return true;
    }

    private boolean canManage(CommandSender sender, Bank bank) {
        if (sender.hasPermission("itembank.admin")) return true;
        if (bank.isServer()) return sender.hasPermission("itembank.create");
        return sender instanceof Player p && p.getUniqueId().equals(bank.owner);
    }

    /** /ibank remove (looking at it) or /ibank remove <id> */
    private void remove(CommandSender sender, String[] args) {
        Bank bank;
        if (args.length >= 2) {
            bank = plugin.banks().byShortId(args[1]);
            if (bank == null) {
                msg().send(sender, "no-bank-id", "%id%", args[1]);
                return;
            }
        } else {
            bank = looking(sender);
            if (bank == null) return;
        }
        if (!canManage(sender, bank)) {
            msg().send(sender, "not-your-bank");
            return;
        }
        plugin.banks().remove(bank);
        msg().send(sender, "removed");
    }

    private void removeAll(CommandSender sender, String[] args) {
        if (!need(sender, "itembank.admin")) return;
        if (args.length < 2) {
            msg().send(sender, "usage");
            return;
        }
        UUID owner = null;
        if (!args[1].equalsIgnoreCase("server")) {
            OfflinePlayer op = findPlayer(args[1]);
            if (op == null) {
                msg().send(sender, "player-not-found");
                return;
            }
            owner = op.getUniqueId();
        }
        List<Bank> list = plugin.banks().ownedBy(owner);
        for (Bank b : list) plugin.banks().remove(b);
        msg().send(sender, "removed-all", "%amount%", String.valueOf(list.size()), "%owner%", args[1]);
    }

    /** /ibank list [player|server|all] */
    private void list(CommandSender sender, String[] args) {
        List<Bank> list;
        String who;
        if (args.length >= 2 && !(sender instanceof Player p && args[1].equalsIgnoreCase(p.getName()))) {
            if (!need(sender, "itembank.admin")) return;
            if (args[1].equalsIgnoreCase("all")) {
                list = new ArrayList<>(plugin.banks().all());
                who = "all";
            } else if (args[1].equalsIgnoreCase("server")) {
                list = plugin.banks().ownedBy(null);
                who = "server";
            } else {
                OfflinePlayer op = findPlayer(args[1]);
                if (op == null) {
                    msg().send(sender, "player-not-found");
                    return;
                }
                list = plugin.banks().ownedBy(op.getUniqueId());
                who = args[1];
            }
        } else if (sender instanceof Player p) {
            list = plugin.banks().ownedBy(p.getUniqueId());
            who = p.getName();
        } else {
            list = new ArrayList<>(plugin.banks().all());
            who = "all";
        }
        msg().send(sender, "list-header", "%owner%", who, "%amount%", String.valueOf(list.size()));
        String line = plugin.getConfig().getString("messages.list-line", "&7%id% &f%material% &7%location% stock %stock%");
        for (Bank b : list) {
            sender.sendMessage(Messages.color(Messages.fill(line,
                    "%id%", b.shortId(),
                    "%material%", Messages.pretty(b.marketKey),
                    "%owner%", b.isServer() ? "server" : name(b.owner),
                    "%location%", b.block.getWorld().getName() + " " + b.block.getBlockX() + " " + b.block.getBlockY() + " " + b.block.getBlockZ(),
                    "%stock%", stock(b))));
        }
        if (list.isEmpty()) msg().send(sender, "list-empty");
        else msg().send(sender, "list-footer");
    }

    private String stock(Bank b) {
        if (b.isServer()) {
            Market m = plugin.markets().get(b.marketKey);
            if (m == null) return "?";
            return plugin.settings().limitedStock ? String.valueOf(m.stock) : "∞";
        }
        return String.valueOf(plugin.trades().playerStock(b));
    }

    private String money(double v) {
        return plugin.money() != null ? plugin.money().format(v) : Messages.number(v);
    }

    /** /ibank info (looking at it) or /ibank info <id> */
    private void info(CommandSender sender, String[] args) {
        Bank bank;
        if (args.length >= 2) {
            bank = plugin.banks().byShortId(args[1]);
            if (bank == null) {
                msg().send(sender, "no-bank-id", "%id%", args[1]);
                return;
            }
        } else {
            bank = looking(sender);
            if (bank == null) return;
        }
        if (!canManage(sender, bank)) {
            msg().send(sender, "not-your-bank");
            return;
        }
        Market m = plugin.markets().get(bank.marketKey);
        Bank.Stats t = bank.total, d = bank.today();
        String created = bank.created > 0
                ? java.time.Instant.ofEpochMilli(bank.created).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() : "?";
        String stock = stock(bank);
        if (bank.isServer() && m != null && plugin.settings().limitedStock) {
            stock += " &7(normal " + m.normalStock + (m.maxStock > 0 ? ", max " + m.maxStock : "") + ", shared by every "
                    + Messages.pretty(bank.marketKey) + " server bank)";
        }
        for (String line : plugin.getConfig().getStringList("messages.info")) {
            sender.sendMessage(Messages.color(Messages.fill(line,
                    "%id%", bank.shortId(),
                    "%material%", Messages.pretty(bank.marketKey),
                    "%owner%", bank.isServer() ? "server" : name(bank.owner),
                    "%created%", created,
                    "%stock%", stock,
                    "%sell%", m == null ? "?" : money(m.sellPrice()),
                    "%buy%", m == null ? "?" : money(m.buyPrice()),
                    "%today-in%", String.valueOf(d.itemsIn),
                    "%today-out%", String.valueOf(d.itemsOut),
                    "%today-paid%", money(d.moneyOut),
                    "%today-got%", money(d.moneyIn),
                    "%today-tax%", money(d.tax),
                    "%total-in%", String.valueOf(t.itemsIn),
                    "%total-out%", String.valueOf(t.itemsOut),
                    "%total-paid%", money(t.moneyOut),
                    "%total-got%", money(t.moneyIn),
                    "%total-tax%", money(t.tax))));
        }
        if (!bank.recent.isEmpty()) {
            msg().send(sender, "info-recent");
            for (String r : bank.recent) sender.sendMessage(Messages.color("&8- &7" + r));
        }
    }

    /** /ibank tax, /ibank tax on|off, /ibank tax sell|buy <percent>, /ibank tax receiver <player|none> */
    private void tax(CommandSender sender, String[] args) {
        var tax = plugin.settings().tax;
        if (args.length < 2) {
            String receiver = tax.receiver == null ? "none" : name(tax.receiver);
            msg().send(sender, "tax-info",
                    "%state%", tax.enabled ? "on" : "off",
                    "%sell%", Messages.number(tax.sellRate * 100),
                    "%buy%", Messages.number(tax.buyRate * 100),
                    "%receiver%", receiver,
                    "%total%", money(plugin.markets().taxCollected));
            return;
        }
        if (!need(sender, "itembank.tax")) return;
        String what = args[1].toLowerCase(Locale.ROOT);
        switch (what) {
            case "on", "off" -> {
                plugin.setTax("tax.enabled", what.equals("on"));
                msg().send(sender, "tax-set", "%what%", "tax", "%value%", what);
            }
            case "sell", "buy" -> {
                if (args.length < 3) { msg().send(sender, "usage"); return; }
                double pct;
                try {
                    pct = Double.parseDouble(args[2].replace("%", ""));
                } catch (NumberFormatException e) {
                    msg().send(sender, "usage");
                    return;
                }
                double set = plugin.setTaxRate(what.equals("sell"), pct);
                msg().send(sender, "tax-set", "%what%", what + " tax", "%value%", Messages.number(set) + "%");
            }
            case "receiver" -> {
                if (args.length < 3) { msg().send(sender, "usage"); return; }
                if (args[2].equalsIgnoreCase("none")) {
                    plugin.setTaxReceiver(null);
                } else {
                    OfflinePlayer op = findPlayer(args[2]);
                    if (op == null) {
                        msg().send(sender, "player-not-found");
                        return;
                    }
                    plugin.setTaxReceiver(op.getUniqueId());
                }
                msg().send(sender, "tax-set", "%what%", "tax receiver", "%value%", args[2]);
            }
            default -> msg().send(sender, "usage");
        }
    }

    /** /ibank inflation reset, /ibank inflation set <index> */
    private void inflation(CommandSender sender, String[] args) {
        if (!need(sender, "itembank.admin")) return;
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            plugin.markets().rebaseInflation();
            msg().send(sender, "inflation-reset");
        } else if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
            try {
                plugin.markets().setIndex(Double.parseDouble(args[2]));
            } catch (NumberFormatException e) {
                msg().send(sender, "usage");
                return;
            }
            msg().send(sender, "index-set", "%index%", String.format(Locale.ROOT, "%.3f", plugin.markets().index()));
        } else {
            stats(sender);
        }
    }

    private void stats(CommandSender sender) {
        if (!need(sender, "itembank.admin")) return;
        var inf = plugin.markets().inflation();
        for (String line : plugin.getConfig().getStringList("messages.stats")) {
            sender.sendMessage(Messages.color(Messages.fill(line,
                    "%supply%", Double.isNaN(inf.lastTotal) ? "not checked yet" : money(inf.lastTotal),
                    "%players%", String.valueOf(inf.lastPlayers),
                    "%per-player%", Double.isNaN(inf.lastTotal) ? "-" : money(inf.lastTotal / Math.max(1, inf.lastPlayers)),
                    "%supply-change%", String.format(Locale.ROOT, "%+.1f%%", inf.supplyChange() * 100),
                    "%days%", String.valueOf(Math.max(0, inf.history.size() - 1)),
                    "%issued%", money(inf.issued),
                    "%index%", String.format(Locale.ROOT, "%.3f", plugin.markets().index()),
                    "%tax-total%", money(plugin.markets().taxCollected),
                    "%banks%", String.valueOf(plugin.banks().all().size()),
                    "%player-banks%", String.valueOf(plugin.banks().all().stream().filter(b -> !b.isServer()).count()))));
        }
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    private void setOwner(CommandSender sender, String[] args) {
        if (!need(sender, "itembank.admin")) return;
        if (args.length < 2) {
            msg().send(sender, "usage");
            return;
        }
        Bank bank = looking(sender);
        if (bank == null) return;
        if (args[1].equalsIgnoreCase("server")) {
            bank.owner = null;
        } else {
            OfflinePlayer op = findPlayer(args[1]);
            if (op == null) {
                msg().send(sender, "player-not-found");
                return;
            }
            bank.owner = op.getUniqueId();
        }
        plugin.banks().save();
        plugin.banks().refresh(bank);
        msg().send(sender, "owner-set", "%owner%", bank.isServer() ? "server" : args[1]);
    }

    private void stock(CommandSender sender, String[] args) {
        if (!need(sender, "itembank.admin")) return;
        if (args.length < 3) {
            msg().send(sender, "usage");
            return;
        }
        Market m = plugin.markets().get(args[1]);
        if (m == null) {
            msg().send(sender, "unknown-material", "%material%", args[1]);
            return;
        }
        long amount;
        try {
            amount = Math.max(0, Long.parseLong(args[2]));
        } catch (NumberFormatException e) {
            msg().send(sender, "usage");
            return;
        }
        m.stock = amount;
        plugin.markets().save();
        plugin.banks().refreshMarket(m.key);
        msg().send(sender, "stock-set", "%material%", Messages.pretty(m.key), "%amount%", String.valueOf(amount));
    }

    private void toggle(CommandSender sender, String[] args, boolean freeze) {
        if (!need(sender, "itembank.admin")) return;
        if (args.length < 2) {
            msg().send(sender, "usage");
            return;
        }
        Market m = plugin.markets().get(args[1]);
        if (m == null) {
            msg().send(sender, "unknown-material", "%material%", args[1]);
            return;
        }
        String name = Messages.pretty(m.key);
        if (freeze) {
            m.freeze(!m.frozen);
            msg().send(sender, m.frozen ? "frozen" : "unfrozen", "%material%", name);
        } else {
            m.disabled = !m.disabled;
            msg().send(sender, m.disabled ? "disabled-now" : "enabled", "%material%", name);
        }
        plugin.markets().save();
        plugin.banks().refreshMarket(m.key);
    }

    @SuppressWarnings("deprecation")
    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null && op.hasPlayedBefore() ? op : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("create", "stock", "freeze", "disable", "reopen").contains(sub)) {
            for (String k : plugin.markets().keys()) if (k.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k);
        } else if (args.length == 2 && sub.equals("inflation")) {
            for (String x : List.of("reset", "set")) if (x.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (args.length == 2 && sub.equals("tax")) {
            out.addAll(List.of("on", "off", "sell", "buy", "receiver"));
            out.removeIf(x -> !x.startsWith(args[1].toLowerCase(Locale.ROOT)));
        } else if (args.length == 2 && (sub.equals("remove") || sub.equals("info"))) {
            List<Bank> mine = sender.hasPermission("itembank.admin") ? new ArrayList<>(plugin.banks().all())
                    : sender instanceof Player p ? plugin.banks().ownedBy(p.getUniqueId()) : List.of();
            for (Bank b : mine) if (b.shortId().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(b.shortId());
        } else if ((args.length == 3 && sub.equals("create")) || (args.length == 2 && (sub.equals("setowner")
                || sub.equals("removeall") || sub.equals("list"))) || (args.length == 3 && sub.equals("tax") && args[1].equalsIgnoreCase("receiver"))) {
            if (sub.equals("setowner") || sub.equals("removeall") || sub.equals("list")) out.add("server");
            if (sub.equals("list")) out.add("all");
            if (sub.equals("tax")) out.add("none");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
        }
        return out;
    }
}
