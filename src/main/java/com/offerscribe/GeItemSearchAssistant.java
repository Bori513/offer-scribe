package com.offerscribe;

import com.offerscribe.model.Opportunity;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

/** Direct-click suggested-item row adapted from Flipping Copilot under BSD-2-Clause. */
@Singleton
final class GeItemSearchAssistant {
    private static final int GE_SEARCH_MODE=14;
    private final Client client;private final ClientThread clientThread;private volatile Opportunity opportunity;
    @Inject GeItemSearchAssistant(Client client,ClientThread clientThread){this.client=client;this.clientThread=clientThread;}
    void setOpportunity(Opportunity value){opportunity=value;clientThread.invokeLater(this::showIfSearching);}
    void clear(){opportunity=null;}
    void onVarClientIntChanged(VarClientIntChanged event){if(event.getIndex()==VarClientID.MESLAYERMODE)clientThread.invokeLater(this::showIfSearching);}
    private void showIfSearching(){if(client.getVarcIntValue(VarClientID.MESLAYERMODE)==GE_SEARCH_MODE)show();}
    private void show(){Opportunity value=opportunity;Widget parent=client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);if(value==null||parent==null)return;
        if ((previousSearchSet() || offerScribeRowExists(parent)) && showLastSearchEnabled()) update(parent,value);
        else create(parent,value);
    }
    private boolean previousSearchSet(){return client.getVarpValue(VarPlayerID.GE_LAST_SEARCHED)!=-1;}
    private boolean showLastSearchEnabled(){return client.getVarbitValue(VarbitID.DISABLE_LAST_SEARCHED)==0;}
    private static boolean offerScribeRowExists(Widget parent){
        Widget[] children=parent.getChildren();if(children==null)return false;
        for(Widget child:children)if(child!=null&&child.getText()!=null&&child.getText().startsWith("Offer Scribe item:"))return true;
        return false;
    }
    private void update(Widget parent,Opportunity value){
        Widget select=parent.getChild(0),label=parent.getChild(1),name=parent.getChild(2),item=parent.getChild(3);
        if(select==null||label==null||name==null||item==null){create(parent,value);return;}
        select.setHasListener(true);select.setOnOpListener(754,value.itemId,84);select.setOnKeyListener(754,value.itemId,-2147483640);select.setName("<col=ff9040>"+value.name+"</col>");select.setAction(0,"Select");select.revalidate();
        label.setText("Offer Scribe item:");label.setOriginalWidth(110);label.setXTextAlignment(WidgetTextAlignment.LEFT);label.revalidate();
        name.setText(value.name);name.revalidate();item.setItemId(value.itemId);item.revalidate();
    }
    private void create(Widget parent,Opportunity value){
        Widget select=parent.createChild(0,WidgetType.RECTANGLE);select.setTextColor(0xFFFFFF);select.setOpacity(255);select.setFilled(true);select.setName("<col=ff9040>"+value.name+"</col>");select.setHasListener(true);select.setOriginalX(114);select.setOriginalY(0);select.setOriginalWidth(256);select.setOriginalHeight(32);select.setOnOpListener(754,value.itemId,84);select.setOnKeyListener(754,value.itemId,-2147483640);select.setAction(0,"Select");select.setOnMouseOverListener((JavaScriptCallback)e->select.setOpacity(200));select.setOnMouseLeaveListener((JavaScriptCallback)e->select.setOpacity(255));select.revalidate();
        Widget label=parent.createChild(1,WidgetType.TEXT);label.setText("Offer Scribe item:");label.setFontId(495);label.setOriginalX(114);label.setOriginalY(0);label.setOriginalWidth(110);label.setOriginalHeight(32);label.setYTextAlignment(1);label.revalidate();
        Widget name=parent.createChild(2,WidgetType.TEXT);name.setText(value.name);name.setFontId(495);name.setOriginalX(254);name.setOriginalY(0);name.setOriginalWidth(116);name.setOriginalHeight(32);name.setYTextAlignment(1);name.revalidate();
        Widget item=parent.createChild(3,WidgetType.GRAPHIC);item.setItemId(value.itemId);item.setItemQuantity(1);item.setItemQuantityMode(0);item.setRotationX(550);item.setModelZoom(1031);item.setBorderType(1);item.setOriginalX(214);item.setOriginalY(0);item.setOriginalWidth(36);item.setOriginalHeight(32);item.revalidate();
    }
}
