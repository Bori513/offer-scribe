package com.offerscribe;

import com.offerscribe.model.Recommendation;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.VarClientStr;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;

/** Direct-click GE input helper. Filling a field never confirms or submits it. */
@Singleton
final class GeInputAssistant {
    private static final int INPUT_INT=7,INPUT_LONG=30;
    private final Client client;private final ClientThread clientThread;private volatile Recommendation recommendation;private Widget prompt;
    @Inject GeInputAssistant(Client client,ClientThread clientThread){this.client=client;this.clientThread=clientThread;}
    void start(){}
    void stop(){recommendation=null;prompt=null;}
    void setRecommendation(Recommendation value){recommendation=value;clientThread.invokeLater(this::showPromptIfOpen);}
    void clear(){recommendation=null;}
    void onVarClientIntChanged(VarClientIntChanged event){if(event.getIndex()!=VarClientID.MESLAYERMODE)return;int mode=client.getVarcIntValue(VarClientID.MESLAYERMODE);if(mode==INPUT_INT||mode==INPUT_LONG)clientThread.invokeLater(this::showPromptIfOpen);else prompt=null;}
    private boolean isPromptOpen(){int mode=client.getVarcIntValue(VarClientID.MESLAYERMODE);Widget title=client.getWidget(ComponentID.CHATBOX_TITLE);return(mode==INPUT_INT||mode==INPUT_LONG)&&title!=null&&("Set a price for each item:".equals(title.getText())||"How many do you wish to buy?".equals(title.getText())||"How many do you wish to sell?".equals(title.getText()))&&client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER)!=null;}
    private boolean matches(){Recommendation value=recommendation;return value!=null&&client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)==value.itemId;}
    private void showPromptIfOpen(){if(!isPromptOpen()||!matches()||(isQuantity()&&recommendation.side==com.offerscribe.model.OfferSnapshot.Side.SELL)){if(prompt!=null)prompt.setHidden(true);return;}if(prompt!=null&&!prompt.isHidden()){update();return;}Widget parent=client.getWidget(ComponentID.CHATBOX_CONTAINER);if(parent==null)return;prompt=parent.createChild(-1,WidgetType.TEXT);prompt.setTextColor(0x0040FF);prompt.setFontId(FontID.VERDANA_11_BOLD);prompt.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);prompt.setOriginalX(10);prompt.setOriginalY(40);prompt.setOriginalHeight(20);prompt.setXTextAlignment(WidgetTextAlignment.LEFT);prompt.setWidthMode(WidgetSizeMode.MINUS);prompt.setHasListener(true);prompt.setAction(0,"Set Offer Scribe value");prompt.setOnOpListener((JavaScriptCallback)e->setSuggestedValue());prompt.setOnMouseRepeatListener((JavaScriptCallback)e->prompt.setTextColor(0xFFFFFF));prompt.setOnMouseLeaveListener((JavaScriptCallback)e->prompt.setTextColor(0x0040FF));update();Widget title=client.getWidget(ComponentID.CHATBOX_TITLE);if(title!=null){title.setOriginalY(title.getOriginalY()+7);title.revalidate();}prompt.revalidate();}
    private void update(){if(prompt==null||recommendation==null)return;prompt.setHidden(false);prompt.setText(isQuantity()?String.format("Click to set Offer Scribe quantity: %,d",recommendation.suggestedQuantity):String.format("Click to set Offer Scribe price: %,d gp",recommendation.suggestedPrice));prompt.revalidate();}
    private boolean isQuantity(){Widget title=client.getWidget(ComponentID.CHATBOX_TITLE);return title!=null&&title.getText()!=null&&title.getText().startsWith("How many do you wish to");}
    private void setSuggestedValue(){if(!isPromptOpen()||!matches())return;Widget input=client.getWidget(ComponentID.CHATBOX_FULL_INPUT);if(input==null)return;long value=isQuantity()?recommendation.suggestedQuantity:recommendation.suggestedPrice;input.setText(value+"*");client.setVarcStrValue(VarClientStr.INPUT_TEXT,Long.toString(value));if(prompt!=null)prompt.setHidden(true);}
}
