package com.alakaiigaming.itembank.money;

import org.bukkit.Bukkit;

public final class MoneyFinder {

    private MoneyFinder() {
    }

    /** Vault first, then CMI directly. null if neither works. */
    public static Money find() {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                Money m = VaultMoney.find();
                if (m != null) return m;
            } catch (LinkageError ignored) {
            }
        }
        org.bukkit.plugin.Plugin cmi = Bukkit.getPluginManager().getPlugin("CMI");
        if (cmi != null && cmi.isEnabled()) {
            return CmiMoney.find(cmi.getClass().getClassLoader());
        }
        return null;
    }
}
