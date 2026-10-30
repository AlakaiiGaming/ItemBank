package com.alakaiigaming.itembank.market;

import java.util.UUID;

/**
 * Tax on bank trades. Sellers get the price minus tax, buyers pay the price plus tax.
 * The tax goes to the receiver, or disappears if there isn't one.
 */
public final class Tax {

    public boolean enabled;
    public double sellRate;      // 0.05 = 5%
    public double buyRate;
    public double maxRate = 0.5;
    public UUID receiver;        // null = tax money is removed from the economy
    public boolean playerBanks = true;

    /** What one trade does with money. */
    public record Split(double player, double tax, double owner, double created) {}

    public boolean applies(boolean serverBank) {
        return enabled && (serverBank || playerBanks);
    }

    /**
     * A player sells items worth {@code gross}.
     * player = what the seller gets, owner = what a player bank's owner pays,
     * created = money that didn't exist before (negative = money removed).
     */
    public Split sell(double gross, boolean serverBank) {
        double tax = applies(serverBank) ? floor2(gross * sellRate) : 0;
        double burned = receiver == null ? tax : 0;
        double created = (serverBank ? gross : 0) - burned;
        return new Split(gross - tax, tax, serverBank ? 0 : gross, created);
    }

    /**
     * A player buys items costing {@code cost}.
     * player = what the buyer pays, owner = what a player bank's owner gets.
     */
    public Split buy(double cost, boolean serverBank) {
        double tax = applies(serverBank) ? ceil2(cost * buyRate) : 0;
        double burned = receiver == null ? tax : 0;
        double created = -(serverBank ? cost : 0) - burned;
        return new Split(cost + tax, tax, serverBank ? 0 : cost, created);
    }

    public double clampRate(double rate) {
        return Math.max(0, Math.min(maxRate, rate));
    }

    static double floor2(double v) {
        return Math.floor(v * 100 + 1e-9) / 100.0;
    }

    static double ceil2(double v) {
        return Math.ceil(v * 100 - 1e-9) / 100.0;
    }
}
