package com.alakaiigaming.itembank.money;

import org.bukkit.OfflinePlayer;

/** Whatever pays players: Vault, or CMI directly. */
public interface Money {
    String name();
    double balance(OfflinePlayer player);
    boolean withdraw(OfflinePlayer player, double amount);
    boolean deposit(OfflinePlayer player, double amount);
    String format(double amount);

    default boolean has(OfflinePlayer player, double amount) {
        return balance(player) >= amount;
    }
}
