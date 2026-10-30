package com.alakaiigaming.itembank.market;

/** Settings shared by every market. */
public interface PriceRules {
    /** Total gap between buy and sell, 0.12 = 12%. */
    double spread();
    double minMultiplier();
    double maxMultiplier();
    /** true = big trades follow the curve, false = flat. */
    boolean curve();
    /** Inflation multiplier, starts at 1. */
    double index();
}
