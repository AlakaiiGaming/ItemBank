package com.alakaiigaming.itembank.market;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the money supply once a day and moves prices to follow it.
 * If the total money on the server doubles, prices end up doubled too, moving at most
 * max-change per day so it happens over a few weeks instead of overnight.
 *
 * Money the banks create or remove themselves (payouts, purchases, burned tax) doesn't count,
 * otherwise a farm selling to the bank would push prices up forever. So this picks up money
 * that comes from anywhere else: /eco give, jobs, votes, a dictator printing money.
 */
public final class Inflation {

    /** Net money this plugin has created (paid out minus taken in). */
    public double issued;
    /** One entry per day: total money minus issued. Oldest first. */
    public final List<Double> history = new ArrayList<>();
    /** Money supply (minus issued) that matches a price index of 1. NaN until the first check. */
    public double baseline = Double.NaN;
    /** Last measured total money of active players, NaN if never checked. */
    public double lastTotal = Double.NaN;
    /** How many active players that total covered. */
    public int lastPlayers;

    public void record(double created) {
        issued += created;
    }

    public void recordTrade(double paidOut, double takenIn) {
        issued += paidOut - takenIn;
    }

    /**
     * Adds today's money total and returns the multiplier to apply to the price index today,
     * e.g. 1.01 for +1%.
     *
     * Works per player, so new players joining with a starting balance don't count as inflation.
     *
     * @param players  how many active players the total covers
     * @param days     how many days of checks to average, so a one-day spike doesn't jerk prices
     * @param index    the price index right now
     */
    public double dailyFactor(double totalMoney, int players, int days, double maxChange, double index) {
        lastTotal = totalMoney;
        lastPlayers = players;
        double adjusted = (totalMoney - issued) / Math.max(1, players);
        if (adjusted <= 0 || Double.isNaN(adjusted) || index <= 0) return 1;
        history.add(adjusted);
        while (history.size() > Math.max(1, days)) history.remove(0);

        // first check: whatever the economy is now is "normal" for today's prices
        if (Double.isNaN(baseline) || baseline <= 0) {
            baseline = adjusted / index;
            return 1;
        }
        double average = 0;
        for (double d : history) average += d;
        average /= history.size();

        double target = average / baseline;
        double factor = target / index;
        return Math.max(1 - maxChange, Math.min(1 + maxChange, factor));
    }

    /** Makes today's money the new normal for today's prices. */
    public void rebase(double index) {
        issued = 0;
        history.clear();
        baseline = Double.NaN;
        if (!Double.isNaN(lastTotal) && lastTotal > 0 && index > 0) {
            baseline = lastTotal / Math.max(1, lastPlayers) / index;
        }
    }

    /** Percent the money supply (not counting the banks) changed over the stored history. */
    public double supplyChange() {
        if (history.size() < 2 || history.get(0) <= 0) return 0;
        return history.get(history.size() - 1) / history.get(0) - 1;
    }
}
