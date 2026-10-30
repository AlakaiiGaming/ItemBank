package com.alakaiigaming.itembank.api;

import com.alakaiigaming.itembank.ItemBank;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * For other plugins. Get it with {@code ItemBankAPI.get()} or Bukkit's ServicesManager.
 * Changes made here are saved to config.yml.
 */
public final class ItemBankAPI {

    private final ItemBank plugin;

    public ItemBankAPI(ItemBank plugin) {
        this.plugin = plugin;
    }

    /** null if Item Bank isn't running. */
    public static ItemBankAPI get() {
        var reg = Bukkit.getServicesManager().getRegistration(ItemBankAPI.class);
        return reg == null ? null : reg.getProvider();
    }

    // tax

    public boolean isTaxEnabled() { return plugin.settings().tax.enabled; }
    public void setTaxEnabled(boolean on) { plugin.setTax("tax.enabled", on); }

    /** In percent, e.g. 5 for 5%. */
    public double getSellTax() { return plugin.settings().tax.sellRate * 100; }
    /** Clamped to tax.max-percent. Returns what was set. */
    public double setSellTax(double percent) { return plugin.setTaxRate(true, percent); }

    public double getBuyTax() { return plugin.settings().tax.buyRate * 100; }
    public double setBuyTax(double percent) { return plugin.setTaxRate(false, percent); }

    public double getMaxTax() { return plugin.settings().tax.maxRate * 100; }

    /** Who gets the tax, or null when it's removed from the economy. */
    public UUID getTaxReceiver() { return plugin.settings().tax.receiver; }
    /** null = nobody (tax money is removed). */
    public void setTaxReceiver(UUID player) { plugin.setTaxReceiver(player); }

    /** All tax collected since the plugin was installed. */
    public double getTotalTaxCollected() { return plugin.markets().taxCollected; }

    // economy

    /** Money held by active players at the last daily check, NaN if never checked. */
    public double getMoneySupply() { return plugin.markets().inflation().lastTotal; }

    /** Active players counted in the last daily check. */
    public int getActivePlayers() { return plugin.markets().inflation().lastPlayers; }

    /** Adds up active players' balances right now. Slow on big servers, call it rarely. */
    public double measureMoneySupply() { return plugin.markets().measureMoneySupply().total(); }

    /** Net money banks have created (payouts minus purchases and burned tax). */
    public double getBankIssuedMoney() { return plugin.markets().inflation().issued; }

    /** Price multiplier from inflation. 1 = starting prices. */
    public double getInflationIndex() { return plugin.markets().index(); }

    /** Sets the price multiplier directly, within inflation.index-limits. Today's money becomes the new normal. */
    public void setInflationIndex(double index) { plugin.markets().setIndex(index); }
}
