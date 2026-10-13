package com.offerscribe;

import com.offerscribe.model.OfferSnapshot;
import java.time.Instant;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

final class GeOfferObserver {
    OfferSnapshot snapshot(int slot, GrandExchangeOffer offer, Instant now) {
        GrandExchangeOfferState raw = offer.getState();
        OfferSnapshot.Side side = side(raw);
        OfferSnapshot.State state = map(raw);
        return new OfferSnapshot(now, slot, offer.getItemId(), side, offer.getPrice(),
            offer.getTotalQuantity(), offer.getQuantitySold(), offer.getSpent(), state);
    }
    OfferSnapshot.Side side(GrandExchangeOfferState state) {
        String value = state.name();
        return value.equals("BUYING") || value.equals("BOUGHT") || value.equals("CANCELLED_BUY")
            ? OfferSnapshot.Side.BUY
            : OfferSnapshot.Side.SELL;
    }
    private OfferSnapshot.State map(GrandExchangeOfferState state) {
        String value = state.name();
        if (value.equals("EMPTY")) return OfferSnapshot.State.EMPTY;
        if (value.contains("CANCEL")) return OfferSnapshot.State.CANCELLED;
        if (value.contains("BOUGHT") || value.contains("SOLD")) return OfferSnapshot.State.COMPLETED;
        if (value.contains("PENDING")) return OfferSnapshot.State.PENDING;
        return OfferSnapshot.State.ACTIVE;
    }
}
