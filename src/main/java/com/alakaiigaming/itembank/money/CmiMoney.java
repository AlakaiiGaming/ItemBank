package com.alakaiigaming.itembank.money;

import org.bukkit.OfflinePlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.UUID;

/**
 * Talks to CMI's own money directly, for servers without Vault.
 * Uses method handles so CMI isn't needed to build, and so CMI's Vault-only methods are never touched.
 */
final class CmiMoney implements Money {

    private final Object playerManager;
    private final Object economyManager;
    private final MethodHandle getUser;
    private final MethodHandle getBalance;
    private final MethodHandle deposit;
    private final MethodHandle withdraw;
    private final MethodHandle format;

    private CmiMoney(Object playerManager, Object economyManager, MethodHandle getUser, MethodHandle getBalance,
                     MethodHandle deposit, MethodHandle withdraw, MethodHandle format) {
        this.playerManager = playerManager;
        this.economyManager = economyManager;
        this.getUser = getUser;
        this.getBalance = getBalance;
        this.deposit = deposit;
        this.withdraw = withdraw;
        this.format = format;
    }

    static Money find(ClassLoader loader) {
        try {
            MethodHandles.Lookup lk = MethodHandles.publicLookup();
            Class<?> cmi = Class.forName("com.Zrips.CMI.CMI", false, loader);
            Class<?> em = Class.forName("com.Zrips.CMI.Modules.Economy.EconomyManager", false, loader);
            Class<?> pm = Class.forName("com.Zrips.CMI.PlayerManager", false, loader);
            Class<?> user = Class.forName("com.Zrips.CMI.Containers.CMIUser", false, loader);

            Object instance = lk.findStatic(cmi, "getInstance", MethodType.methodType(cmi)).invoke();
            Object economy = lk.findVirtual(cmi, "getEconomyManager", MethodType.methodType(em)).invoke(instance);
            boolean enabled = (boolean) lk.findVirtual(em, "isEnabled", MethodType.methodType(boolean.class)).invoke(economy);
            if (!enabled) return null;
            Object players = lk.findVirtual(cmi, "getPlayerManager", MethodType.methodType(pm)).invoke(instance);

            return new CmiMoney(players, economy,
                    lk.findVirtual(pm, "getUser", MethodType.methodType(user, UUID.class)),
                    lk.findVirtual(user, "getBalance", MethodType.methodType(Double.class)),
                    lk.findVirtual(user, "deposit", MethodType.methodType(Double.class, Double.class)),
                    lk.findVirtual(user, "withdraw", MethodType.methodType(Double.class, Double.class)),
                    lk.findVirtual(em, "format", MethodType.methodType(String.class, Double.class)));
        } catch (Throwable e) {
            return null;
        }
    }

    private Object user(OfflinePlayer p) throws Throwable {
        return getUser.invoke(playerManager, p.getUniqueId());
    }

    @Override
    public String name() {
        return "CMI";
    }

    @Override
    public double balance(OfflinePlayer p) {
        try {
            Object user = user(p);
            if (user == null) return 0;
            Double v = (Double) getBalance.invoke(user);
            return v == null ? 0 : v;
        } catch (Throwable e) {
            return 0;
        }
    }

    @Override
    public boolean withdraw(OfflinePlayer p, double amount) {
        if (amount <= 0) return true;
        if (balance(p) < amount) return false;
        return change(p, withdraw, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer p, double amount) {
        if (amount <= 0) return true;
        return change(p, deposit, amount);
    }

    private boolean change(OfflinePlayer p, MethodHandle how, double amount) {
        try {
            Object user = user(p);
            if (user == null) return false;
            double before = balance(p);
            how.invoke(user, Double.valueOf(amount));
            return Math.abs(balance(p) - before) > 1e-9;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        try {
            return (String) format.invoke(economyManager, Double.valueOf(Math.round(amount * 100) / 100.0));
        } catch (Throwable e) {
            return String.format(Locale.ROOT, "%,.2f", amount);
        }
    }
}
