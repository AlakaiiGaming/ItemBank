package com.alakaiigaming.itembank.bank;

import com.alakaiigaming.itembank.ItemBank;
import com.alakaiigaming.itembank.Messages;
import com.alakaiigaming.itembank.Settings;
import com.alakaiigaming.itembank.market.Market;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The buy/sell screen. Row 2: sell buttons, the item, buy buttons. */
public final class BankMenu implements InventoryHolder {

    private static final int INFO = 13;

    private final ItemBank plugin;
    private final Bank bank;
    private final Player player;
    private final Inventory inventory;
    /** slot -> amount, positive = sell, negative = buy. ALL is stored as Integer.MAX_VALUE. */
    private final Map<Integer, Integer> actions = new HashMap<>();

    public BankMenu(ItemBank plugin, Bank bank, Player player) {
        this.plugin = plugin;
        this.bank = bank;
        this.player = player;
        String title = plugin.getConfig().getString("messages.menu-title", "%material% Bank");
        this.inventory = Bukkit.createInventory(this, 27, Messages.color(Messages.fill(title, "%material%", Messages.pretty(bank.marketKey))));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Bank bank() {
        return bank;
    }

    public void render() {
        actions.clear();
        Market m = plugin.markets().get(bank.marketKey);
        if (m == null) return;

        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        List<Integer> amounts = plugin.settings().menuAmounts;
        int count = amounts.size();
        for (int i = 0; i < count; i++) {
            int amount = amounts.get(i);
            int sellSlot = INFO - count + i;   // 9..12
            int buySlot = INFO + 1 + i;        // 14..17
            inventory.setItem(sellSlot, sellButton(m, amount));
            inventory.setItem(buySlot, buyButton(m, amount));
            int stored = amount == Settings.ALL ? Integer.MAX_VALUE : amount;
            actions.put(sellSlot, stored);
            actions.put(buySlot, -stored);
        }
        inventory.setItem(INFO, info(m));
    }

    private String text(String key, String fallback) {
        return plugin.getConfig().getString("messages." + key, fallback);
    }

    private ItemStack sellButton(Market m, int amount) {
        long n = plugin.trades().sellable(player, bank, amount);
        String label = amount == Settings.ALL ? text("menu-sell-all", "&aSell all") : text("menu-sell", "&aSell %amount%");
        List<String> lore = new ArrayList<>();
        if (n > 0) {
            lore.add(Messages.fill(text("menu-get", "&7You get &f%money%"), "%money%", money(plugin.trades().sellPayout(bank, n))));
            if (amount == Settings.ALL || n < amount) lore.add(Messages.fill(text("menu-count", "&7%amount% items"), "%amount%", String.valueOf(n)));
        } else {
            lore.add(text("menu-none", "&8Not available"));
        }
        return button(n > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                Messages.fill(label, "%amount%", String.valueOf(amount)), lore);
    }

    private ItemStack buyButton(Market m, int amount) {
        long available = plugin.trades().buyable(player, bank, amount);
        long n = plugin.trades().affordableNow(player, bank, available);
        String label = amount == Settings.ALL ? text("menu-buy-all", "&cBuy max") : text("menu-buy", "&cBuy %amount%");
        List<String> lore = new ArrayList<>();
        if (available > 0 && n == 0) {
            lore.add(text("menu-cant-afford", "&cYou can't afford any"));
        } else if (n > 0) {
            lore.add(Messages.fill(text("menu-cost", "&7Costs &f%money%"), "%money%", money(plugin.trades().buyCost(bank, n))));
            if (amount == Settings.ALL || n < amount) lore.add(Messages.fill(text("menu-count", "&7%amount% items"), "%amount%", String.valueOf(n)));
        } else if (stockLeft(m) <= 0) {
            lore.add(text("menu-out-of-stock", "&cOut of stock"));
        } else {
            lore.add(text("menu-none", "&8Not available"));
        }
        return button(n > 0 ? Material.ORANGE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                Messages.fill(label, "%amount%", String.valueOf(amount)), lore);
    }

    private long stockLeft(Market m) {
        if (!bank.isServer()) return plugin.trades().playerStock(bank);
        return plugin.settings().limitedStock ? m.stock : Long.MAX_VALUE;
    }

    private ItemStack info(Market m) {
        String stock;
        if (bank.isServer()) stock = plugin.settings().limitedStock ? String.valueOf(m.stock) : "∞";
        else stock = String.valueOf(plugin.trades().playerStock(bank));
        String change = Double.isNaN(m.lastDailyPrice) || m.lastDailyPrice <= 0 ? "-"
                : String.format(java.util.Locale.ROOT, "%+.1f%%", (m.price() / m.lastDailyPrice - 1) * 100);
        String owner = bank.isServer() ? text("menu-server", "Server")
                : String.valueOf(Bukkit.getOfflinePlayer(bank.owner).getName());

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("messages.menu-info")) {
            lore.add(Messages.fill(line,
                    "%sell%", money(m.sellPrice()),
                    "%buy%", money(m.buyPrice()),
                    "%stock%", stock,
                    "%change%", change,
                    "%owner%", owner,
                    "%tax%", taxText()));
        }
        Material item = plugin.markets().itemOf(bank.marketKey);
        return button(item, "&f" + Messages.pretty(bank.marketKey), lore);
    }

    private String taxText() {
        var tax = plugin.settings().tax;
        if (!tax.applies(bank.isServer())) return text("menu-no-tax", "none");
        return Messages.fill(text("menu-tax", "%sell%% selling, %buy%% buying"),
                "%sell%", Messages.number(tax.sellRate * 100), "%buy%", Messages.number(tax.buyRate * 100));
    }

    private String money(double v) {
        return plugin.money() != null ? plugin.money().format(v) : Messages.number(v);
    }

    private static ItemStack button(Material type, String name, List<String> lore) {
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.color(name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Messages.color(l));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Returns true if the click did something. */
    public boolean click(int slot) {
        Integer action = actions.get(slot);
        if (action == null) return false;
        if (plugin.banks().at(bank.block.getBlock()) != bank) {   // removed while the menu was open
            player.closeInventory();
            return false;
        }
        int amount = Math.abs(action) == Integer.MAX_VALUE ? Settings.ALL : Math.abs(action);
        plugin.messages().takeLast(player.getUniqueId());
        if (action > 0) plugin.trades().sell(player, bank, amount);
        else plugin.trades().buy(player, bank, amount);
        render();
        showResult(slot, plugin.messages().takeLast(player.getUniqueId()));
        return true;
    }

    /**
     * Chat is hidden while a menu is open, so show what happened on the button for a couple of
     * seconds: green for a trade, red for a problem.
     */
    private void showResult(int slot, String text) {
        if (text == null || text.isEmpty()) return;
        boolean ok = text.startsWith("&a");
        inventory.setItem(slot, button(ok ? Material.LIME_STAINED_GLASS_PANE : Material.BARRIER, text, List.of()));
        long id = ++shown;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (id == shown && player.getOpenInventory().getTopInventory() == inventory) render();
        }, 40L);
    }

    private long shown;
}
