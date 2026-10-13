package com.offerscribe;

import com.offerscribe.model.*;
import java.awt.*;
import java.time.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Compact RuneLite-native sidebar. Trading logic stays outside this view. */
final class GeTradingPanel extends PluginPanel {
    private static final Color GREEN=new Color(76,224,126), CYAN=new Color(67,211,205), MUTED=new Color(195,195,195), PANEL=ColorScheme.DARKER_GRAY_COLOR, BORDER=new Color(62,62,62);
    private final JLabel connection=new JLabel("MARKET OFF"), cash=muted("Cash unavailable"), sourceStatus=muted("Source unavailable"), marketStatus=muted("Enable market data to begin"), syncStatus=muted("API export off"), summary=muted("No fills this session");
    private final JEditorPane active=html(), detail=html();
    private final DefaultListModel<String> ideas=new DefaultListModel<>(),offers=new DefaultListModel<>(),fills=new DefaultListModel<>();
    private final JRadioButton low=risk("Low"),medium=risk("Med"),high=risk("High");
    private final JButton skip=new JButton("⏭  Skip");
    private final JButton block=new JButton("Block");

    GeTradingPanel(){
        super(false);setLayout(new BorderLayout(0,6));setBackground(ColorScheme.DARK_GRAY_COLOR);setBorder(new EmptyBorder(8,8,8,8));
        JPanel top=vertical();top.add(header());top.add(Box.createVerticalStrut(7));top.add(suggestion());top.add(Box.createVerticalStrut(5));top.add(actionRow());top.add(Box.createVerticalStrut(5));top.add(preferences());top.add(Box.createVerticalStrut(5));top.add(status());
        add(top,BorderLayout.NORTH);add(tabs(),BorderLayout.CENTER);
        JLabel note=muted("Guidance only · every game action stays manual");note.setFont(note.getFont().deriveFont(9f));add(note,BorderLayout.SOUTH);
        sourceStatus.setForeground(CYAN);sourceStatus.setFont(sourceStatus.getFont().deriveFont(Font.BOLD,12f));
        showMarketDisabled();showActiveOpportunity(null,"MEDIUM");
    }
    private JPanel header(){JPanel p=new JPanel(new BorderLayout());p.setOpaque(false);JLabel title=new JLabel("Offer Scribe");title.setForeground(Color.WHITE);title.setFont(title.getFont().deriveFont(Font.BOLD,16f));connection.setOpaque(true);connection.setBorder(new EmptyBorder(3,6,3,6));connection.setFont(connection.getFont().deriveFont(Font.BOLD,9f));p.add(title,BorderLayout.WEST);p.add(connection,BorderLayout.EAST);return fixed(p,28);}
    private JPanel suggestion(){JPanel p=new JPanel(new BorderLayout());p.setBackground(PANEL);p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),new EmptyBorder(9,9,7,9)));active.setPreferredSize(new Dimension(PANEL_WIDTH-36,100));p.add(active);return fixed(p,120);}
    private JPanel actionRow(){JPanel p=new JPanel(new BorderLayout(6,0));p.setOpaque(false);JLabel guide=new JLabel("BUY  →  COLLECT  →  SELL");guide.setForeground(MUTED);guide.setFont(guide.getFont().deriveFont(Font.BOLD,9f));JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,0));actions.setOpaque(false);for(JButton button:new JButton[]{block,skip}){button.setFocusable(false);button.setMargin(new Insets(3,6,3,6));actions.add(button);}p.add(guide,BorderLayout.WEST);p.add(actions,BorderLayout.EAST);return fixed(p,27);}
    private JPanel preferences(){JPanel p=new JPanel(new BorderLayout());p.setBackground(PANEL);p.setBorder(new EmptyBorder(5,7,5,7));JLabel label=new JLabel("Risk");label.setForeground(MUTED);JPanel choices=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));choices.setOpaque(false);ButtonGroup group=new ButtonGroup();for(JRadioButton b:new JRadioButton[]{low,medium,high}){group.add(b);choices.add(b);}medium.setSelected(true);p.add(label,BorderLayout.WEST);p.add(choices,BorderLayout.EAST);return fixed(p,31);}
    private JPanel status(){JPanel p=new JPanel(new GridLayout(0,1,0,3));p.setOpaque(false);for(JLabel l:new JLabel[]{cash,sourceStatus,marketStatus,syncStatus,summary})p.add(l);return fixed(p,96);}
    private JTabbedPane tabs(){JTabbedPane p=new JTabbedPane();p.addTab("Ideas",list(ideas));p.addTab("Offers",list(offers));p.addTab("Fills",list(fills));JScrollPane info=new JScrollPane(detail);info.setBorder(null);p.addTab("Info",info);return p;}
    private static JScrollPane list(ListModel<String> model){JList<String> list=new JList<>(model);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(64);list.setCellRenderer((owner,text,index,selected,focus)->{JLabel l=new JLabel(text);l.setOpaque(true);l.setBorder(new EmptyBorder(6,7,6,7));l.setBackground(selected?ColorScheme.DARKER_GRAY_HOVER_COLOR:PANEL);l.setForeground(Color.WHITE);l.setFont(l.getFont().deriveFont(13f));return l;});JScrollPane p=new JScrollPane(list);p.setBorder(null);p.getVerticalScrollBar().setUnitIncrement(12);return p;}
    private static JPanel vertical(){JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    private static JPanel fixed(JPanel p,int height){p.setAlignmentX(Component.LEFT_ALIGNMENT);p.setPreferredSize(new Dimension(PANEL_WIDTH-16,height));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));return p;}
    private static JEditorPane html(){JEditorPane p=new JEditorPane();HTMLEditorKit kit=new HTMLEditorKit();kit.getStyleSheet().addRule("body { font-family: sans-serif; font-size: 13pt; color: #ffffff; margin: 0; } b { font-weight: bold; }");p.setEditorKit(kit);p.setContentType("text/html");p.setEditable(false);p.setOpaque(false);p.setBorder(null);p.setForeground(Color.WHITE);p.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,true);return p;}
    private static JLabel muted(String text){JLabel l=new JLabel(text);l.setForeground(MUTED);l.setFont(l.getFont().deriveFont(Font.BOLD,13f));return l;}
    private static JRadioButton risk(String text){JRadioButton b=new JRadioButton(text);b.setOpaque(false);b.setForeground(Color.WHITE);b.setFocusable(false);b.setFont(b.getFont().deriveFont(10f));return b;}

    void setSuggestionControls(Consumer<String> change,Runnable next,Runnable blockCurrent){low.addActionListener(e->change.accept("LOW"));medium.addActionListener(e->change.accept("MEDIUM"));high.addActionListener(e->change.accept("HIGH"));skip.addActionListener(e->next.run());block.addActionListener(e->blockCurrent.run());}
    void showActiveOpportunity(Opportunity v,String selected){String text=v==null?"<html><b>No suitable opportunity</b><br><font color='#a5a5a5'>Keep coins in inventory or change risk.</font></html>":String.format("<html><font color='#43d3cd'><b>NEXT  ·  BUY</b></font><br><b>%,d × %s</b><br>Buy %,d  ·  sell %,d gp<br><font color='#4ce07e'><b>+%,d gp</b></font>  ·  %.2f%% ROI</html>",v.quantity,v.name,v.buyPrice,v.sellPrice,v.expectedProfit,v.roi*100);SwingUtilities.invokeLater(()->{active.setText(text);low.setSelected("LOW".equals(selected));medium.setSelected("MEDIUM".equals(selected));high.setSelected("HIGH".equals(selected));skip.setEnabled(v!=null);block.setEnabled(v!=null);});}
    void showGuidedPhase(Opportunity v,GeRecommendationOverlay.GuidedPhase phase,int quantity){if(v==null)return;String action;switch(phase){case WAIT_BUY:action="WAIT  ·  BUY OFFER OPEN";break;case COLLECT_BUY:action="COLLECT  ·  BOUGHT ITEMS";break;case SELL:action="NEXT  ·  SELL";break;case WAIT_SELL:action="WAIT  ·  SELL OFFER OPEN";break;case COLLECT_SELL:action="COLLECT  ·  SALE PROCEEDS";break;default:action="NEXT  ·  BUY";}long price=phase==GeRecommendationOverlay.GuidedPhase.SELL?v.sellPrice:v.buyPrice;int amount=quantity>0?quantity:v.quantity;long profit=Math.multiplyExact(v.profitPerItem,amount);String text=String.format("<html><font color='#43d3cd'><b>%s</b></font><br><b>%,d × %s</b><br>Target %,d gp<br><font color='#4ce07e'>Expected +%,d gp  ·  %.2f%% ROI</font></html>",action,amount,v.name,price,profit,v.roi*100);SwingUtilities.invokeLater(()->active.setText(text));}
    void showOpportunityLoading(){SwingUtilities.invokeLater(()->marketStatus.setText("Refreshing Wiki prices…"));}
    void showOpportunityError(String message){SwingUtilities.invokeLater(()->marketStatus.setText("Refresh failed: "+message));}
    void showSyncStatus(String message){SwingUtilities.invokeLater(()->syncStatus.setText(message));}
    void showAvailableCash(long coins){SwingUtilities.invokeLater(()->cash.setText("Cash: "+compact(coins)+" gp"));}
    void showRecommendationSource(String source){SwingUtilities.invokeLater(()->{sourceStatus.setText("Source: "+source);sourceStatus.setToolTipText(source);});}
    void showMarketDisabled(){SwingUtilities.invokeLater(()->{connection.setText("MARKET OFF");connection.setBackground(new Color(75,42,42));connection.setForeground(new Color(255,130,130));detail.setText("<html><b>Recommendations off</b><br>Enable External market data in settings.</html>");});}
    void showMarketEnabled(){SwingUtilities.invokeLater(()->{connection.setText("DATA ON");connection.setBackground(new Color(30,72,58));connection.setForeground(GREEN);});}
    void showLoading(int itemId){SwingUtilities.invokeLater(()->detail.setText("<html><b>Checking item "+itemId+"…</b></html>"));}
    void showMarketError(String message){SwingUtilities.invokeLater(()->detail.setText("<html><b>No recommendation</b><br>"+message+"</html>"));}
    void showOpportunities(List<Opportunity> values){SwingUtilities.invokeLater(()->{ideas.clear();for(Opportunity v:values)ideas.addElement(String.format("<html><b>%s</b>  <font color='#a5a5a5'>%s</font><br>%,d → %,d gp  ·  qty %,d<br><font color='#4ce07e'>+%,d gp  ·  %.2f%%</font></html>",v.name,v.risk,v.buyPrice,v.sellPrice,v.quantity,v.expectedProfit,v.roi*100));marketStatus.setText(values.isEmpty()?"No suitable candidates":values.size()+" ranked ideas");});}
    void showRecommendation(Recommendation v){long age=Math.max(0,Duration.between(v.dataTimestamp,Instant.now()).toMinutes());String text=String.format("<html><font color='#43d3cd'><b>%s recommendation</b></font><br><b>%,d gp</b>  ·  qty %,d<br>%,d gp/unit  ·  %.2f%% ROI<br><font color='#a5a5a5'>Data age %d min<br>%s</font></html>",v.side,v.suggestedPrice,v.suggestedQuantity,v.expectedUnitProfit,v.roi*100,age,v.evidence);SwingUtilities.invokeLater(()->detail.setText(text));}
    void observeOffer(OfferSnapshot v,String name){SwingUtilities.invokeLater(()->{while(offers.size()<=v.getSlot())offers.addElement("Waiting…");String text=v.getState()==OfferSnapshot.State.EMPTY?"Slot "+(v.getSlot()+1)+"  ·  Empty":String.format("<html><b>Slot %d  ·  %s %s</b><br>%,d/%,d @ %,d gp<br><font color='#a5a5a5'>%s</font></html>",v.getSlot()+1,v.getSide(),name,v.getFilledQuantity(),v.getTotalQuantity(),v.getPricePerItem(),v.getState());offers.set(v.getSlot(),text);});}
    void addFill(TradeEvent v,String name,int count){SwingUtilities.invokeLater(()->{summary.setText(count+" recorded fill"+(count==1?"":"s"));fills.add(0,String.format("<html><b>%s %s</b><br>%,d items  ·  %,d gp</html>",v.getSide(),name,v.getQuantityDelta(),v.getCoinsDelta()));});}
    void setEventCount(int count){SwingUtilities.invokeLater(()->summary.setText(count+" recorded fill"+(count==1?"":"s")));}
    private static String compact(long value){if(value>=1_000_000_000)return String.format("%.2fB",value/1_000_000_000d);if(value>=1_000_000)return String.format("%.2fM",value/1_000_000d);if(value>=1_000)return String.format("%.1fK",value/1_000d);return Long.toString(value);}
}
