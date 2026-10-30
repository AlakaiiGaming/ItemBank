# Item Bank

I made this for my server DictatorCraft because every shop plugin I tried had fixed prices, and players would just farm one thing and break the economy in a week.

Item Bank turns chests into banks that buy and sell one material each. If people sell a lot of something the price goes down, if they buy a lot it goes up. Once a day it also checks how much money is on the server and moves prices with it, so if someone prints money, things get more expensive. Like a real economy.

Needs Paper or Purpur 1.21 or newer (I run it on 26.x), plus CMI or Vault with an economy plugin like EssentialsX.

## Install

Grab the jar from [Releases](https://github.com/AlakaiiGaming/ItemBank/releases), put it in your plugins folder and restart. That's it.

## Making a bank

Look at a chest and type `/ibank create iron_ingot`. Players right-click it and get a menu to buy and sell. The item spins above the chest and a sign shows the price.

**Player banks:** give players `itembank.create.own` and they can make their own bank on their own chest. It sells whatever's in the chest and pays sellers out of the owner's money. The owner keeps the difference between buy and sell. To restock, sneak + right-click the chest. They can't do it on chests they don't have access to (works with claim and lock plugins). You can limit how many they get and charge for them in the config.

## Commands

| Command | What it does | Permission |
| --- | --- | --- |
| `/ibank prices` | all prices, stock and today's change | `itembank.use` |
| `/ibank create <material>` | make a bank (server bank, or your own) | `itembank.create` or `itembank.create.own` |
| `/ibank create <material> <player>` | make a bank for someone | `itembank.create.player` |
| `/ibank list [player\|server\|all]` | list banks and their ids | your own, `itembank.admin` for others |
| `/ibank info [id]` | stock, trades today and all time, tax, last 10 trades | owner or `itembank.admin` |
| `/ibank remove [id]` | remove a bank | owner, `itembank.create` for server banks, `itembank.admin` for any |
| `/ibank removeall <player\|server>` | remove all of someone's banks | `itembank.admin` |
| `/ibank tax` | see the tax | anyone |
| `/ibank tax on\|off`, `sell <%>`, `buy <%>`, `receiver <player\|none>` | change the tax | `itembank.tax` |
| `/ibank stats` | money on the server, price index, tax collected | `itembank.admin` |
| `/ibank inflation reset\|set <index>` | reset inflation or set it by hand | `itembank.admin` |
| `/ibank setowner <player\|server>` | change who owns a bank | `itembank.admin` |
| `/ibank stock <material> <amount>` | set stock | `itembank.admin` |
| `/ibank freeze <material>` | lock a price | `itembank.admin` |
| `/ibank disable <material>` | turn trading off/on | `itembank.admin` |
| `/ibank reopen <material>` | reopen selling after a sell-off | `itembank.admin` |
| `/ibank update` | run the daily update now | `itembank.admin` |
| `/ibank reload` | reload the config | `itembank.admin` |

`/itembank` works too. `itembank.bypass.limit` skips the daily limits and `itembank.create.unlimited` skips the player bank limit.

## How prices work

Every material has a base price and a normal stock. More stock than normal, cheaper. Less, more expensive. Big sales are priced one item at a time so dumping a double chest drops the price as you go, and selling 64 at once pays the same as selling 1 at a time 64 times. Buying always costs a bit more than selling so you can't flip items for profit.

Banks also slowly get rid of extra stock over the day (50% of the surplus by default), so a material that got flooded comes back instead of being worthless forever.

## Inflation

Every day at midnight it adds up the money of everyone who played in the last 30 days and works out the average per player. If that goes up, prices go up. If the money per player doubles, prices end up doubled too, but only a few % a day so it's not a shock.

Money the banks pay out themselves doesn't count, so a farm can't push prices up by selling. New players joining with starting money don't count either. What does count is money from anywhere else: /eco give, jobs, vote rewards, or the dictator printing money. `/ibank stats` shows the numbers.

## Farm protection

Each player can only sell so much of one material per hour (25% of its normal stock by default, so 250 coal or 25 diamonds). Every material has its own limit so hitting the coal cap doesn't stop you selling iron. If everyone together floods a bank, it stops buying that material for an hour and tells the server.

## Tax

Off by default. Turn it on and pick a buy and sell rate. The tax can go to a player (king, mayor, dictator, whoever) or just disappear from the economy. There's a max rate so nobody can set it to 100%.

## For other plugins

I'm making an election plugin that hooks into this, so there's an API:

```java
ItemBankAPI bank = ItemBankAPI.get();   // null if Item Bank isn't installed
bank.setTaxReceiver(playerUuid);
bank.setSellTax(8);
double supply = bank.getMoneySupply();
```

There's also `TaxCollectedEvent` and `DailyUpdateEvent`. Anything changed through the API gets saved to the config.

## Config

Everything is in `config.yml` with notes on what each setting does. Every message can be changed too.

## Building it yourself

`mvn package` with Java 21. The jar ends up in `target/`.

---

Designed by AlakaiiGaming for DictatorCraft, written with help from AI. Found a bug? Open an issue.
