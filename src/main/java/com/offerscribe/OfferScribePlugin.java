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
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(name="Offer Scribe", internalName="offer-scribe", description="Local-first GE fill ledger and read-only analytics", tags={"ge","trading","flipping"})
public final class OfferScribePlugin extends Plugin {
    private static final String VERSION = "0.2.0";
    @Inject private Client client;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlayManager;
    @Inject private GeRecommendationOverlay recommendationOverlay;
    @Inject private WikiMarketDataClient marketData;
    @Inject private GeInputAssistant inputAssistant;
    @Inject private GeItemSearchAssistant itemSearchAssistant;
    @Inject private ItemCatalog itemCatalog;
    @Inject private FillWebhookClient webhookClient;
    @Inject private ExternalRecommendationClient externalRecommendations;
    @Inject private LocalLedgerPersistence ledgerPersistence;
    @Inject private ClientThread clientThread;
    @Inject private OfferScribeConfig config;
    @Inject private ConfigManager configManager;
    private final GeOfferObserver observer = new GeOfferObserver();
    private final FillDeltaDetector detector = new FillDeltaDetector();
    private final RecommendationEngine recommendations = new RecommendationEngine();
    private final MarketOpportunityEngine opportunityEngine = new MarketOpportunityEngine();
    private final TradeLedger ledger = new TradeLedger();
    private final PositionEngine positions = new PositionEngine();
    private final Map<Integer, OfferSnapshot> prior = new HashMap<>();
    private String installation;
    private final String session = UUID.randomUUID().toString();
    private NavigationButton navigationButton; private GeTradingPanel panel;
    private int requestedMarketItem = -1;
    private Instant lastOpportunityRequest = Instant.EPOCH;
    private boolean opportunityRequestPending;
    private int opportunityRequestGeneration;
    private List<Opportunity> rankedOpportunities = Collections.emptyList();
    private String riskLevel = "MEDIUM";
    private int suggestionIndex;
    private Opportunity activeOpportunity;
    private GeRecommendationOverlay.GuidedPhase guidedPhase = GeRecommendationOverlay.GuidedPhase.BUY;
    private int activeSlot = -1;
    private int guidedQuantity;
    private int quantityAfterCollection;
    private Boolean lastMembersWorld;
    private List<MarketItemSnapshot> marketUniverse = Collections.emptyList();
    private List<ExternalRecommendationClient.Candidate> serviceCandidates = Collections.emptyList();
    private boolean usingRecommendationService;
    private long availableCoins;
    private boolean inventoryInitialized;
    private boolean startupRecoveryPending;
    private boolean guidanceRefreshPending;

    @Provides OfferScribeConfig config(ConfigManager manager) { return manager.getConfig(OfferScribeConfig.class); }
    @Override protected void startUp() throws Exception {
        panel = new GeTradingPanel();
        installation = UUID.randomUUID().toString();
        net.runelite.client.util.Filepath dataDirectory = getPluginDirectory();
        ledgerPersistence.setDataDirectory(dataDirectory);
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/offer-scribe-icon.png");
        navigationButton = NavigationButton.builder().tooltip("Offer Scribe").icon(icon).panel(panel).priority(8).build();
        toolbar.addNavigation(navigationButton);
        overlayManager.add(recommendationOverlay);
        panel.setSuggestionControls(this::setRiskLevel, this::skipSuggestion, this::blockSuggestion);
        inputAssistant.start();
        webhookClient.start(dataDirectory, panel::showSyncStatus);
        ledgerPersistence.load(events -> clientThread.invokeLater(() -> {
            for (TradeEvent event : events) if (ledger.append(event)) {
                positions.apply(event); panel.addFill(event,itemCatalog.name(event.getItemId()),ledger.size());
            }
            startupRecoveryPending = true;
            panel.setEventCount(ledger.size());
            rankCurrentUniverse();
        }));
    }
    @Override protected void shutDown() {
        opportunityRequestGeneration++;
        if (navigationButton != null) toolbar.removeNavigation(navigationButton);
        overlayManager.remove(recommendationOverlay);
        recommendationOverlay.clear();
        inputAssistant.stop();
        itemSearchAssistant.clear();
        webhookClient.stop();
        externalRecommendations.stop();
        requestedMarketItem = -1;
        prior.clear();
    }
    @Subscribe public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        OfferSnapshot current = observer.snapshot(event.getSlot(), event.getOffer(), Instant.now());
        String itemName = current.getItemId() <= 0 ? "Empty" : itemCatalog.name(current.getItemId());
        panel.observeOffer(current, itemName);
        OfferSnapshot previous = prior.put(event.getSlot(), current);
        detector.detect(previous, current, TradeEvent.Source.LIVE_EVENT, profileId(), installation, session, VERSION)
            .ifPresent(fill -> { if (ledger.append(fill)) { positions.apply(fill); panel.addFill(fill,
                itemCatalog.name(fill.getItemId()), ledger.size()); webhookClient.enqueue(fill, itemCatalog.name(fill.getItemId()));
                ledgerPersistence.save(ledger.events()); } });
        advanceGuidance(current);
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged event) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null || event.getItemContainer() != inventory) return;
        long coins = inventory.count(ItemID.COINS_995);
        boolean cashChanged = coins != availableCoins;
        availableCoins = coins;
        inventoryInitialized = true;
        // Collection usually changes items without changing coins. Re-evaluate open ledger
        // positions on every inventory update so a collected buy immediately becomes SELL.
        if (cashChanged || startupRecoveryPending || hasHeldOpenPosition()
            || activeOpportunity == null || guidedPhase == GeRecommendationOverlay.GuidedPhase.BUY && activeSlot < 0) {
            rankCurrentUniverse();
        }
    }

    @Subscribe public void onGameTick(GameTick tick) {
        webhookClient.tick();
        if (guidanceRefreshPending) {
            guidanceRefreshPending = false;
            rankCurrentUniverse();
        }
        if (!inventoryInitialized) {
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            if (inventory != null) {
                availableCoins = inventory.count(ItemID.COINS_995);
                inventoryInitialized = true;
                rankCurrentUniverse();
            }
        }
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
        panel.showMarketEnabled();
        if (reconcileCompletedOffers()) return;
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

    @Subscribe public void onConfigChanged(ConfigChanged event) {
        if (!"offerscribe".equals(event.getGroup())) return;
        if ("recommendationSource".equals(event.getKey()) || "recommendationServiceUrl".equals(event.getKey())
            || "marketDataEnabled".equals(event.getKey())) {
            lastOpportunityRequest = Instant.EPOCH;
            refreshOpportunities();
        } else if ("blockedItemIds".equals(event.getKey())) {
            suggestionIndex = 0;
            rankCurrentUniverse();
        }
    }

    private void refreshOpportunities() {
        int generation = ++opportunityRequestGeneration;
        if (!config.marketDataEnabled()) {
            opportunityRequestPending = false;
            return;
        }
        opportunityRequestPending = true;
        lastOpportunityRequest = Instant.now();
        panel.showOpportunityLoading();
        if (config.recommendationSource() != RecommendationSource.WIKI) {
            externalRecommendations.fetch(values -> clientThread.invokeLater(() -> {
                if (generation != opportunityRequestGeneration) return;
                opportunityRequestPending = false;
                if (!config.marketDataEnabled()) return;
                serviceCandidates = values;
                usingRecommendationService = true;
                availableCoins = inventoryCoins();
                inventoryInitialized = true;
                panel.showRecommendationSource("Service");
                rankCurrentUniverse();
            }), message -> clientThread.invokeLater(() -> {
                if (generation != opportunityRequestGeneration) return;
                if (config.recommendationSource() == RecommendationSource.SERVICE_WITH_WIKI_FALLBACK) {
                    panel.showRecommendationSource("Wiki fallback");
                    fetchWikiOpportunities(generation);
                } else {
                    opportunityRequestPending = false;
                    usingRecommendationService = false;
                    serviceCandidates = Collections.emptyList();
                    activeOpportunity = null;
                    recommendationOverlay.clear();
                    inputAssistant.clear();
                    itemSearchAssistant.clear();
                    panel.showOpportunityError(message);
                    panel.showActiveOpportunity(null, riskLevel);
                }
            }));
            return;
        }
        fetchWikiOpportunities(generation);
    }

    private void fetchWikiOpportunities(int generation) {
        marketData.fetchUniverse(items -> {
            clientThread.invokeLater(() -> {
                if (generation != opportunityRequestGeneration) return;
                opportunityRequestPending = false;
                if (!config.marketDataEnabled()) return;
                usingRecommendationService = false;
                serviceCandidates = Collections.emptyList();
                marketUniverse = items;
                itemCatalog.update(items);
                availableCoins = inventoryCoins();
                inventoryInitialized = true;
                panel.showRecommendationSource("OSRS Wiki");
                rankCurrentUniverse();
            });
        }, message -> {
            clientThread.invokeLater(() -> {
                if (generation != opportunityRequestGeneration) return;
                opportunityRequestPending = false;
                panel.showOpportunityError(message);
            });
        });
    }

    private long inventoryCoins() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        return inventory == null ? 0 : inventory.count(ItemID.COINS_995);
    }

    private void rankCurrentUniverse() {
        if ((!usingRecommendationService && marketUniverse.isEmpty())
            || (usingRecommendationService && serviceCandidates.isEmpty()) || panel == null) return;
        if (reconcileCompletedOffers()) return;
        boolean allowMembers = config.includeMembersItems()
            && client.getWorldType().contains(WorldType.MEMBERS);
        rankedOpportunities = usingRecommendationService
            ? rankServiceCandidates(allowMembers)
            : opportunityEngine.rank(marketUniverse, availableCoins, allowMembers, 50, Instant.now());
        Set<Integer> blocked = blockedItemIds();
        rankedOpportunities.removeIf(value -> blocked.contains(value.itemId));
        panel.showOpportunities(rankedOpportunities.subList(0, Math.min(10, rankedOpportunities.size())));
        panel.showAvailableCash(availableCoins);
        // Inventory and the persisted ledger become available independently during login.
        // Always prefer a held, unsold position once both are visible; a temporary BUY
        // suggestion must never prevent recovery on the next inventory update.
        Opportunity recovery = recoverHeldPosition();
        if (recovery != null) {
            startupRecoveryPending = false;
            if (activeOpportunity != null && activeOpportunity.itemId == recovery.itemId
                && guidedPhase != GeRecommendationOverlay.GuidedPhase.BUY) return;
            long open = positions.openQuantity(recovery.itemId);
            activateOpenPosition(recovery, (int)Math.min(Math.min(Integer.MAX_VALUE, open), inventoryQuantity(recovery.itemId)));
            return;
        }
        if (startupRecoveryPending && !inventoryInitialized) return;
        startupRecoveryPending = false;
        if (activeOpportunity != null && (activeSlot >= 0 || guidedPhase != GeRecommendationOverlay.GuidedPhase.BUY)) return;
        suggestionIndex = 0;
        selectActiveSuggestion();
    }

    private Opportunity recoverHeldPosition() {
        Opportunity selected = null;
        Instant newest = Instant.EPOCH;
        Set<Integer> openIds = positions.openItemIds();
        for (int itemId : openIds) {
            int quantity = (int)Math.min(Integer.MAX_VALUE,
                Math.min(positions.openQuantity(itemId), inventoryQuantity(itemId)));
            if (quantity <= 0) continue;
            Opportunity opportunity = opportunityForItem(itemId, quantity);
            if (opportunity == null) continue;
            Instant acquired = positions.latestOpenAt(itemId);
            if (selected == null || acquired.isAfter(newest)) { selected = opportunity; newest = acquired; }
        }
        return selected;
    }

    private boolean hasHeldOpenPosition() {
        for (int itemId : positions.openItemIds()) if (inventoryQuantity(itemId) > 0) return true;
        return false;
    }

    private void activateOpenPosition(Opportunity value, int quantity) {
        activeOpportunity=value; guidedPhase=GeRecommendationOverlay.GuidedPhase.SELL;
        guidedQuantity=quantity; quantityAfterCollection=0; activeSlot=-1;
        panel.showActiveOpportunity(value,riskLevel); panel.showGuidedPhase(value,guidedPhase,guidedQuantity);
        recommendationOverlay.showOpportunity(value); recommendationOverlay.showPhase(guidedPhase,activeSlot,guidedQuantity);
        itemSearchAssistant.setOpportunity(value);
        applyActiveRecommendation(value);
    }

    private int inventoryQuantity(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        return inventory == null ? 0 : inventory.count(itemId);
    }

    private void setRiskLevel(String value) {
        riskLevel = value;
        suggestionIndex = 0;
        rankCurrentUniverse();
    }

    private void skipSuggestion() {
        if (activeOpportunity != null && guidedPhase != GeRecommendationOverlay.GuidedPhase.BUY) return;
        suggestionIndex++;
        selectActiveSuggestion();
    }

    private void blockSuggestion() {
        if (activeOpportunity == null) return;
        Set<Integer> blocked = blockedItemIds();
        blocked.add(activeOpportunity.itemId);
        String value = blocked.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        configManager.setConfiguration("offerscribe", "blockedItemIds", value);
        suggestionIndex = 0;
        rankCurrentUniverse();
    }

    private Set<Integer> blockedItemIds() {
        Set<Integer> result = new HashSet<>();
        for (String token : config.blockedItemIds().split(",")) {
            try { int value = Integer.parseInt(token.trim()); if (value > 0) result.add(value); }
            catch (NumberFormatException ignored) { }
        }
        result.add(13190);
        result.add(13192);
        return result;
    }

    private List<Opportunity> rankServiceCandidates(boolean allowMembers) {
        List<Opportunity> result = new ArrayList<>();
        Instant now = Instant.now();
        for (ExternalRecommendationClient.Candidate value : serviceCandidates) {
            if (!value.expiresAt.isAfter(now) || value.buyPrice <= 0 || value.sellPrice <= value.buyPrice) continue;
            net.runelite.api.ItemComposition item = client.getItemDefinition(value.itemId);
            if (item == null || !item.isTradeable() || !allowMembers && item.isMembers()) continue;
            long tax = GeTaxCalculator.taxPerItem(value.sellPrice);
            long profit = value.sellPrice - value.buyPrice - tax;
            if (profit <= 0) continue;
            long affordable = availableCoins / value.buyPrice;
            int quantity = (int)Math.min(Integer.MAX_VALUE, Math.min(value.maxQuantity, affordable));
            if (quantity <= 0) continue;
            double roi = (double)profit / value.buyPrice;
            String name = itemCatalog.name(value.itemId);
            try {
                result.add(new Opportunity(value.itemId, name, value.buyPrice, value.sellPrice, quantity,
                    tax, profit, Math.multiplyExact(profit, quantity), roi, 0, value.generatedAt,
                    value.risk, value.score));
            } catch (ArithmeticException ignored) { }
        }
        result.sort(Comparator.comparingDouble((Opportunity value)->value.score).reversed().thenComparingInt(value->value.itemId));
        return result;
    }

    private void selectActiveSuggestion() {
        List<Opportunity> eligible = new ArrayList<>();
        Set<Integer> itemsAlreadyBuying = openBuyItemIds();
        for (Opportunity value : rankedOpportunities) {
            if (isEligibleRisk(value.risk) && !itemsAlreadyBuying.contains(value.itemId)) eligible.add(value);
        }
        activeOpportunity = eligible.isEmpty() || !hasEmptyOfferSlot()
            ? null : eligible.get(Math.floorMod(suggestionIndex, eligible.size()));
        guidedPhase = GeRecommendationOverlay.GuidedPhase.BUY;
        activeSlot = -1;
        guidedQuantity = activeOpportunity == null ? 0 : activeOpportunity.quantity;
        quantityAfterCollection = 0;
        panel.showActiveOpportunity(activeOpportunity, riskLevel);
        recommendationOverlay.showOpportunity(activeOpportunity);
        recommendationOverlay.showPhase(guidedPhase, activeSlot, guidedQuantity);
        itemSearchAssistant.setOpportunity(activeOpportunity);
        if (activeOpportunity == null) inputAssistant.clear();
        else applyActiveRecommendation(activeOpportunity);
    }

    private Set<Integer> openBuyItemIds() {
        Set<Integer> result = new HashSet<>();
        for (GrandExchangeOffer offer : client.getGrandExchangeOffers()) {
            if (offer == null) continue;
            GrandExchangeOfferState state = offer.getState();
            if (state == GrandExchangeOfferState.BUYING || state.name().equals("PENDING_BUY")) result.add(offer.getItemId());
        }
        return result;
    }

    private boolean hasEmptyOfferSlot() {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        int usable = client.getWorldType().contains(WorldType.MEMBERS) ? 8 : 3;
        for (int slot = 0; slot < Math.min(usable, offers.length); slot++) {
            if (offers[slot] != null && offers[slot].getState() == GrandExchangeOfferState.EMPTY) return true;
        }
        return false;
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
            selling ? value.sellPrice : value.buyPrice,
            guidedQuantity > 0 ? guidedQuantity : value.quantity, value.profitPerItem, value.roi, value.dataTimestamp,
            usingRecommendationService
                ? "Service data; locally validated"
                : "OSRS Wiki; tax and liquidity adjusted");
        recommendationOverlay.show(recommendation);
        inputAssistant.setRecommendation(recommendation);
        panel.showRecommendation(recommendation);
    }

    private void advanceGuidance(OfferSnapshot offer) {
        if (offer.getState() == OfferSnapshot.State.EMPTY && activeOpportunity == null) {
            selectNextBuySuggestion();
            return;
        }
        if (offer.getState() == OfferSnapshot.State.CANCELLED && offer.getFilledQuantity() == 0) {
            selectNextBuySuggestion();
            return;
        }
        if (offer.getSide() == OfferSnapshot.Side.BUY
            && (offer.getState() == OfferSnapshot.State.COMPLETED || offer.getState() == OfferSnapshot.State.CANCELLED)
            && offer.getFilledQuantity() > 0) {
            if (activateCompletedOffer(offer, GeRecommendationOverlay.GuidedPhase.COLLECT_BUY,
                offer.getFilledQuantity())) return;
        }
        if (offer.getSide() == OfferSnapshot.Side.SELL
            && (offer.getState() == OfferSnapshot.State.COMPLETED || offer.getState() == OfferSnapshot.State.CANCELLED)) {
            if (activateCompletedOffer(offer, GeRecommendationOverlay.GuidedPhase.COLLECT_SELL,
                offer.getTotalQuantity() - offer.getFilledQuantity())) return;
        }
        if (activeOpportunity == null) return;
        if (offer.getItemId() == activeOpportunity.itemId) {
            activeSlot = offer.getSlot();
            if (offer.getSide() == OfferSnapshot.Side.BUY) {
                guidedQuantity = offer.getTotalQuantity();
                if (offer.getState() == OfferSnapshot.State.COMPLETED || offer.getState() == OfferSnapshot.State.CANCELLED) {
                    quantityAfterCollection = offer.getFilledQuantity();
                    guidedPhase = GeRecommendationOverlay.GuidedPhase.COLLECT_BUY;
                } else {
                    selectNextBuySuggestion();
                    return;
                }
            } else {
                guidedQuantity = offer.getTotalQuantity();
                if (offer.getState() == OfferSnapshot.State.COMPLETED || offer.getState() == OfferSnapshot.State.CANCELLED) {
                    quantityAfterCollection = offer.getTotalQuantity() - offer.getFilledQuantity();
                    guidedPhase = GeRecommendationOverlay.GuidedPhase.COLLECT_SELL;
                } else {
                    selectNextBuySuggestion();
                    return;
                }
            }
        } else if (offer.getState() == OfferSnapshot.State.EMPTY && offer.getSlot() == activeSlot) {
            if (guidedPhase == GeRecommendationOverlay.GuidedPhase.COLLECT_BUY) {
                if (quantityAfterCollection <= 0) { suggestionIndex++; selectActiveSuggestion(); return; }
                guidedQuantity = quantityAfterCollection;
                guidedPhase = GeRecommendationOverlay.GuidedPhase.SELL;
                applyActiveRecommendation(activeOpportunity);
            } else if (guidedPhase == GeRecommendationOverlay.GuidedPhase.COLLECT_SELL) {
                if (quantityAfterCollection > 0) {
                    guidedQuantity = quantityAfterCollection;
                    guidedPhase = GeRecommendationOverlay.GuidedPhase.SELL;
                    applyActiveRecommendation(activeOpportunity);
                    recommendationOverlay.showPhase(guidedPhase, activeSlot, guidedQuantity);
                    panel.showGuidedPhase(activeOpportunity, guidedPhase, guidedQuantity);
                    return;
                }
                suggestionIndex++;
                selectActiveSuggestion();
                return;
            }
        } else return;
        recommendationOverlay.showPhase(guidedPhase, activeSlot, guidedQuantity);
        panel.showGuidedPhase(activeOpportunity, guidedPhase, guidedQuantity);
    }

    private boolean activateCompletedOffer(OfferSnapshot offer, GeRecommendationOverlay.GuidedPhase phase,
        int quantityAfterCollection) {
        if (activeSlot == offer.getSlot() && guidedPhase == phase && activeOpportunity != null
            && activeOpportunity.itemId == offer.getItemId()) return true;
        Opportunity completed = opportunityForItem(offer.getItemId(), Math.max(1, offer.getFilledQuantity()));
        if (completed == null) return false;
        activeOpportunity = completed;
        activeSlot = offer.getSlot();
        guidedQuantity = offer.getFilledQuantity();
        this.quantityAfterCollection = quantityAfterCollection;
        guidedPhase = phase;
        itemSearchAssistant.setOpportunity(completed);
        recommendationOverlay.showOpportunity(completed);
        recommendationOverlay.showPhase(guidedPhase, activeSlot, guidedQuantity);
        panel.showActiveOpportunity(completed, riskLevel);
        panel.showGuidedPhase(completed, guidedPhase, guidedQuantity);
        applyActiveRecommendation(completed);
        return true;
    }

    private boolean reconcileCompletedOffers() {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        int usable = client.getWorldType().contains(WorldType.MEMBERS) ? 8 : 3;
        for (int slot = 0; slot < Math.min(usable, offers.length); slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer == null || offer.getQuantitySold() <= 0) continue;
            GrandExchangeOfferState state = offer.getState();
            boolean completedBuy = state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY;
            boolean completedSell = state == GrandExchangeOfferState.SOLD
                || state == GrandExchangeOfferState.CANCELLED_SELL;
            if (!completedBuy && !completedSell) continue;
            OfferSnapshot snapshot = observer.snapshot(slot, offer, Instant.now());
            GeRecommendationOverlay.GuidedPhase phase = completedBuy
                ? GeRecommendationOverlay.GuidedPhase.COLLECT_BUY
                : GeRecommendationOverlay.GuidedPhase.COLLECT_SELL;
            int remaining = completedBuy ? snapshot.getFilledQuantity()
                : snapshot.getTotalQuantity() - snapshot.getFilledQuantity();
            if (activateCompletedOffer(snapshot, phase, remaining)) return true;
        }
        return false;
    }

    private void selectNextBuySuggestion() {
        activeOpportunity = null;
        activeSlot = -1;
        guidedPhase = GeRecommendationOverlay.GuidedPhase.BUY;
        suggestionIndex = 0;
        guidedQuantity = 0;
        quantityAfterCollection = 0;
        panel.showActiveOpportunity(null, riskLevel);
        recommendationOverlay.showOpportunity(null);
        recommendationOverlay.showPhase(guidedPhase, activeSlot, guidedQuantity);
        inputAssistant.clear();
        itemSearchAssistant.clear();
        guidanceRefreshPending = true;
    }

    private Opportunity opportunityForItem(int itemId, int quantity) {
        if (usingRecommendationService) {
            for (ExternalRecommendationClient.Candidate item : serviceCandidates) {
                if (item.itemId != itemId || item.sellPrice <= item.buyPrice) continue;
                long tax = GeTaxCalculator.taxPerItem(item.sellPrice);
                long profit = item.sellPrice - item.buyPrice - tax;
                if (profit <= 0) return null;
                try {
                    return new Opportunity(item.itemId, itemCatalog.name(item.itemId), item.buyPrice,
                        item.sellPrice, Math.max(1, quantity), tax, profit,
                        Math.multiplyExact(profit, Math.max(1, quantity)), (double)profit/item.buyPrice,
                        0, item.generatedAt, "OPEN POSITION", Double.MAX_VALUE);
                } catch (ArithmeticException ignored) { return null; }
            }
        }
        for (MarketItemSnapshot item : marketUniverse) {
            if (item.itemId != itemId || item.high <= 0 || item.low <= 0) continue;
            long tax = GeTaxCalculator.taxPerItem(item.high);
            long profit = item.high - item.low - tax;
            long volume = Math.min(item.highVolume, item.lowVolume);
            Instant timestamp = item.highTime.isBefore(item.lowTime) ? item.highTime : item.lowTime;
            try {
                return new Opportunity(item.itemId, item.name, item.low, item.high, Math.max(1, quantity),
                    tax, profit, Math.multiplyExact(profit, Math.max(1, quantity)),
                    item.low <= 0 ? 0 : (double)profit / item.low,
                    volume, timestamp, "OPEN POSITION", Double.MAX_VALUE);
            } catch (ArithmeticException ignored) { return null; }
        }
        return null;
    }
    private String profileId() { return "local-" + Integer.toUnsignedString(Objects.hash(client.getAccountHash()), 36); }
}
