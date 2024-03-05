package com.foxapplication.mc.interconnectionplayer.common.util;

import com.foxapplication.embed.hutool.core.map.SafeConcurrentHashMap;
import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerTimestamp;
import lombok.Getter;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PlayerTimestampDataAggregator {
    @Getter
    private final SafeConcurrentHashMap<String, PlayerTimestamp> receivedDataPackets = new SafeConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> timeoutFuture;
    private final int totalPackets;
    private final long timeout;
    private final TimeUnit timeUnit;
    private final PlayerTimestampDataCallback onAllPacketsReceivedOrTimeout;

    public PlayerTimestampDataAggregator(int totalPackets, long timeout, TimeUnit timeUnit, PlayerTimestampDataCallback onAllPacketsReceivedOrTimeout) {
        this.totalPackets = totalPackets;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.onAllPacketsReceivedOrTimeout = onAllPacketsReceivedOrTimeout;
    }

    public PlayerTimestampDataAggregator(int totalPackets, PlayerTimestampDataCallback onAllPacketsReceivedOrTimeout){
        this(totalPackets,500,TimeUnit.MILLISECONDS,onAllPacketsReceivedOrTimeout);
    }

    public void startTimeoutCountdown() {
        timeoutFuture = scheduler.schedule(this::onTimeout, timeout, timeUnit);
    }
    public String getMaxPlayerTimestamp(String... filter){
        HashMap<String, PlayerTimestamp> map = new HashMap<>(receivedDataPackets);
        for(String f:filter){
            map.remove(f);
        }
        AtomicReference<String> re = new AtomicReference<>(null);
        AtomicReference<Long> max = new AtomicReference<>((long)-1);
        map.forEach((k,v)->{
            if (v.getTimestamp()==-1)return;
            if (v.getTimestamp() > max.get()){
                re.set(k);
                max.set(v.getTimestamp());
            }
        });
        return re.get();
    }


    public synchronized void receiveDataPacket(String packetId, PlayerTimestamp data) {
        if (receivedDataPackets.size() < totalPackets && !receivedDataPackets.containsKey(packetId)) {
            receivedDataPackets.put(packetId, data);
            if (receivedDataPackets.size() == totalPackets) {
                timeoutFuture.cancel(false);
                triggerCallback();
            }
        }
    }
    private void onTimeout() {
        triggerCallback();
    }

    private void triggerCallback() {
        scheduler.shutdownNow();
        onAllPacketsReceivedOrTimeout.handleData(this );
    }

    public interface PlayerTimestampDataCallback{
        void handleData(PlayerTimestampDataAggregator playerTimestampDataAggregator);
    }

}
