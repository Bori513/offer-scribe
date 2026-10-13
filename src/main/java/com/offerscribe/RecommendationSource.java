package com.offerscribe;

public enum RecommendationSource {
    WIKI("Local OSRS Wiki"),
    SERVICE_WITH_WIKI_FALLBACK("Service, then Wiki fallback"),
    SERVICE_ONLY("Service only");

    private final String label;
    RecommendationSource(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
