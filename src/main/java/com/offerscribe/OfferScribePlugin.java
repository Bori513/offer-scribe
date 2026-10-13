package com.offerscribe;

import com.offerscribe.core.*;
import com.offerscribe.model.*;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.VarPlayer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(name="Offer Scribe", description="Local-first GE fill ledger and read-only analytics", tags={"ge","trading","flipping"})
public final class OfferScribePlugin extends Plugin {
    private static final String VERSION = "0.1.0";
    @Inject private Client client;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlayManager;
    @Inject private GeRecommendationOverlay recommendationOverlay;
    @Inject private WikiMarketDataClient marketData;
    @Inject private GeInputAssistant inputAssistant;
    @Inject private GeItemSearchAssistant itemSearchAssistant;
    @Inject private OfferScribeConfig config;
    private final GeOfferObserver observer = new GeOfferObserver();
    private final FillDeltaDetector detector = new FillDeltaDetector();
    private final RecommendationEngine recommendations = new RecommendationEngine();
    private final MarketOpportunityEngine opportunityEngine = new MarketOpportunityEngine();
    private final TradeLedger ledger = new TradeLedger();
    private final PositionEngine positions = new PositionEngine();
    private final Map<Integer, OfferSnapshot> prior = new HashMap<>();
    private final String installation = UUID.randomUUID().toString(), session = UUID.randomUUID().toString();
    private NavigationButton navigationButton; private GeTradingPanel panel;
    private int requestedMarketItem = -1;
    private Instant lastOpportunityRequest = Instant.EPOCH;
    private boolean opportunityRequestPending;
    private List<Opportunity> rankedOpportunities = Collections.emptyList();
    private String riskLevel = "MEDIUM";
    private int suggestionIndex;
    private Opportunity activeOpportunity;
    private GeRecommendationOverlay.GuidedPhase guidedPhase = GeRecommendationOverlay.GuidedPhase.BUY;
    private int activeSlot = -1;
    private Boolean lastMembersWorld;
    private List<MarketItemSnapshot> marketUniverse = Collections.emptyList();
    private long availableCoins;

    @Provides OfferScribeConfig config(ConfigManager manager) { return manager.getConfig(OfferScribeConfig.class); }
    @Override protected void startUp() {
        panel = new GeTradingPanel();
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/offer-scribe-icon.png");
        navigationButton = NavigationButton.builder().tooltip("Offer Scribe").icon(icon).panel(panel).priority(8).build();
        toolbar.addNavigation(navigationButton);
        overlayManager.add(recommendationOverlay);
        panel.setSuggestionControls(this::setRiskLevel, this::skipSuggestion);
        inputAssistant.start();
    }
    @Override protected void shutDown() {
        if (navigationButton != null) toolbar.removeNavigation(navigationButton);
        overlayManager.remove(recommendationOverlay);
        recommendationOverlay.clear();
        inputAssistant.stop();
        itemSearchAssistant.clear();
        requestedMarketItem = -1;
        prior.clear();
    }
    @Subscribe public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        OfferSnapshot current = observer.snapshot(event.getSlot(), event.getOffer(), Instant.now());
        panel.observeOffer(current);
        OfferSnapshot previous = prior.put(event.getSlot(), current);
        detector.detect(previous, current, TradeEvent.Source.LIVE_EVENT, profileId(), installation, session, VERSION)
            .ifPresent(fill -> { if (ledger.append(fill)) { positions.apply(fill); panel.addFill(fill, ledger.size()); } });
        advanceGuidance(current);
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged event) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null || event.getItemContainer() != inventory) return;
        long coins = inventory.count(ItemID.COINS_995);
        if (coins == availableCoins) return;
        availableCoins = coins;
        rankCurrentUniverse();
    }

    @Subscribe public void onGameTick(GameTick tick) {
        if (!config.marketDataEnabled()) {
            recommendationOverlay.clear();
            inputAssistant.clear();
            itemSearchAssistant.clear();
            activeOpportunity = null;
            rankedOpportunities = Collections.emptyList();
            panel.showActiveOpportunity(null, riskLevel);
            requestedMarketItem = -1;
            lastOpportunityRequest = Instant.EPOCH;
            panel.showMarketDisabled();
            return;
        }
        boolean membersWorld = client.getWorldType().contains(WorldType.MEMBERS);
        if (lastMembersWorld == null || lastMembersWorld != membersWorld) {
            lastMembersWorld = membersWorld;
            rankedOpportunities = Collections.emptyList();
            activeOpportunity = null;
            suggestionIndex = 0;
            lastOpportunityRequest = Instant.EPOCH;
        }
        if (!opportunityRequestPending && Duration.between(lastOpportunityRequest, Instant.now()).toMinutes() >= 5) {
            refreshOpportunities();
        }
        Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
        if (setup == null || setup.isHidden()) {
            requestedMarketItem = -1;
            return;
        }
        int itemId = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);
        if (itemId <= 0 || itemId == requestedMarketItem) return;
        requestedMarketItem = itemId;
        if (activeOpportunity != null && itemId == activeOpportunity.itemId) {
            applyActiveRecommendation(activeOpportunity);
            return;
        }
        int type = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE);
        int quantity = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
        OfferSnapshot.Side side = type == 0 ? OfferSnapshot.Side.BUY : OfferSnapshot.Side.SELL;
        panel.showLoading(itemId);
        marketData.fetch(itemId, price -> {
            if (requestedMarketItem != itemId || !config.marketDataEnabled()) return;
            Recommendation recommendation = recommendations.recommend(price, side, quantity);
            panel.showRecommendation(recommendation);
            recommendationOverlay.show(recommendation);
            inputAssistant.setRecommendation(recommendation);
        }, message -> {
            if (requestedMarketItem == itemId) panel.showMarketError(message);
        });
    }
    @Subscribe public void onVarClientIntChanged(VarClientIntChanged event) {
        inputAssistant.onVarClientIntChanged(event);
        itemSearchAssistant.onVarClientIntChanged(event);
    }

    private void refreshOpportunities() {
        opportunityRequestPending = true;
        lastOpportunityRequest = Instant.now();
        panel.showOpportunityLoading();
        marketData.fetchUniverse(items -> {
            opportunityRequestPending = false;
            if (!config.marketDataEnabled()) return;
            marketUniverse = items;
            availableCoins = inventoryCoins();
            rankCurrentUniverse();
        }, message -> {
            opportunityRequestPending = false;
            panel.showOpportunityError(message);
        });
    }

    private long inventoryCoins() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        return inventory == null ? 0 : inventory.count(ItemID.COINS_995);
    }

    private void rankCurrentUniverse() {
        if (marketUniverse.isEmpty() || panel == null) return;
        boolean allowMembers = config.includeMembersItems()
            && client.getWorldType().contains(WorldType.MEMBERS);
        rankedOpportunities = opportunityEngine.rank(marketUniverse, availableCoins,
            allowMembers, 50, Instant.now());
        panel.showOpportunities(rankedOpportunities.subList(0, Math.min(10, rankedOpportunities.size())));
        panel.showAvailableCash(availableCoins);
        suggestionIndex = 0;
        selectActiveSuggestion();
    }

    private void setRiskLevel(String value) {
        riskLevel = value;
        suggestionIndex = 0;
        selectActiveSuggestion();
    }

    private void skipSuggestion() {
        suggestionIndex++;
        selectActiveSuggestion();
    }

    private void selectActiveSuggestion() {
        List<Opportunity> eligible = new ArrayList<>();
        for (Opportunity value : rankedOpportunities) {
            if (isEligibleRisk(value.risk)) eligible.add(value);
        }
        activeOpportunity = eligible.isEmpty() ? null : eligible.get(Math.floorMod(suggestionIndex, eligible.size()));
        guidedPhase = GeRecommendationOverlay.GuidedPhase.BUY;
        activeSlot = -1;
        panel.showActiveOpportunity(activeOpportunity, riskLevel);
        recommendationOverlay.showOpportunity(activeOpportunity);
        recommendationOverlay.showPhase(guidedPhase, activeSlot);
        itemSearchAssistant.setOpportunity(activeOpportunity);
        if (activeOpportunity == null) inputAssistant.clear();
        else applyActiveRecommendation(activeOpportunity);
    }

    private boolean isEligibleRisk(String risk) {
        if ("HIGH".equals(riskLevel)) return true;
        if ("MEDIUM".equals(riskLevel)) return !"HIGH".equals(risk);
        return "LOW".equals(risk);
    }

    private void applyActiveRecommendation(Opportunity value) {
        boolean selling = guidedPhase == GeRecommendationOverlay.GuidedPhase.SELL
            || guidedPhase == GeRecommendationOverlay.GuidedPhase.WAIT_SELL
            || guidedPhase == GeRecommendationOverlay.GuidedPhase.COLLECT_SELL;
        Recommendation recommendation = new Recommendation(value.itemId,
            selling ? OfferSnapshot.Side.SELL : OfferSnapshot.Side.BUY,
            selling ? value.sellPrice : value.buyPrice, value.quantity, value.profitPerItem, value.roi, value.dataTimestamp,
            "OSRS Wiki prices and hourly volume; tax, budget, buy limit, freshness, and stability adjusted");
        recommendationOverlay.show(recommendation);
        inputAssistant.setRecommendation(recommendation);
        panel.showRecommendation(recommendation);
    }

    private void advanceGuidance(OfferSnapshot offer) {
        if (activeOpportunity == null) return;
        if (offer.getItemId() == activeOpportunity.itemId) {
            activeSlot = offer.getSlot();
            if (offer.getSide() == OfferSnapshot.Side.BUY) {
                guidedPhase = offer.getState() == OfferSnapshot.State.COMPLETED
                    ? GeRecommendationOverlay.GuidedPhase.COLLECT_BUY
                    : GeRecommendationOverlay.GuidedPhase.WAIT_BUY;
            } else {
                guidedPhase = offer.getState() == OfferSnapshot.State.COMPLETED
                    ? GeRecommendationOverlay.GuidedPhase.COLLECT_SELL
                    : GeRecommendationOverlay.GuidedPhase.WAIT_SELL;
            }
        } else if (offer.getState() == OfferSnapshot.State.EMPTY && offer.getSlot() == activeSlot) {
            if (guidedPhase == GeRecommendationOverlay.GuidedPhase.COLLECT_BUY) {
                guidedPhase = GeRecommendationOverlay.GuidedPhase.SELL;
                applyActiveRecommendation(activeOpportunity);
            } else if (guidedPhase == GeRecommendationOverlay.GuidedPhase.COLLECT_SELL) {
                suggestionIndex++;
                selectActiveSuggestion();
                return;
            }
        } else return;
        recommendationOverlay.showPhase(guidedPhase, activeSlot);
        panel.showGuidedPhase(activeOpportunity, guidedPhase);
    }
    private String profileId() { return "local-" + Integer.toUnsignedString(Objects.hash(client.getAccountHash()), 36); }
}
