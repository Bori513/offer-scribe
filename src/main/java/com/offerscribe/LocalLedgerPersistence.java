package com.offerscribe;

import com.google.gson.Gson;
import com.offerscribe.model.TradeEvent;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;

/** Atomic local event persistence. Disk work always runs off the client thread. */
@Singleton
final class LocalLedgerPersistence {
    private final Gson gson; private final OkHttpClient http; private final Filepath file; private long version;
    @Inject LocalLedgerPersistence(Gson gson, OkHttpClient http, Filepath dataDirectory) {
        this.gson=gson;this.http=http;this.file=dataDirectory.join("trade-events.json");
    }
    void load(Consumer<List<TradeEvent>> success) { http.dispatcher().executorService().execute(() -> {
        List<TradeEvent> result=Collections.emptyList();
        if(file.exists())try{TradeEvent[] values=gson.fromJson(file.openReader(),TradeEvent[].class);if(values!=null)result=Arrays.asList(values);}catch(IOException|RuntimeException ignored){}
        success.accept(result);
    }); }
    synchronized void save(List<TradeEvent> events) { List<TradeEvent> snapshot=new ArrayList<>(events);long current=++version;
        http.dispatcher().executorService().execute(() -> write(snapshot,current)); }
    private void write(List<TradeEvent> events,long current){Filepath parent=file.getParent(),temporary=parent.join("trade-events-"+current+".tmp");try{
        parent.createDirectories();temporary.write(gson.toJson(events),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
        synchronized(this){if(current!=version){temporary.deleteIfExists();return;}try{temporary.moveTo(file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ignored){temporary.moveTo(file,StandardCopyOption.REPLACE_EXISTING);}}
    }catch(IOException ignored){}}
}
