package com.alakaiigaming.itembank.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after a bank trade is taxed. The money has already moved. */
public final class TaxCollectedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player payer;
    private final UUID receiver;
    private final double amount;
    private final String material;
    private final boolean sale;
    private final UUID bankOwner;

    public TaxCollectedEvent(Player payer, UUID receiver, double amount, String material, boolean sale, UUID bankOwner) {
        this.payer = payer;
        this.receiver = receiver;
        this.amount = amount;
        this.material = material;
        this.sale = sale;
        this.bankOwner = bankOwner;
    }

    /** The player who traded. */
    public Player getPayer() { return payer; }
    /** Who got the tax, or null if it was removed from the economy. */
    public UUID getReceiver() { return receiver; }
    public double getAmount() { return amount; }
    /** Config key of the material, e.g. iron_ingot. */
    public String getMaterial() { return material; }
    /** true when the player sold to the bank, false when they bought. */
    public boolean isSale() { return sale; }
    /** Owner of the bank, or null for a server bank. */
    public UUID getBankOwner() { return bankOwner; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
