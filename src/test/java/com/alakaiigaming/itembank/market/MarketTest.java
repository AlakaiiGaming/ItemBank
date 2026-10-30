package com.alakaiigaming.itembank.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketTest {

    static final class Rules implements PriceRules {
        boolean curve = true;
        double index = 1;
        public double spread() { return 0.12; }
        public double minMultiplier() { return 0.25; }
        public double maxMultiplier() { return 4.0; }
        public boolean curve() { return curve; }
        public double index() { return index; }
    }

    static Market iron(Rules r, long stock) {
        return new Market("iron_ingot", 8, 500, 250, 2000, stock, r);
    }

    @Test
    void pricesFollowStock() {
        Rules r = new Rules();
        assertEquals(8, iron(r, 500).price(), 1e-9);
        assertEquals(4, iron(r, 750).price(), 1e-9);
        assertEquals(16, iron(r, 250).price(), 1e-9);
        assertEquals(2, iron(r, 5000).price(), 1e-9);   // floor
        assertEquals(32, iron(r, 0).price(), 1e-9);     // ceiling
    }

    @Test
    void splittingAStackPaysTheSame() {
        Rules r = new Rules();
        Market whole = iron(r, 500);
        double all = whole.sellTotal(64);

        Market split = iron(r, 500);
        double sum = 0;
        for (int i = 0; i < 16; i++) {
            sum += split.sellTotal(4);
            split.stock += 4;
        }
        assertEquals(all, sum, 0.2);   // only rounding to cents differs
    }

    // only holds for curve pricing, which is why flat has a warning in the config
    @Test
    void roundTripsAlwaysLose() {
        Rules r = new Rules();
        for (long stock : new long[]{0, 10, 50, 300, 500, 900, 3000}) {
            for (long n : new long[]{1, 16, 64, 640}) {
                Market m = iron(r, stock);
                double got = m.sellTotal(n);
                m.stock += n;
                double paid = m.buyTotal(n);
                assertTrue(paid > got, "sell then buy made money at stock " + stock + " x" + n);

                Market b = iron(r, stock + n);
                double cost = b.buyTotal(n);
                b.stock -= n;
                double back = b.sellTotal(n);
                assertTrue(cost > back, "buy then sell made money at stock " + stock + " x" + n);
            }
        }
    }

    @Test
    void inflationScalesPrices() {
        Rules r = new Rules();
        r.index = 1.5;
        assertEquals(12, iron(r, 500).price(), 1e-9);
    }

    @Test
    void bankPayoutsDontCauseInflation() {
        Inflation inf = new Inflation();
        double total = 100_000;
        double index = 1;
        for (int day = 0; day < 30; day++) {
            inf.recordTrade(5_000, 0);   // a farm sells to the bank
            total += 5_000;
            index *= inf.dailyFactor(total, 10, 7, 0.03, index);
        }
        assertEquals(1, index, 1e-9);
    }

    @Test
    void dailyChangeIsCapped() {
        Inflation inf = new Inflation();
        inf.dailyFactor(100_000, 10, 7, 0.03, 1);
        assertEquals(1.03, inf.dailyFactor(200_000, 10, 7, 0.03, 1), 1e-9);
    }

    @Test
    void printedMoneyIsFullyPricedIn() {
        Inflation inf = new Inflation();
        double index = 1;
        index *= inf.dailyFactor(100_000, 10, 7, 0.03, index);
        for (int day = 0; day < 60; day++) index *= inf.dailyFactor(200_000, 10, 7, 0.03, index);
        assertEquals(2, index, 1e-6);   // money doubled, prices doubled
        // and if the money is taken back out, prices come back down
        for (int day = 0; day < 60; day++) index *= inf.dailyFactor(100_000, 10, 7, 0.03, index);
        assertEquals(1, index, 1e-6);
    }

    @Test
    void newPlayersArentInflation() {
        Inflation inf = new Inflation();
        double index = 1;
        index *= inf.dailyFactor(10_000, 10, 7, 0.03, index);
        for (int day = 0; day < 30; day++) index *= inf.dailyFactor(20_000, 20, 7, 0.03, index);
        assertEquals(1, index, 1e-9);
    }

    @Test
    void surplusStockSettlesBackToNormal() {
        Market m = iron(new Rules(), 2000);   // 1500 over normal
        double perMinute = 1 - Math.pow(0.5, 1.0 / 1440);   // 50% a day
        long moved = 0;
        for (int i = 0; i < 1440; i++) moved += m.settle(perMinute, 0);
        assertEquals(-750, moved, 2);   // 750 left the bank
        assertEquals(1250, m.stock, 2);
        // shortage isn't touched when restock is off
        Market low = iron(new Rules(), 100);
        for (int i = 0; i < 1440; i++) low.settle(perMinute, 0);
        assertEquals(100, low.stock, 0);
        // never overshoots
        Market near = iron(new Rules(), 501);
        for (int i = 0; i < 100_000; i++) near.settle(1, 0);
        assertEquals(500, near.stock, 0);
    }

    @Test
    void everyoneCapOnlyAboveNormal() {
        SellGuard.Rules rules = new SellGuard.Rules();
        rules.everyonePercent = 50;
        SellGuard guard = new SellGuard(rules);
        Market empty = iron(new Rules(), 20);
        assertEquals(10_000, guard.allowed(java.util.UUID.randomUUID(), empty, 10_000, 0), 0);
        Market full = iron(new Rules(), 600);
        assertEquals(250, guard.allowed(java.util.UUID.randomUUID(), full, 10_000, 0), 0);
    }

    @Test
    void taxSplitsTheMoney() {
        Tax tax = new Tax();
        tax.enabled = true;
        tax.sellRate = 0.10;
        tax.buyRate = 0.05;
        tax.receiver = java.util.UUID.randomUUID();

        Tax.Split sell = tax.sell(100, true);
        assertEquals(90, sell.player(), 1e-9);
        assertEquals(10, sell.tax(), 1e-9);
        assertEquals(100, sell.created(), 1e-9);   // bank made 100, tax just moved to the receiver

        Tax.Split buy = tax.buy(100, true);
        assertEquals(105, buy.player(), 1e-9);
        assertEquals(-100, buy.created(), 1e-9);

        // player bank: owner pays/gets the price, no money is created
        Tax.Split ps = tax.sell(100, false);
        assertEquals(100, ps.owner(), 1e-9);
        assertEquals(0, ps.created(), 1e-9);
        Tax.Split pb = tax.buy(100, false);
        assertEquals(100, pb.owner(), 1e-9);
        assertEquals(0, pb.created(), 1e-9);

        // no receiver: the tax is removed from the economy
        tax.receiver = null;
        assertEquals(90, tax.sell(100, true).created(), 1e-9);
        assertEquals(-5, tax.buy(100, false).created(), 1e-9);

        tax.enabled = false;
        assertEquals(100, tax.sell(100, true).player(), 1e-9);
    }

    @Test
    void taxDoesntMoveInflation() {
        Tax tax = new Tax();
        tax.enabled = true;
        tax.sellRate = 0.2;
        Inflation inf = new Inflation();
        double total = 100_000, index = 1;
        inf.dailyFactor(total, 10, 7, 0.03, index);
        for (int day = 0; day < 20; day++) {
            Tax.Split s = tax.sell(1000, false);   // burned tax at a player bank
            total -= s.tax();
            inf.record(s.created());
            index *= inf.dailyFactor(total, 10, 7, 0.03, index);
        }
        assertEquals(1, index, 1e-9);
    }

    @Test
    void taxRateIsCapped() {
        Tax tax = new Tax();
        tax.maxRate = 0.25;
        assertEquals(0.25, tax.clampRate(0.9), 1e-9);
        assertEquals(0, tax.clampRate(-1), 1e-9);
    }

    @Test
    void playerPayoutIsCapped() {
        SellGuard.Rules rules = new SellGuard.Rules();
        rules.playerMaxPayout = 1000;
        rules.everyonePercent = 0;
        SellGuard guard = new SellGuard(rules);
        Market m = iron(new Rules(), 500);
        java.util.UUID p = java.util.UUID.randomUUID();

        long n = guard.allowed(p, m, 10_000, 0);
        assertTrue(m.sellTotal(n) <= 1000, "paid more than the cap");
        assertTrue(m.sellTotal(n + 1) > 1000, "stopped early");
        guard.record(p, m, n, m.sellTotal(n), 0);
        m.stock += n;
        assertEquals(0, guard.allowed(p, m, 64, 1000), 0);
        // other materials aren't affected
        Market gold = new Market("gold_ingot", 20, 300, 150, 1200, 30, new Rules());
        assertTrue(guard.allowed(p, gold, 64, 1000) > 0, "cooldown spilled over to gold");
        // an hour later they can sell again
        assertTrue(guard.allowed(p, m, 64, 60 * 60_000L + 1) > 0, "never reset");
    }

    @Test
    void materialCanOverrideTheCap() {
        SellGuard.Rules rules = new SellGuard.Rules();
        rules.playerMaxPayout = 1000;
        rules.everyonePercent = 0;
        SellGuard guard = new SellGuard(rules);
        Market m = iron(new Rules(), 500);
        m.payoutCap = 3000;
        long n = guard.allowed(java.util.UUID.randomUUID(), m, 10_000, 0);
        assertTrue(m.sellTotal(n) > 1000 && m.sellTotal(n) <= 3000, "override ignored");
    }

    @Test
    void massSellOffClosesTheMaterial() {
        SellGuard.Rules rules = new SellGuard.Rules();
        rules.playerMaxPayout = 0;
        rules.everyonePercent = 50;   // 250 iron
        SellGuard guard = new SellGuard(rules);
        Market m = iron(new Rules(), 500);
        m.stock = 500;

        long sold = 0;
        boolean closed = false;
        for (int i = 0; i < 10 && !closed; i++) {
            java.util.UUID p = java.util.UUID.randomUUID();
            long n = guard.allowed(p, m, 64, i);
            closed = guard.record(p, m, n, m.sellTotal(n), i);
            sold += n;
        }
        assertTrue(closed, "never closed");
        assertEquals(250, sold, 0);
        assertTrue(guard.closedFor(m.key, 100) > 0, "not closed");
        assertEquals(0, guard.closedFor(m.key, 121 * 60_000L), 0);
    }

    @Test
    void itemPercentCapScalesWithTheMaterial() {
        SellGuard.Rules rules = new SellGuard.Rules();
        rules.playerItemsPercent = 25;
        rules.everyonePercent = 0;
        SellGuard guard = new SellGuard(rules);
        java.util.UUID p = java.util.UUID.randomUUID();
        Market coal = new Market("coal", 0.5, 1000, 1000, 4000, 100, new Rules());
        Market diamond = new Market("diamond", 30, 100, 100, 400, 10, new Rules());
        assertEquals(250, guard.allowed(p, coal, 10_000, 0), 0);
        assertEquals(25, guard.allowed(p, diamond, 10_000, 0), 0);
    }

    @Test
    void twoStacksOfCoalArentAJackpot() {
        Rules r = new Rules();
        Market coal = new Market("coal", 0.5, 1000, 1000, 4000, 100, r);
        assertTrue(coal.sellTotal(128) < 120, "2 stacks of coal paid " + coal.sellTotal(128));
    }
}
