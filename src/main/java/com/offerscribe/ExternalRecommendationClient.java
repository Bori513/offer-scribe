package com.offerscribe;

import com.google.gson.Gson;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.*;

/** Fetches untrusted, read-only opportunity data without disclosing player state. */
@Singleton
final class ExternalRecommendationClient {
    static final class Candidate {
        final int itemId;
        final long buyPrice;
        final long sellPrice;
        final int maxQuantity;
        final String risk;
        final double score;
        final Instant generatedAt;
        final Instant expiresAt;

        Candidate(int itemId, long buyPrice, long sellPrice, int maxQuantity, String risk,
            double score, Instant generatedAt, Instant expiresAt) {
            this.itemId=itemId;this.buyPrice=buyPrice;this.sellPrice=sellPrice;
            this.maxQuantity=maxQuantity;this.risk=risk;this.score=score;
            this.generatedAt=generatedAt;this.expiresAt=expiresAt;
        }
    }

    private final OkHttpClient http;
    private final Gson gson;
    private final OfferScribeConfig config;
    private final Set<Call> calls=ConcurrentHashMap.newKeySet();

    @Inject ExternalRecommendationClient(OkHttpClient http,Gson gson,OfferScribeConfig config) {
        this.http=http;this.gson=gson;this.config=config;
    }

    void fetch(Consumer<List<Candidate>> success,Consumer<String> failure) {
        HttpUrl endpoint=validEndpoint();
        if(endpoint==null){failure.accept("Recommendation service needs HTTPS (HTTP is local-only)");return;}
        Request request=new Request.Builder().url(endpoint).get()
            .header("Accept","application/json").header("User-Agent","Offer Scribe RuneLite plugin/0.2.0").build();
        Call call=http.newCall(request);calls.add(call);call.enqueue(new Callback(){
            @Override public void onFailure(Call call,IOException error){calls.remove(call);failure.accept("Recommendation service unavailable");}
            @Override public void onResponse(Call call,Response response){
                calls.remove(call);
                try(Response ignored=response){
                    if(!response.isSuccessful()||response.body()==null){failure.accept("Recommendation service returned "+response.code());return;}
                    List<Candidate> values=parse(gson,response.body().string(),Instant.now());
                    if(values.isEmpty()){failure.accept("Recommendation service returned no valid opportunities");return;}
                    success.accept(values);
                }catch(IOException|RuntimeException error){failure.accept("Invalid recommendation response");}
            }
        });
    }

    void stop(){for(Call call:calls)call.cancel();calls.clear();}

    private HttpUrl validEndpoint(){
        HttpUrl value=HttpUrl.parse(config.recommendationServiceUrl().trim());if(value==null)return null;
        boolean loopback="localhost".equalsIgnoreCase(value.host())||"127.0.0.1".equals(value.host())||"::1".equals(value.host());
        return value.isHttps()||loopback&&"http".equals(value.scheme())?value:null;
    }

    static List<Candidate> parse(Gson gson,String json,Instant now){
        Payload payload=gson.fromJson(json,Payload.class);
        if(payload==null||payload.schema_version!=1||payload.opportunities==null)return Collections.emptyList();
        long generated=payload.generated_at;long ttl=Math.min(3600,Math.max(1,payload.expires_in_seconds));
        if(generated<=0)return Collections.emptyList();
        Instant generatedAt=Instant.ofEpochSecond(generated),expiresAt=generatedAt.plusSeconds(ttl);
        if(generatedAt.isAfter(now.plusSeconds(60))||!expiresAt.isAfter(now))return Collections.emptyList();
        List<Candidate> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(RemoteOpportunity value:payload.opportunities){
            if(value==null||value.item_id<=0||value.buy_price<=0||value.sell_price<=value.buy_price
                ||value.max_quantity<=0||!Double.isFinite(value.score)||!seen.add(value.item_id))continue;
            String risk=value.risk==null?"MEDIUM":value.risk.toUpperCase(Locale.ROOT);
            if(!risk.equals("LOW")&&!risk.equals("MEDIUM")&&!risk.equals("HIGH"))risk="MEDIUM";
            result.add(new Candidate(value.item_id,value.buy_price,value.sell_price,value.max_quantity,
                risk,value.score,generatedAt,expiresAt));
            if(result.size()>=200)break;
        }
        result.sort(Comparator.comparingDouble((Candidate value)->value.score).reversed().thenComparingInt(value->value.itemId));
        return result;
    }

    private static final class Payload {int schema_version;long generated_at;long expires_in_seconds;List<RemoteOpportunity> opportunities;}
    private static final class RemoteOpportunity {int item_id;long buy_price;long sell_price;int max_quantity;String risk;double score;}
}
