package com.offerscribe.core;

import com.offerscribe.model.OfferSnapshot;
import com.offerscribe.model.TradeEvent;
import java.util.Optional;

public final class FillDeltaDetector {
    public Optional<TradeEvent> detect(OfferSnapshot previous, OfferSnapshot current,
        TradeEvent.Source source, String profile, String installation, String session, String pluginVersion) {
        if (previous == null || current == null || !previous.identity().equals(current.identity())) return Optional.empty();
        int quantityDelta = current.getFilledQuantity() - previous.getFilledQuantity();
        long coinsDelta = current.getSpentOrReceived() - previous.getSpentOrReceived();
        if (quantityDelta <= 0 || coinsDelta < 0) return Optional.empty();
        return Optional.of(new TradeEvent(profile, installation, session, current,
            quantityDelta, coinsDelta, source, GeTaxCalculator.CURRENT_VERSION, pluginVersion));
    }
}

