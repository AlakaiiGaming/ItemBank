package com.alakaiigaming.itembank.bank;

import com.alakaiigaming.itembank.ItemBank;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public final class BankListener implements Listener {

    private final ItemBank plugin;

    public BankListener(ItemBank plugin) {
        this.plugin = plugin;
    }

    private Bank bankAt(Block b) {
        return b == null ? null : plugin.banks().at(b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event instanceof ProtectionCheck) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Bank bank = bankAt(event.getClickedBlock());
        if (bank == null) return;

        event.setCancelled(true);
        Player p = event.getPlayer();

        // owners (and admins) open the real chest by sneaking, to restock or collect
        boolean owner = !bank.isServer() && bank.owner.equals(p.getUniqueId());
        if (p.isSneaking() && (owner || (!bank.isServer() && p.hasPermission("itembank.admin")))) {
            if (bank.inventory() != null) p.openInventory(bank.inventory());
            return;
        }
        if (!p.hasPermission("itembank.use")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        p.openInventory(new BankMenu(plugin, bank, p).getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof BankMenu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            menu.click(event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BankMenu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (bankAt(event.getBlock()) != null) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "protected");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        // a chest placed next to a bank can turn it into a double chest. Only the bank's owner
        // (or an admin) may do that, otherwise anyone could grow a bank into their own chest
        if (!(event.getBlock().getState() instanceof org.bukkit.block.Chest)) return;
        Player p = event.getPlayer();
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Bank bank = bankAt(event.getBlock().getRelative(face));
            if (bank == null) continue;
            boolean owner = !bank.isServer() && bank.owner.equals(p.getUniqueId());
            if (!owner && !p.hasPermission(bank.isServer() ? "itembank.create" : "itembank.admin")) {
                event.setCancelled(true);
                plugin.messages().send(p, "protected");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.banks().recheckHalves(bank));
        }
    }

    private void protect(List<Block> blocks) {
        blocks.removeIf(b -> bankAt(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (bankAt(event.getBlock()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (bankAt(event.getBlock()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) if (bankAt(b) != null) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) if (bankAt(b) != null) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (isBank(event.getSource().getHolder()) || isBank(event.getDestination().getHolder())) {
            event.setCancelled(true);
        }
    }

    private boolean isBank(InventoryHolder holder) {
        if (holder instanceof org.bukkit.block.BlockState state) return bankAt(state.getBlock()) != null;
        if (holder instanceof org.bukkit.block.DoubleChest dc && dc.getLocation() != null) {
            return isBank(dc.getLeftSide()) || isBank(dc.getRightSide());
        }
        return false;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if ("hologram".equals(plugin.settings().displayType) || plugin.settings().floatingItem) {
            plugin.banks().chunkLoaded(event.getChunk());
        }
    }
}
