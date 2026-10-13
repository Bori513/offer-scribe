package com.offerscribe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.offerscribe.model.MarketPrice;
import com.offerscribe.model.MarketItemSnapshot;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Singleton
final class WikiMarketDataClient {
    private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs";
    private final OkHttpClient http;
    private final Gson gson;

    @Inject WikiMarketDataClient(OkHttpClient http, Gson gson) {
        this.http = http;
        this.gson = gson;
    }

    void fetch(int itemId, Consumer<MarketPrice> success, Consumer<String> failure) {
        Request request = new Request.Builder()
            .url(BASE + "/latest?id=" + itemId)
            .header("User-Agent", "Offer Scribe RuneLite plugin/0.2.0")
            .build();
        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException exception) {
                failure.accept("Market request failed");
            }

            @Override public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        failure.accept("Market service returned " + response.code());
                        return;
                    }
                    JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
                    JsonObject quote = root.getAsJsonObject("data").getAsJsonObject(Integer.toString(itemId));
                    if (quote == null || quote.get("high").isJsonNull() || quote.get("low").isJsonNull()) {
                        failure.accept("No recent high/low price for this item");
                        return;
                    }
                    long high = quote.get("high").getAsLong();
                    long low = quote.get("low").getAsLong();
                    Instant highTime = Instant.ofEpochSecond(quote.get("highTime").getAsLong());
                    Instant lowTime = Instant.ofEpochSecond(quote.get("lowTime").getAsLong());
                    success.accept(new MarketPrice(itemId, high, low, highTime, lowTime));
                } catch (RuntimeException exception) {
                    failure.accept("Invalid market response");
                }
            }
        });
    }

    void fetchUniverse(Consumer<List<MarketItemSnapshot>> success, Consumer<String> failure) {
        fetchObject("/latest", latest -> fetchObject("/1h", hourly -> fetchArray("/mapping", mapping -> {
            try {
                JsonObject latestData = latest.getAsJsonObject("data");
                JsonObject hourlyData = hourly.getAsJsonObject("data");
                List<MarketItemSnapshot> items = new ArrayList<>();
                mapping.forEach(element -> {
                    JsonObject metadata = element.getAsJsonObject();
                    int id = metadata.get("id").getAsInt();
                    JsonObject quote = latestData.getAsJsonObject(Integer.toString(id));
                    JsonObject hour = hourlyData.getAsJsonObject(Integer.toString(id));
                    if (quote == null || hour == null || absent(quote, "high") || absent(quote, "low")
                        || absent(quote, "highTime") || absent(quote, "lowTime")
                        || absent(hour, "avgHighPrice") || absent(hour, "avgLowPrice")) return;
                    items.add(new MarketItemSnapshot(id, metadata.get("name").getAsString(),
                        metadata.get("members").getAsBoolean(),
                        absent(metadata, "limit") ? 0 : metadata.get("limit").getAsInt(),
                        quote.get("high").getAsLong(), quote.get("low").getAsLong(),
                        Instant.ofEpochSecond(quote.get("highTime").getAsLong()),
                        Instant.ofEpochSecond(quote.get("lowTime").getAsLong()),
                        hour.get("avgHighPrice").getAsLong(), hour.get("avgLowPrice").getAsLong(),
                        absent(hour, "highPriceVolume") ? 0 : hour.get("highPriceVolume").getAsLong(),
                        absent(hour, "lowPriceVolume") ? 0 : hour.get("lowPriceVolume").getAsLong()));
                });
                success.accept(items);
            } catch (RuntimeException exception) {
                failure.accept("Invalid bulk market response");
            }
        }, failure), failure), failure);
    }

    private void fetchObject(String path, Consumer<JsonObject> success, Consumer<String> failure) {
        request(path, response -> gson.fromJson(response.body().charStream(), JsonObject.class), success, failure);
    }

    private void fetchArray(String path, Consumer<com.google.gson.JsonArray> success, Consumer<String> failure) {
        request(path, response -> gson.fromJson(response.body().charStream(), com.google.gson.JsonArray.class), success, failure);
    }

    private <T> void request(String path, Parser<T> parser, Consumer<T> success, Consumer<String> failure) {
        Request request = new Request.Builder().url(BASE + path)
            .header("User-Agent", "Offer Scribe RuneLite plugin/0.2.0")
            .build();
        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException exception) { failure.accept("Market request failed"); }
            @Override public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        failure.accept("Market service returned " + response.code());
                        return;
                    }
                    success.accept(parser.parse(response));
                } catch (RuntimeException exception) {
                    failure.accept("Invalid market response");
                }
            }
        });
    }

    private static boolean absent(JsonObject object, String name) {
        return !object.has(name) || object.get(name).isJsonNull();
    }

    private interface Parser<T> { T parse(Response response); }
}
