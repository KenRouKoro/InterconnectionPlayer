package com.foxapplication.mc.interconnectionplayer.common;


import com.foxapplication.embed.hutool.cache.CacheUtil;
import com.foxapplication.embed.hutool.cache.impl.TimedCache;
import com.foxapplication.embed.hutool.core.io.FastByteArrayOutputStream;
import com.foxapplication.embed.hutool.core.thread.ThreadUtil;
import com.foxapplication.embed.hutool.log.Log;
import com.foxapplication.embed.hutool.log.LogFactory;
import com.foxapplication.mc.core.config.LocalFoxConfig;
import com.foxapplication.mc.core.config.webconfig.WebConfig;
import com.foxapplication.mc.interaction.base.service.ConnectManager;
import com.foxapplication.mc.interaction.base.service.MessageManager;
import com.foxapplication.mc.interconnection.common.util.MessageUtil;
import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerData;
import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerTimestamp;
import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerTimestampCache;
import com.foxapplication.mc.interconnectionplayer.common.config.InterconnectionPlayerConfig;
import com.foxapplication.mc.interconnectionplayer.common.util.KryoUtil;
import com.foxapplication.mc.interconnectionplayer.common.util.PlayerTimestampDataAggregator;
import lombok.Getter;
import lombok.Setter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class InterconnectionPlayerCommon {
    @Getter
    private static final Log log = LogFactory.get();
    @Getter
    private static InterconnectionPlayerConfig CONFIG;
    @Getter
    private static LocalFoxConfig localFoxConfig;
    @Getter
    private static InterconnectionPlayer interconnectionPlayer = null;
    @Getter
    private static final KryoUtil<PlayerTimestamp> kryoUtil = new KryoUtil<>(PlayerTimestamp.class);
    private static final KryoUtil<PlayerData> playerKryoUtil = new KryoUtil<>(PlayerData.class);

    private static final TimedCache<String, PlayerDataCallback> dataTimedCache = CacheUtil.newTimedCache(500);
    private static final TimedCache<String, PlayerTimestampDataAggregator> timestampTimedCache = CacheUtil.newTimedCache(600);


    public static void Init(InterconnectionPlayer interconnectionPlayer){
        InterconnectionPlayerCommon.interconnectionPlayer = interconnectionPlayer;
        localFoxConfig = new LocalFoxConfig(InterconnectionPlayerConfig.class);
        CONFIG = (InterconnectionPlayerConfig) localFoxConfig.getBeanFoxConfig().getBean();
        WebConfig.addConfig(localFoxConfig.getBeanFoxConfig());
        PlayerTimestampCache.load();
        dataTimedCache.schedulePrune(500);
        timestampTimedCache.schedulePrune(600);

        MessageUtil.addListener("get_InterconnectionPlayer_Timestamp", (data) -> {
            String uuid = data.getMessageByString();
            ThreadUtil.execute(() -> {
                PlayerTimestamp playerTimestamp = new PlayerTimestamp(uuid, PlayerTimestampCache.get(uuid));
                MessageUtil.send(data.getForm(),"InterconnectionPlayer_Timestamp", kryoUtil.serialize(playerTimestamp));
            });
        });

        MessageUtil.addListener("InterconnectionPlayer_Timestamp", (data) -> {
            PlayerTimestamp playerTimestamp = kryoUtil.deserialize(data.getMessage());
            PlayerTimestampDataAggregator playerTimestampDataAggregator = timestampTimedCache.get(playerTimestamp.getUuid(),false);
            if (playerTimestampDataAggregator!=null){
                playerTimestampDataAggregator.receiveDataPacket(data.getForm(), playerTimestamp);
            }
        });

        MessageUtil.addListener("get_InterconnectionPlayer_Data", (data) -> {
            String uuid = data.getMessageByString();
            log.info("收到玩家数据请求，玩家UUID为：{}",uuid);
            ThreadUtil.execute(() -> {
                MessageUtil.send(data.getForm(),"InterconnectionPlayer_Data", playerKryoUtil.serialize(new PlayerData(uuid,interconnectionPlayer.getData(uuid))));
            });
        });
        MessageUtil.addListener("InterconnectionPlayer_Data" , (data)->{
            ThreadUtil.execute(() -> {
                PlayerData playerData = playerKryoUtil.deserialize(data.getMessage());
                PlayerDataCallback callback = dataTimedCache.get(playerData.getUUID(),false);
                if (callback==null)return;
                callback.handleData(playerData);
            });
        });

    }

    public static void sendTimestampRequest(String uuid,PlayerTimestampDataAggregator.PlayerTimestampDataCallback callback){
        PlayerTimestampDataAggregator aggregator = new PlayerTimestampDataAggregator(ConnectManager.getConnectMap().size(),callback);
        timestampTimedCache.put(uuid,aggregator);
        MessageUtil.send(MessageManager.ALL,"get_InterconnectionPlayer_Timestamp",uuid);
        aggregator.startTimeoutCountdown();
    }

    public static void sendDataRequest(String target,String uuid,PlayerDataCallback callback){
        dataTimedCache.put(uuid,callback);
        MessageUtil.send(target,"get_InterconnectionPlayer_Data",uuid);
    }

    public static void onStop(){
        PlayerTimestampCache.save();
        dataTimedCache.cancelPruneSchedule();
        timestampTimedCache.cancelPruneSchedule();
    }

    public interface PlayerDataCallback {
        void handleData(PlayerData playerData);
    }

}
