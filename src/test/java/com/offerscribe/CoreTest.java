package com.offerscribe;

import com.offerscribe.core.*;
import com.offerscribe.model.*;
import java.time.Instant;
import com.offerscribe.model.MarketPrice;
import com.offerscribe.model.Recommendation;
import com.offerscribe.model.MarketItemSnapshot;
import com.offerscribe.model.Opportunity;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    private OfferSnapshot snap(int filled,long coins,OfferSnapshot.Side side,Instant at) {
        return new OfferSnapshot(at, 0, 4151, side, 1_000_000, 10, filled, coins, OfferSnapshot.State.ACTIVE);
    }
    @Test void detectsOnlyPositiveStableOfferDeltasAndDeduplicates() {
        FillDeltaDetector d = new FillDeltaDetector(); Instant t=Instant.parse("2026-01-01T00:00:00Z");
        TradeEvent e=d.detect(snap(2,2_000_000,OfferSnapshot.Side.BUY,t),snap(5,5_000_000,OfferSnapshot.Side.BUY,t.plusSeconds(1)),TradeEvent.Source.LIVE_EVENT,"p","i","s","v").orElseThrow();
        assertEquals(3,e.getQuantityDelta()); assertEquals(3_000_000,e.getCoinsDelta());
        assertTrue(d.detect(snap(5,5_000_000,OfferSnapshot.Side.BUY,t),snap(5,5_000_000,OfferSnapshot.Side.BUY,t),TradeEvent.Source.LIVE_EVENT,"p","i","s","v").isEmpty());
        TradeLedger ledger=new TradeLedger(); assertTrue(ledger.append(e)); assertFalse(ledger.append(e));
    }
    @Test void taxBoundariesFloorAndCap() {
        assertEquals(0,GeTaxCalculator.taxPerItem(49)); assertEquals(1,GeTaxCalculator.taxPerItem(50));
        assertEquals(5_000_000,GeTaxCalculator.taxPerItem(300_000_000));
        assertEquals(10_000_000,GeTaxCalculator.tax(300_000_000,2));
    }
    @Test void fifoRecordsProfitsAndLosses() {
        Instant t=Instant.parse("2026-01-01T00:00:00Z"); PositionEngine p=new PositionEngine();
        TradeEvent buy=new TradeEvent("p","i","s",snap(4,4_000_000,OfferSnapshot.Side.BUY,t),4,4_000_000,TradeEvent.Source.LIVE_EVENT,GeTaxCalculator.CURRENT_VERSION,"v");
        OfferSnapshot sellSnap=new OfferSnapshot(t.plusSeconds(3600),1,4151,OfferSnapshot.Side.SELL,1_100_000,2,2,2_200_000,OfferSnapshot.State.COMPLETED);
        TradeEvent sell=new TradeEvent("p","i","s",sellSnap,2,2_200_000,TradeEvent.Source.LIVE_EVENT,GeTaxCalculator.CURRENT_VERSION,"v");
        p.apply(buy);p.apply(sell);assertEquals(2,p.openQuantity(4151));assertEquals(156_000,p.matches().get(0).netProfit);assertEquals(0.078,p.matches().get(0).roi(),0.0001);
    }

    @Test void completedBuyRemainsOnBuySide() {
        GeOfferObserver observer = new GeOfferObserver();
        assertEquals(OfferSnapshot.Side.BUY, observer.side(GrandExchangeOfferState.BUYING));
        assertEquals(OfferSnapshot.Side.BUY, observer.side(GrandExchangeOfferState.BOUGHT));
        assertEquals(OfferSnapshot.Side.BUY, observer.side(GrandExchangeOfferState.CANCELLED_BUY));
        assertEquals(OfferSnapshot.Side.SELL, observer.side(GrandExchangeOfferState.SELLING));
        assertEquals(OfferSnapshot.Side.SELL, observer.side(GrandExchangeOfferState.SOLD));
    }

    @Test void recommendationUsesObservedSpreadAndTax() {
        Instant observed = Instant.parse("2026-01-01T00:00:00Z");
        MarketPrice price = new MarketPrice(4151, 1_100_000, 1_000_000, observed, observed);
        Recommendation recommendation = new RecommendationEngine().recommend(price, OfferSnapshot.Side.BUY, 8);
        assertEquals(1_000_000, recommendation.suggestedPrice);
        assertEquals(8, recommendation.suggestedQuantity);
        assertEquals(78_000, recommendation.expectedUnitProfit);
        assertEquals(0.078, recommendation.roi, 0.0001);
    }

    @Test void ranksOnlyFreshLiquidPlausibleOpportunities() {
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        MarketItemSnapshot stable = new MarketItemSnapshot(1, "Stable item", false, 100,
            1100, 1000, now.minusSeconds(100), now.minusSeconds(100), 1090, 1005, 1000, 800);
        MarketItemSnapshot stale = new MarketItemSnapshot(2, "Stale item", false, 100,
            1100, 1000, now.minusSeconds(4000), now.minusSeconds(4000), 1090, 1005, 1000, 800);
        MarketItemSnapshot fakeSpread = new MarketItemSnapshot(3, "Fake spread", false, 100,
            2000, 1000, now.minusSeconds(100), now.minusSeconds(100), 1900, 1100, 1000, 800);
        List<Opportunity> ranked = new MarketOpportunityEngine().rank(
            Arrays.asList(stable, stale, fakeSpread), 10_000, true, 10, now);
        assertEquals(1, ranked.size());
        assertEquals("Stable item", ranked.get(0).name);
        assertEquals(10, ranked.get(0).quantity);
        assertEquals(780, ranked.get(0).expectedProfit);
    }
}
