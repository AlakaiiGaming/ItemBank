package com.alakaiigaming.itembank.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after the daily money supply check and price update. */
public final class DailyUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final double moneySupply;
    private final double change;
    private final double index;

    public DailyUpdateEvent(double moneySupply, double change, double index) {
        this.moneySupply = moneySupply;
        this.change = change;
        this.index = index;
    }

    /** Total money on the server at the check, NaN if there's no economy. */
    public double getMoneySupply() { return moneySupply; }
    /** Price multiplier applied today, e.g. 1.02 for +2%. */
    public double getChange() { return change; }
    /** Price index after the update (1 = starting prices). */
    public double getIndex() { return index; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
