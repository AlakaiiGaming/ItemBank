package com.alakaiigaming.itembank.market;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stops farms from cashing in huge piles at once.
 * Per player, per material: caps money (and optionally items) earned from selling within a time window.
 * Everyone: if too much of one material is sold within the window, the bank stops buying it for a while.
 * No Bukkit code in here so it can be tested on its own.
 */
public final class SellGuard {

    public static final class Rules {
        public boolean enabled = true;
        public long windowMillis = 60 * 60_000L;
        public long playerMaxItems;      // per material, 0 = off
        public double playerItemsPercent;   // per material, % of its normal stock, 0 = off
        public double playerMaxPayout;   // per material, 0 = off (a material can override it)
        public double everyonePercent;   // of normal stock, 0 = off
        public long closeMillis = 120 * 60_000L;
    }

    private record Sale(long time, String key, long items, double money) {}

    private final Rules rules;
    private final Map<UUID, Deque<Sale>> byPlayer = new HashMap<>();
    private final Map<String, Deque<Sale>> byMaterial = new HashMap<>();
    /** material -> time selling opens again */
    public final Map<String, Long> closedUntil = new HashMap<>();

    public SellGuard(Rules rules) {
        this.rules = rules;
    }

    public Rules rules() {
        return rules;
    }

    private void prune(Deque<Sale> q, long now) {
        while (!q.isEmpty() && q.peekFirst().time() <= now - rules.windowMillis) q.pollFirst();
    }

    /** Millis until selling this material opens again, 0 if open. */
    public long closedFor(String key, long now) {
        if (!rules.enabled) return 0;
        Long until = closedUntil.get(key);
        if (until == null) return 0;
        if (until <= now) {
            closedUntil.remove(key);
            return 0;
        }
        return until - now;
    }

    public void reopen(String key) {
        closedUntil.remove(key);
        Deque<Sale> q = byMaterial.get(key);
        if (q != null) q.clear();
    }

    /**
     * Items everyone can still sell before the bank closes this material. Long.MAX_VALUE if no cap.
     * The cap only counts once the bank already holds more than its normal stock: filling an empty
     * bank is what we want, flooding a full one isn't.
     */
    public long everyoneLeft(Market m, long now) {
        if (!rules.enabled || rules.everyonePercent <= 0) return Long.MAX_VALUE;
        if (m.stock < m.normalStock) return Long.MAX_VALUE;
        long cap = Math.max(1, Math.round(m.normalStock * rules.everyonePercent / 100.0));
        Deque<Sale> q = byMaterial.computeIfAbsent(m.key, k -> new ArrayDeque<>());
        prune(q, now);
        long sold = 0;
        for (Sale s : q) sold += s.items();
        return Math.max(0, cap - sold);
    }

    /** Items this player can still sell of this material in the window. */
    public long playerItemsLeft(UUID player, Market m, long now) {
        if (!rules.enabled) return Long.MAX_VALUE;
        long cap = Long.MAX_VALUE;
        if (rules.playerMaxItems > 0) cap = rules.playerMaxItems;
        if (rules.playerItemsPercent > 0) {
            cap = Math.min(cap, Math.max(1, Math.round(m.normalStock * rules.playerItemsPercent / 100.0)));
        }
        if (cap == Long.MAX_VALUE) return cap;
        Deque<Sale> q = byPlayer.computeIfAbsent(player, k -> new ArrayDeque<>());
        prune(q, now);
        long sold = 0;
        for (Sale s : q) if (s.key().equals(m.key)) sold += s.items();
        return Math.max(0, cap - sold);
    }

    private double cap(Market m) {
        return Double.isNaN(m.payoutCap) ? rules.playerMaxPayout : m.payoutCap;
    }

    /** Money this player can still earn selling this material in the window. */
    public double playerMoneyLeft(UUID player, Market m, long now) {
        double cap = cap(m);
        if (!rules.enabled || cap <= 0) return Double.MAX_VALUE;
        Deque<Sale> q = byPlayer.computeIfAbsent(player, k -> new ArrayDeque<>());
        prune(q, now);
        double earned = 0;
        for (Sale s : q) if (s.key().equals(m.key)) earned += s.money();
        return Math.max(0, cap - earned);
    }

    /** Millis until this player's oldest sale of this material leaves the window. */
    public long playerWait(UUID player, String key, long now) {
        Deque<Sale> q = byPlayer.get(player);
        if (q == null) return 0;
        for (Sale s : q) {
            if (s.key().equals(key)) return Math.max(0, s.time() + rules.windowMillis - now);
        }
        return 0;
    }

    /** Largest amount up to {@code n} this player may sell right now. */
    public long allowed(UUID player, Market m, long n, long now) {
        if (!rules.enabled) return n;
        if (closedFor(m.key, now) > 0) return 0;
        n = Math.min(n, everyoneLeft(m, now));
        n = Math.min(n, playerItemsLeft(player, m, now));
        double money = playerMoneyLeft(player, m, now);
        if (money < Double.MAX_VALUE && m.sellTotal(n) > money) {
            long lo = 0, hi = n;
            while (lo < hi) {
                long mid = (lo + hi + 1) / 2;
                if (m.sellTotal(mid) <= money) lo = mid; else hi = mid - 1;
            }
            n = lo;
        }
        return Math.max(0, n);
    }

    /** Records a sale. Returns true if it just closed the material for everyone. */
    public boolean record(UUID player, Market m, long items, double money, long now) {
        if (!rules.enabled) return false;
        Sale sale = new Sale(now, m.key, items, money);
        byPlayer.computeIfAbsent(player, k -> new ArrayDeque<>()).addLast(sale);
        byMaterial.computeIfAbsent(m.key, k -> new ArrayDeque<>()).addLast(sale);
        if (rules.everyonePercent > 0 && everyoneLeft(m, now) == 0) {
            closedUntil.put(m.key, now + rules.closeMillis);
            byMaterial.get(m.key).clear();
            return true;
        }
        return false;
    }

    public static String time(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        long h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
