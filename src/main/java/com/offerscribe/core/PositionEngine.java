package com.offerscribe.core;

import com.offerscribe.model.OfferSnapshot.Side;
import com.offerscribe.model.TradeEvent;
import java.time.*;
import java.util.*;

public final class PositionEngine {
    private static final class Lot { long quantity, unitCost; Instant at; Lot(long q,long c,Instant a){quantity=q;unitCost=c;at=a;} }
    public static final class Match {
        public final int itemId; public final long quantity, cost, grossProceeds, tax, netProfit; public final Duration held;
        Match(int i,long q,long c,long g,long t,long p,Duration h){itemId=i;quantity=q;cost=c;grossProceeds=g;tax=t;netProfit=p;held=h;}
        public double roi() { return cost == 0 ? 0 : (double) netProfit / cost; }
    }
    private final Map<Integer, Deque<Lot>> lots = new HashMap<>();
    private final List<Match> matches = new ArrayList<>();
    public void apply(TradeEvent event) {
        if (event.getSide() == Side.BUY) {
            long unit = event.getQuantityDelta() == 0 ? 0 : event.getCoinsDelta() / event.getQuantityDelta();
            lots.computeIfAbsent(event.getItemId(), k -> new ArrayDeque<>()).add(new Lot(event.getQuantityDelta(), unit, event.getObservedAt()));
            return;
        }
        Deque<Lot> queue = lots.computeIfAbsent(event.getItemId(), k -> new ArrayDeque<>());
        long remaining = event.getQuantityDelta();
        while (remaining > 0 && !queue.isEmpty()) {
            Lot lot = queue.peek(); long used = Math.min(remaining, lot.quantity);
            long gross = Math.multiplyExact(event.getPricePerItem(), used);
            long cost = Math.multiplyExact(lot.unitCost, used);
            long tax = GeTaxCalculator.tax(event.getPricePerItem(), used);
            matches.add(new Match(event.getItemId(), used, cost, gross, tax, gross - tax - cost,
                Duration.between(lot.at, event.getObservedAt())));
            lot.quantity -= used; remaining -= used; if (lot.quantity == 0) queue.remove();
        }
    }
    public long openQuantity(int itemId) { return lots.getOrDefault(itemId, new ArrayDeque<>()).stream().mapToLong(l -> l.quantity).sum(); }
    public List<Match> matches() { return Collections.unmodifiableList(matches); }
}
