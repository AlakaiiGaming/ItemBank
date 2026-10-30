package com.alakaiigaming.itembank.money;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Only loaded when Vault is installed. */
final class VaultMoney implements Money {

    private final Economy economy;
    private final String name;

    private VaultMoney(Economy economy, String name) {
        this.economy = economy;
        this.name = name;
    }

    static Money find() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultMoney(rsp.getProvider(), rsp.getPlugin().getName() + " (through Vault)");
    }

    @Override public String name() { return name; }
    @Override public double balance(OfflinePlayer p) { return economy.getBalance(p); }
    @Override public boolean withdraw(OfflinePlayer p, double a) { return economy.withdrawPlayer(p, a).transactionSuccess(); }
    @Override public boolean deposit(OfflinePlayer p, double a) { return economy.depositPlayer(p, a).transactionSuccess(); }
    @Override public String format(double a) { return economy.format(Math.round(a * 100) / 100.0); }
}
