package com.alakaiigaming.itembank.bank;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Counting, taking and giving a material, with its block worth 9. Only plain items count. */
public final class Items {

    private Items() {
    }

    public static boolean plain(ItemStack stack, Material type) {
        return stack != null && stack.getType() == type && stack.isSimilar(new ItemStack(type));
    }

    private static ItemStack[] slots(Inventory inv) {
        // storage only, so armor and offhand are never touched
        return inv.getStorageContents();
    }

    /** Total in items, blocks counted as 9. */
    public static long count(Inventory inv, Material item, Material block) {
        long total = 0;
        for (ItemStack s : slots(inv)) {
            if (plain(s, item)) total += s.getAmount();
            else if (block != null && plain(s, block)) total += s.getAmount() * 9L;
        }
        return total;
    }

    /**
     * Takes {@code amount} items, loose items first, then breaks blocks and hands back change.
     * Returns change that didn't fit (rare: a completely full inventory).
     */
    public static long take(Inventory inv, Material item, Material block, long amount) {
        long left = amount;
        ItemStack[] contents = slots(inv);
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (!plain(s, item)) continue;
            int use = (int) Math.min(left, s.getAmount());
            s.setAmount(s.getAmount() - use);
            contents[i] = s.getAmount() <= 0 ? null : s;
            left -= use;
        }
        long change = 0;
        if (left > 0 && block != null) {
            for (int i = 0; i < contents.length && left > 0; i++) {
                ItemStack s = contents[i];
                if (!plain(s, block)) continue;
                long blocksNeeded = (left + 8) / 9;
                int use = (int) Math.min(blocksNeeded, s.getAmount());
                s.setAmount(s.getAmount() - use);
                contents[i] = s.getAmount() <= 0 ? null : s;
                long value = use * 9L;
                if (value > left) change += value - left;
                left = Math.max(0, left - value);
            }
        }
        inv.setStorageContents(contents);
        return change > 0 ? give(inv, item, change) : 0;
    }

    /** How many loose items fit. */
    public static long room(Inventory inv, Material item) {
        long room = 0;
        int max = item.getMaxStackSize();
        for (ItemStack s : slots(inv)) {
            if (s == null || s.getType().isAir()) room += max;
            else if (plain(s, item)) room += max - s.getAmount();
        }
        return room;
    }

    /** Gives loose items. Anything that doesn't fit is returned as the leftover count. */
    public static long give(Inventory inv, Material item, long amount) {
        long left = amount;
        int max = item.getMaxStackSize();
        ItemStack[] contents = slots(inv);
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (plain(s, item) && s.getAmount() < max) {
                int add = (int) Math.min(left, max - s.getAmount());
                s.setAmount(s.getAmount() + add);
                left -= add;
            }
        }
        for (int i = 0; i < contents.length && left > 0; i++) {
            if (contents[i] == null || contents[i].getType().isAir()) {
                int add = (int) Math.min(left, max);
                contents[i] = new ItemStack(item, add);
                left -= add;
            }
        }
        inv.setStorageContents(contents);
        return left;
    }
}
