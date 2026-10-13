package com.offerscribe;

import com.offerscribe.model.*;
import java.awt.*;
import java.time.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class GeTradingPanel extends PluginPanel {
    private static final Color ACCENT = new Color(52, 211, 190), MUTED = new Color(148, 163, 184), CARD = new Color(24, 34, 49);
    private final JLabel connection = new JLabel(" MARKET OFF "), cash = new JLabel("Inventory cash unavailable"),
        marketStatus = new JLabel("Enable market data to begin"), syncStatus = new JLabel("Dashboard sync off"), summary = new JLabel("No fills observed this session");
    private final JEditorPane active = html(), detail = html();
    private final DefaultListModel<String> ideas = new DefaultListModel<>(), offers = new DefaultListModel<>(), fills = new DefaultListModel<>();
    private final JRadioButton low = risk("Low"), medium = risk("Med"), high = risk("High");
    private final JButton skip = new JButton("Skip suggestion  ›");

    GeTradingPanel() {
        super(false); setLayout(new BorderLayout()); setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel body = vertical(); body.setBorder(new EmptyBorder(12, 10, 14, 10));
        body.add(header()); body.add(Box.createVerticalStrut(10)); body.add(card(active)); body.add(Box.createVerticalStrut(8));
        body.add(controls()); body.add(Box.createVerticalStrut(8));
        for (JLabel label : new JLabel[]{cash, marketStatus, syncStatus, summary}) { label.setForeground(label == cash ? Color.WHITE : MUTED); body.add(label); body.add(Box.createVerticalStrut(3)); }
        body.add(Box.createVerticalStrut(6)); body.add(tabs()); body.add(Box.createVerticalStrut(8));
        JLabel note = new JLabel("Guidance only — every game action stays manual."); note.setForeground(MUTED); note.setFont(note.getFont().deriveFont(10f)); body.add(note);
        JScrollPane scroll = new JScrollPane(body); scroll.setBorder(null); scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16); add(scroll); showMarketDisabled(); showActiveOpportunity(null, "MEDIUM");
    }

    private JPanel header() {
        JPanel value = new JPanel(new BorderLayout()); value.setOpaque(false); JPanel names = vertical();
        JLabel title = new JLabel("Offer Scribe"); title.setForeground(Color.WHITE); title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel sub = new JLabel("Grand Exchange copilot"); sub.setForeground(MUTED); names.add(title); names.add(sub);
        connection.setOpaque(true); connection.setBorder(new EmptyBorder(4, 6, 4, 6)); connection.setFont(connection.getFont().deriveFont(Font.BOLD, 10f));
        value.add(names, BorderLayout.WEST); value.add(connection, BorderLayout.EAST); return value;
    }

    private JPanel controls() {
        JPanel value = vertical(); value.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(45, 59, 75)), new EmptyBorder(8, 8, 8, 8)));
        JLabel title = new JLabel("RISK TOLERANCE"); title.setForeground(MUTED); title.setFont(title.getFont().deriveFont(Font.BOLD, 10f)); value.add(title);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 4)); row.setOpaque(false); ButtonGroup group = new ButtonGroup();
        for (JRadioButton button : new JRadioButton[]{low, medium, high}) { group.add(button); row.add(button); } medium.setSelected(true); value.add(row);
        skip.setAlignmentX(Component.LEFT_ALIGNMENT); skip.setFocusPainted(false); value.add(skip); return value;
    }

    private JTabbedPane tabs() {
        JTabbedPane value = new JTabbedPane(); value.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 270));
        value.addTab("Ideas", list(ideas)); value.addTab("Offers", list(offers)); value.addTab("Fills", list(fills));
        value.addTab("Details", new JScrollPane(card(detail))); return value;
    }

    private static JScrollPane list(ListModel<String> model) {
        JList<String> value = new JList<>(model); value.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        value.setCellRenderer((list, text, index, selected, focus) -> { JLabel label = new JLabel(text); label.setOpaque(true);
            label.setBorder(new EmptyBorder(7, 7, 7, 7)); label.setBackground(selected ? new Color(38, 64, 74) : CARD); label.setForeground(Color.WHITE); return label; });
        return new JScrollPane(value);
    }
    private static JPanel vertical() { JPanel value = new JPanel(); value.setOpaque(false); value.setLayout(new BoxLayout(value, BoxLayout.Y_AXIS)); return value; }
    private static JPanel card(JEditorPane pane) { JPanel value = new JPanel(new BorderLayout()); value.setBackground(CARD);
        value.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(45, 59, 75)), new EmptyBorder(10, 10, 10, 10))); value.add(pane); return value; }
    private static JEditorPane html() { JEditorPane value = new JEditorPane("text/html", ""); value.setEditable(false); value.setOpaque(false); value.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true); return value; }
    private static JRadioButton risk(String text) { JRadioButton value = new JRadioButton(text); value.setOpaque(false); value.setForeground(Color.WHITE); value.setFocusPainted(false); return value; }

    void setSuggestionControls(Consumer<String> change, Runnable next) { low.addActionListener(e -> change.accept("LOW")); medium.addActionListener(e -> change.accept("MEDIUM")); high.addActionListener(e -> change.accept("HIGH")); skip.addActionListener(e -> next.run()); }
    void showActiveOpportunity(Opportunity value, String selectedRisk) {
        String text = value == null ? "<html><b>No suitable opportunity</b><br><font color='#94a3b8'>Keep coins in inventory or choose another risk.</font></html>"
            : String.format("<html><font color='#34d3be'><b>NEXT · BUY</b></font><br><font size='+1'><b>%,d × %s</b></font><br>Buy %,d · sell %,d gp<br><font color='#86efac'><b>+%,d gp</b></font> · %.2f%% ROI<br><font color='#94a3b8'>%s risk · %,d volume</font></html>", value.quantity, value.name, value.buyPrice, value.sellPrice, value.expectedProfit, value.roi * 100, value.risk, value.balancedHourlyVolume);
        SwingUtilities.invokeLater(() -> { active.setText(text); low.setSelected("LOW".equals(selectedRisk)); medium.setSelected("MEDIUM".equals(selectedRisk)); high.setSelected("HIGH".equals(selectedRisk)); skip.setEnabled(value != null); });
    }
    void showGuidedPhase(Opportunity value, GeRecommendationOverlay.GuidedPhase phase, int quantity) {
        if (value == null) return; String action;
        switch (phase) { case WAIT_BUY: action="WAIT · BUY OFFER OPEN"; break; case COLLECT_BUY: action="COLLECT · BOUGHT ITEMS"; break; case SELL: action="NEXT · SELL"; break; case WAIT_SELL: action="WAIT · SELL OFFER OPEN"; break; case COLLECT_SELL: action="COLLECT · SALE PROCEEDS"; break; default: action="NEXT · BUY"; }
        long price = phase == GeRecommendationOverlay.GuidedPhase.SELL ? value.sellPrice : value.buyPrice;
        int amount = quantity > 0 ? quantity : value.quantity;
        long profit = Math.multiplyExact(value.profitPerItem, amount);
        String text = String.format("<html><font color='#34d3be'><b>%s</b></font><br><font size='+1'><b>%,d × %s</b></font><br>Target %,d gp<br><font color='#86efac'>Expected +%,d gp · %.2f%% ROI</font></html>", action, amount, value.name, price, profit, value.roi * 100);
        SwingUtilities.invokeLater(() -> active.setText(text));
    }
    void showOpportunityLoading() { SwingUtilities.invokeLater(() -> marketStatus.setText("Refreshing Wiki market evidence…")); }
    void showOpportunities(List<Opportunity> values) { SwingUtilities.invokeLater(() -> { ideas.clear(); for (Opportunity value : values) ideas.addElement(String.format("<html><b>%s</b> · %s<br>%,d → %,d gp · qty %,d<br><font color='#86efac'>+%,d gp · %.2f%%</font></html>", value.name, value.risk, value.buyPrice, value.sellPrice, value.quantity, value.expectedProfit, value.roi * 100)); marketStatus.setText(values.isEmpty() ? "No fresh, liquid candidates for this cash/risk level" : values.size() + " ranked ideas · transparent model"); }); }
    void showOpportunityError(String message) { SwingUtilities.invokeLater(() -> marketStatus.setText("Refresh failed: " + message)); }
    void showSyncStatus(String message) { SwingUtilities.invokeLater(() -> syncStatus.setText(message)); }
    void showAvailableCash(long coins) { SwingUtilities.invokeLater(() -> cash.setText(String.format("Available cash  %,d gp", coins))); }
    void showMarketDisabled() { SwingUtilities.invokeLater(() -> { connection.setText(" MARKET OFF "); connection.setBackground(new Color(71,45,48)); connection.setForeground(new Color(248,113,113)); detail.setText("<html><b>Recommendations are off</b><br>Enable External market data in settings.</html>"); }); }
    void showMarketEnabled() { SwingUtilities.invokeLater(() -> { connection.setText(" WIKI LIVE "); connection.setBackground(new Color(24,78,67)); connection.setForeground(new Color(110,231,183)); }); }
    void showLoading(int itemId) { SwingUtilities.invokeLater(() -> detail.setText("<html><b>Checking item " + itemId + "…</b></html>")); }
    void showMarketError(String message) { SwingUtilities.invokeLater(() -> detail.setText("<html><b>No recommendation</b><br>" + message + "</html>")); }
    void showRecommendation(Recommendation value) { long age = Math.max(0, Duration.between(value.dataTimestamp, Instant.now()).toMinutes()); String text = String.format("<html><font color='#34d3be'><b>%s manually</b></font><br><font size='+1'><b>%,d gp</b></font> · qty %,d<br>%,d gp/unit · %.2f%% ROI<br><font color='#94a3b8'>Data age %d min<br>%s</font></html>", value.side, value.suggestedPrice, value.suggestedQuantity, value.expectedUnitProfit, value.roi * 100, age, value.evidence); SwingUtilities.invokeLater(() -> detail.setText(text)); }
    void observeOffer(OfferSnapshot value, String name) { SwingUtilities.invokeLater(() -> { while (offers.size() <= value.getSlot()) offers.addElement("Waiting…"); String text = value.getState() == OfferSnapshot.State.EMPTY ? "Slot " + (value.getSlot()+1) + " · Empty" : String.format("<html><b>Slot %d · %s %s</b><br>%,d/%,d @ %,d gp · %s</html>", value.getSlot()+1, value.getSide(), name, value.getFilledQuantity(), value.getTotalQuantity(), value.getPricePerItem(), value.getState()); offers.set(value.getSlot(), text); }); }
    void addFill(TradeEvent value, String name, int count) { SwingUtilities.invokeLater(() -> { summary.setText(count + " immutable fill event(s)"); fills.add(0, String.format("<html><b>%s %s</b><br>%,d for %,d gp</html>", value.getSide(), name, value.getQuantityDelta(), value.getCoinsDelta())); }); }
    void setEventCount(int count) { SwingUtilities.invokeLater(() -> summary.setText(count + " immutable fill event(s)")); }
}
