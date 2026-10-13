package com.offerscribe;

import java.awt.BorderLayout;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import com.offerscribe.model.OfferSnapshot;
import com.offerscribe.model.Opportunity;
import com.offerscribe.model.Recommendation;
import com.offerscribe.model.TradeEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import net.runelite.client.ui.PluginPanel;

final class GeTradingPanel extends PluginPanel {
    private final JLabel summary = new JLabel("No fills observed this session");
    private final JEditorPane recommendation = new JEditorPane("text/html", "");
    private final DefaultListModel<String> offers = new DefaultListModel<>();
    private final DefaultListModel<String> fills = new DefaultListModel<>();
    private final DefaultListModel<String> opportunities = new DefaultListModel<>();
    private final JLabel opportunityStatus = new JLabel("Opportunity scan is off");
    private final JLabel availableCash = new JLabel("Available inventory cash: unknown");
    private final JEditorPane activeSuggestion = new JEditorPane("text/html", "<html>No active suggestion</html>");
    private final JButton lowRisk = new JButton("Low");
    private final JButton mediumRisk = new JButton("Med");
    private final JButton highRisk = new JButton("High");
    private final JButton skip = new JButton("Skip ▶");

    GeTradingPanel() {
        setLayout(new BorderLayout());
        JPanel content = new JPanel(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Offer Scribe");
        title.setFont(title.getFont().deriveFont(18f));
        title.setForeground(new Color(65, 190, 205));
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        activeSuggestion.setEditable(false);
        activeSuggestion.setOpaque(false);
        activeSuggestion.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        content.add(activeSuggestion);
        JPanel controls = new JPanel();
        controls.add(new JLabel("Risk:")); controls.add(lowRisk); controls.add(mediumRisk);
        controls.add(highRisk); controls.add(skip);
        content.add(controls);
        content.add(Box.createVerticalStrut(6));
        content.add(new JLabel("Top opportunities"));
        content.add(availableCash);
        content.add(opportunityStatus);
        JList<String> opportunityList = new JList<>(opportunities);
        opportunityList.setVisibleRowCount(5);
        content.add(new JScrollPane(opportunityList));
        content.add(Box.createVerticalStrut(8));
        content.add(new JLabel("Selected GE item"));
        recommendation.setEditable(false);
        recommendation.setOpaque(false);
        recommendation.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        showMarketDisabled();
        content.add(recommendation);
        content.add(Box.createVerticalStrut(8));
        content.add(summary);
        content.add(Box.createVerticalStrut(8));
        content.add(new JLabel("Observed GE offers"));
        content.add(new JScrollPane(new JList<>(offers)));
        content.add(Box.createVerticalStrut(8));
        content.add(new JLabel("Observed fills"));
        content.add(new JScrollPane(new JList<>(fills)));
        content.add(new JLabel("Guidance only; you perform every game action."));
        add(content, BorderLayout.NORTH);
    }

    void setSuggestionControls(Consumer<String> riskChange, Runnable skipAction) {
        lowRisk.addActionListener(event -> riskChange.accept("LOW"));
        mediumRisk.addActionListener(event -> riskChange.accept("MEDIUM"));
        highRisk.addActionListener(event -> riskChange.accept("HIGH"));
        skip.addActionListener(event -> skipAction.run());
    }

    void showActiveOpportunity(Opportunity value, String riskLevel) {
        String html = value == null ? "<html><b>No candidate for this risk level</b></html>"
            : String.format("<html><body style='color:#e2e8f0;background:#182231;padding:8px'>"
                + "<span style='color:#42d3c8'><b>BUY %,d %s</b></span><br>"
                + "Buy at <b>%,d gp</b> · sell near <b>%,d gp</b><br>"
                + "Expected %,d gp · %.2f%% ROI<br>"
                + "<span style='color:#94a3b8'>%s risk · hourly two-sided volume %,d</span>"
                + "</body></html>", value.quantity, value.name, value.buyPrice, value.sellPrice,
                value.expectedProfit, value.roi * 100, value.risk, value.balancedHourlyVolume);
        SwingUtilities.invokeLater(() -> {
            activeSuggestion.setText(html);
            lowRisk.setEnabled(!"LOW".equals(riskLevel));
            mediumRisk.setEnabled(!"MEDIUM".equals(riskLevel));
            highRisk.setEnabled(!"HIGH".equals(riskLevel));
            skip.setEnabled(value != null);
        });
    }

    void showGuidedPhase(Opportunity value, GeRecommendationOverlay.GuidedPhase phase) {
        if (value == null) return;
        String instruction;
        switch (phase) {
            case WAIT_BUY: instruction = "Buy offer open — waiting for completion"; break;
            case COLLECT_BUY: instruction = "Collect the bought items"; break;
            case SELL: instruction = "SELL " + String.format("%,d %s near %,d gp", value.quantity, value.name, value.sellPrice); break;
            case WAIT_SELL: instruction = "Sell offer open — waiting for completion"; break;
            case COLLECT_SELL: instruction = "Collect the sale proceeds"; break;
            default: instruction = "BUY " + String.format("%,d %s at %,d gp", value.quantity, value.name, value.buyPrice);
        }
        String html = "<html><body style='color:#e2e8f0;background:#182231;padding:8px'>"
            + "<span style='color:#42d3c8'><b>" + instruction + "</b></span><br>"
            + String.format("Expected %,d gp · %.2f%% ROI<br><span style='color:#94a3b8'>%s risk</span>",
                value.expectedProfit, value.roi * 100, value.risk) + "</body></html>";
        SwingUtilities.invokeLater(() -> activeSuggestion.setText(html));
    }

    void showOpportunityLoading() {
        SwingUtilities.invokeLater(() -> opportunityStatus.setText("Refreshing OSRS Wiki market data…"));
    }

    void showOpportunities(List<Opportunity> values) {
        SwingUtilities.invokeLater(() -> {
            opportunities.clear();
            for (Opportunity value : values) {
                opportunities.addElement(String.format("<html><b>%s</b> · %s risk<br>Buy %,d → sell %,d · qty %,d<br>%,d gp · %.2f%% ROI · vol %,d</html>",
                    value.name, value.risk, value.buyPrice, value.sellPrice, value.quantity,
                    value.expectedProfit, value.roi * 100, value.balancedHourlyVolume));
            }
            opportunityStatus.setText(values.isEmpty()
                ? "No candidates passed freshness, liquidity, tax, and risk filters"
                : values.size() + " ranked candidates · transparent model v1");
        });
    }

    void showOpportunityError(String message) {
        SwingUtilities.invokeLater(() -> opportunityStatus.setText("Opportunity refresh failed: " + message));
    }

    void showAvailableCash(long coins) {
        SwingUtilities.invokeLater(() -> availableCash.setText(
            String.format("Available inventory cash: %,d gp", coins)));
    }

    void showMarketDisabled() {
        SwingUtilities.invokeLater(() -> recommendation.setText("<html><body style='color:#cbd5e1'>"
            + "<b>Recommendations are off</b><br>Enable <i>External market data</i> in Offer Scribe settings."
            + "</body></html>"));
    }

    void showLoading(int itemId) {
        SwingUtilities.invokeLater(() -> recommendation.setText("<html><b>Checking item " + itemId
            + "…</b><br>Waiting for public market evidence.</html>"));
    }

    void showMarketError(String message) {
        SwingUtilities.invokeLater(() -> recommendation.setText("<html><b>No recommendation</b><br>"
            + message + "</html>"));
    }

    void showRecommendation(Recommendation value) {
        long age = Math.max(0, Duration.between(value.dataTimestamp, Instant.now()).toMinutes());
        String action = value.side == OfferSnapshot.Side.BUY ? "BUY manually" : "SELL manually";
        String profitability = value.expectedUnitProfit > 0
            ? String.format("%,d gp/unit after tax · %.2f%% ROI", value.expectedUnitProfit, value.roi * 100)
            : "No positive observed post-tax spread — reconsider";
        String html = String.format("<html><body style='color:#e2e8f0;background:#182231;padding:8px'>"
                + "<span style='color:#42d3c8'><b>%s</b></span><br>"
                + "<span style='font-size:18px'><b>%,d gp</b></span> · qty %,d<br>"
                + "%s<br><span style='color:#94a3b8'>Data age: %d min<br>%s</span>"
                + "</body></html>", action, value.suggestedPrice, value.suggestedQuantity,
            profitability, age, value.evidence);
        SwingUtilities.invokeLater(() -> recommendation.setText(html));
    }

    void observeOffer(OfferSnapshot offer) {
        SwingUtilities.invokeLater(() -> {
            while (offers.size() <= offer.getSlot()) offers.addElement("Waiting for offer data…");
            String text = offer.getState() == OfferSnapshot.State.EMPTY
                ? "Slot " + (offer.getSlot() + 1) + " — Empty"
                : String.format("Slot %d — %s item %d: %,d/%,d @ %,d gp (%s)",
                    offer.getSlot() + 1, offer.getSide(), offer.getItemId(), offer.getFilledQuantity(),
                    offer.getTotalQuantity(), offer.getPricePerItem(), offer.getState());
            offers.set(offer.getSlot(), text);
        });
    }

    void addFill(TradeEvent fill, int count) {
        SwingUtilities.invokeLater(() -> {
            summary.setText(count + " immutable fill event(s)");
            fills.add(0, String.format("%s item %d: %,d for %,d gp", fill.getSide(),
                fill.getItemId(), fill.getQuantityDelta(), fill.getCoinsDelta()));
        });
    }

    void setEventCount(int count) { SwingUtilities.invokeLater(() -> summary.setText(count + " immutable fill event(s)")); }
}
