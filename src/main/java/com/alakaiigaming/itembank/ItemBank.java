package com.alakaiigaming.itembank;

import com.alakaiigaming.itembank.bank.BankListener;
import com.alakaiigaming.itembank.bank.BankManager;
import com.alakaiigaming.itembank.bank.TradeService;
import com.alakaiigaming.itembank.market.MarketManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import com.alakaiigaming.itembank.money.Money;
import com.alakaiigaming.itembank.money.MoneyFinder;
import com.alakaiigaming.itembank.api.ItemBankAPI;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class ItemBank extends JavaPlugin {

    private final Settings settings = new Settings();
    private Messages messages;
    private MarketManager markets;
    private BankManager banks;
    private TradeService trades;
    private Money money;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // add settings from newer versions to an existing config
        ConfigMigration.run(this);
        getConfig().options().copyDefaults(true);
        saveConfig();
        settings.load(getConfig(), getLogger());
        messages = new Messages(this);
        markets = new MarketManager(this);
        banks = new BankManager(this);
        trades = new TradeService(this);

        markets.load();
        banks.load();

        PluginCommand command = getCommand("ibank");
        if (command != null) {
            IBankCommand executor = new IBankCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        Bukkit.getPluginManager().registerEvents(new BankListener(this), this);
        Bukkit.getServicesManager().register(ItemBankAPI.class, new ItemBankAPI(this), this, ServicePriority.Normal);

        // economy plugins often register after us, so look once the server has started
        Bukkit.getScheduler().runTask(this, () -> {
            hookEconomy(true);
            banks.refreshAll();
        });
        Bukkit.getScheduler().runTaskTimer(this, markets::tick, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, banks::spinAll, 20L, 10L);
        // save every 5 minutes in case of a crash
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            markets.save();
            banks.save();
        }, 20L * 300, 20L * 300);
    }

    private long lastHookTry;

    /** Looks for an economy. Logs why if there isn't one (only on the first try, or when {@code loud}). */
    private void hookEconomy(boolean loud) {
        lastHookTry = System.currentTimeMillis();
        money = MoneyFinder.find();
        if (money != null) {
            getLogger().info("Using " + money.name() + " for money.");
        } else if (loud) {
            getLogger().severe("No money plugin found. Item Bank works with CMI directly, or any economy through Vault "
                    + "(EssentialsX...). Item Bank will keep checking.");
        }
    }

    @Override
    public void onDisable() {
        if (banks != null) {
            banks.clearDisplays();
            banks.save();
        }
        if (markets != null) markets.save();
    }

    void reload() {
        reloadConfig();
        settings.load(getConfig(), getLogger());
        markets.save();
        markets.load();
        banks.redisplayAll();
    }

    // tax changes from commands or other plugins, saved to config.yml

    public void setTax(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
        settings.load(getConfig(), getLogger());
    }

    /** Percent, clamped to tax.max-percent. Returns what was set. */
    public double setTaxRate(boolean sell, double percent) {
        double set = settings.tax.clampRate(percent / 100.0) * 100;
        setTax(sell ? "tax.sell-percent" : "tax.buy-percent", set);
        return set;
    }

    public void setTaxReceiver(UUID player) {
        setTax("tax.receiver", player == null ? "none" : player.toString());
    }

    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public MarketManager markets() { return markets; }
    public BankManager banks() { return banks; }
    public TradeService trades() { return trades; }
    /** null when no money plugin is hooked. Keeps checking, since some register late. */
    public Money money() {
        if (money == null && System.currentTimeMillis() - lastHookTry > 5000) hookEconomy(false);
        return money;
    }
}
