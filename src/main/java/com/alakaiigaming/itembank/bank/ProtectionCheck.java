package com.alakaiigaming.itembank.bank;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * A fake right-click on a chest, so claim and lock plugins can say no before a player
 * turns someone else's chest into their bank.
 */
public final class ProtectionCheck extends PlayerInteractEvent {

    private ProtectionCheck(Player player, Block block) {
        super(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), block, BlockFace.UP, EquipmentSlot.HAND);
    }

    /** Both halves of a double chest have to be allowed. */
    public static boolean canUse(Player player, Block block) {
        if (!allowed(player, block)) return false;
        org.bukkit.Location other = BankManager.otherHalf(block);
        return other == null || allowed(player, other.getBlock());
    }

    private static boolean allowed(Player player, Block block) {
        ProtectionCheck check = new ProtectionCheck(player, block);
        Bukkit.getPluginManager().callEvent(check);
        return !check.isCancelled() && check.useInteractedBlock() != Event.Result.DENY;
    }
}
