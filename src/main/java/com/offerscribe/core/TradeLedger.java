package com.offerscribe.core;

import com.offerscribe.model.TradeEvent;
import java.util.*;

public final class TradeLedger {
    private final LinkedHashMap<UUID, TradeEvent> events = new LinkedHashMap<>();
    public synchronized boolean append(TradeEvent event) { return events.putIfAbsent(event.getEventId(), event) == null; }
    public synchronized List<TradeEvent> events() { return Collections.unmodifiableList(new ArrayList<>(events.values())); }
    public synchronized int size() { return events.size(); }
}

