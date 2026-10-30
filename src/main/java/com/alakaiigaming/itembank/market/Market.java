package com.alakaiigaming.itembank.market;

/**
 * Price math for one material. No Bukkit code in here so it can be tested on its own.
 *
 * price(stock) = base * index * 2 ^ ((normal - stock) / step), clamped to [min, max] * base * index
 */
public final class Market {

    public final String key;
    public final double base;
    public final long normalStock;
    public final double step;
    public final long maxStock;

    private final PriceRules rules;

    public long stock;
    public boolean frozen;
    public boolean disabled;
    /** Price kept while frozen, before index. */
    public double frozenPrice = Double.NaN;
    /** Per-player sell cap for this material, NaN = use the global one. */
    public double payoutCap = Double.NaN;
    /** Mid price at the last daily update, for the "today" column. */
    public double lastDailyPrice = Double.NaN;
    /** Fraction of an item left over from stock settling, so slow drift still adds up. */
    public double carry;

    /**
     * Moves stock part of the way back toward normal. {@code surplus} and {@code shortage} are the
     * share of the gap to close in this call. Returns how many whole items changed.
     */
    public long settle(double surplus, double shortage) {
        long gap = normalStock - stock;
        if (gap == 0) return 0;
        double move = gap * (gap < 0 ? surplus : shortage) + carry;
        long whole = (long) move;
        carry = move - whole;
        if (whole == 0) return 0;
        if (gap < 0 && stock + whole < normalStock) whole = normalStock - stock;
        if (gap > 0 && stock + whole > normalStock) whole = normalStock - stock;
        stock += whole;
        return whole;
    }

    public Market(String key, double base, long normalStock, double step, long maxStock, long startStock, PriceRules rules) {
        this.key = key;
        this.base = base;
        this.normalStock = normalStock;
        this.step = step <= 0 ? 1 : step;
        this.maxStock = maxStock;
        this.stock = startStock;
        this.rules = rules;
    }

    /** Mid price for one item when the bank holds {@code atStock}. */
    public double price(double atStock) {
        double b = base * rules.index();
        if (frozen && !Double.isNaN(frozenPrice)) return frozenPrice * rules.index();
        double p = b * Math.pow(2, (normalStock - atStock) / step);
        return Math.max(b * rules.minMultiplier(), Math.min(b * rules.maxMultiplier(), p));
    }

    public double price() {
        return price(stock);
    }

    public double sellPrice() {
        return price() * (1 - rules.spread() / 2);
    }

    public double buyPrice() {
        return price() * (1 + rules.spread() / 2);
    }

    /** What a player gets for selling {@code n} items right now. */
    public double sellTotal(long n) {
        double sum = 0;
        if (rules.curve()) {
            for (long i = 0; i < n; i++) sum += price(stock + i);
        } else {
            sum = price() * n;
        }
        return floor2(sum * (1 - rules.spread() / 2));
    }

    /** What a player pays to buy {@code n} items right now. */
    public double buyTotal(long n) {
        double sum = 0;
        if (rules.curve()) {
            for (long i = 0; i < n; i++) sum += price(stock - 1 - i);
        } else {
            sum = price() * n;
        }
        return ceil2(sum * (1 + rules.spread() / 2));
    }

    /** Room left before the bank stops buying. Long.MAX_VALUE when there's no cap. */
    public long room() {
        if (maxStock <= 0) return Long.MAX_VALUE;
        return Math.max(0, maxStock - stock);
    }

    public void freeze(boolean on) {
        frozen = on;
        frozenPrice = on ? price() / rules.index() : Double.NaN;
    }

    static double floor2(double v) {
        return Math.floor(v * 100 + 1e-9) / 100;
    }

    static double ceil2(double v) {
        return Math.ceil(v * 100 - 1e-9) / 100;
    }
}
