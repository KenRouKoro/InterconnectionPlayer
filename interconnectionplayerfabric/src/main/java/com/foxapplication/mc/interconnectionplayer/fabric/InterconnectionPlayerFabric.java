package com.foxapplication.mc.interconnectionplayer.fabric;

import com.foxapplication.embed.hutool.core.thread.ThreadUtil;
import com.foxapplication.embed.hutool.core.util.ReflectUtil;
import com.foxapplication.embed.hutool.core.util.StrUtil;
import com.foxapplication.embed.hutool.log.Log;
import com.foxapplication.embed.hutool.log.LogFactory;
import com.foxapplication.mc.interaction.base.service.ConnectManager;
import com.foxapplication.mc.interconnection.fabric.util.NBTSendUtil;
import com.foxapplication.mc.interconnectionplayer.common.InterconnectionPlayer;
import com.foxapplication.mc.interconnectionplayer.common.InterconnectionPlayerCommon;
import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerData;
import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerTimestampCache;
import com.foxapplication.mc.interconnectionplayer.common.config.InterconnectionPlayerConfig;
import com.mojang.datafixers.DataFixer;
import lombok.Getter;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.UUID;

public class InterconnectionPlayerFabric implements DedicatedServerModInitializer , InterconnectionPlayer {
    @Getter
    private MinecraftServer server = null;
    private static final Log log = LogFactory.get();

    private PlayerDataStorage dataStorage = null;
    private static InterconnectionPlayerConfig CONFIG;
    private File playerDir = null;
    private DataFixer fixerUpper = null;
    @Override
    public void onInitializeServer() {
        InterconnectionPlayerCommon.Init(this);
        CONFIG = InterconnectionPlayerCommon.getCONFIG();

        ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
            PlayerList playerList = minecraftServer.getPlayerList();
            server = minecraftServer;

            Field datastorageField;
            if(ReflectUtil.hasField(PlayerList.class, "field_14358")){
                datastorageField = ReflectUtil.getField(PlayerList.class, "field_14358");
            }else if (ReflectUtil.hasField(PlayerList.class, "playerIo")){
                datastorageField = ReflectUtil.getField(PlayerList.class, "playerIo");
            }else{
                RuntimeException e = new RuntimeException("无法获取PlayerList的PlayerDataStorage字段");
                log.error(e);
                throw e;
            }
            datastorageField.setAccessible(true);
            try {
                dataStorage = (PlayerDataStorage) datastorageField.get(playerList);
            } catch (IllegalAccessException e) {
                log.error("反射PlayerDataStorage异常",e);
                throw new RuntimeException(e);
            }

            Field playerDirField;
            if(ReflectUtil.hasField(PlayerDataStorage.class, "field_144")){
                playerDirField = ReflectUtil.getField(PlayerDataStorage.class, "field_144");
            }else if (ReflectUtil.hasField(PlayerDataStorage.class, "playerDir")){
                playerDirField = ReflectUtil.getField(PlayerDataStorage.class, "playerDir");
            }else{
                RuntimeException e = new RuntimeException("无法获取PlayerDataStorage的playerDir字段");
                log.error(e);
                throw e;
            }
            playerDirField.setAccessible(true);
            try {
                playerDir = (File) playerDirField.get(dataStorage);
            } catch (IllegalAccessException e) {
                log.error("反射playerDir异常",e);
                throw new RuntimeException(e);
            }

            Field fixerUpperField;
            if(ReflectUtil.hasField(PlayerDataStorage.class, "field_148")){
                fixerUpperField = ReflectUtil.getField(PlayerDataStorage.class, "field_148");
            }else if (ReflectUtil.hasField(PlayerDataStorage.class, "fixerUpper")){
                fixerUpperField = ReflectUtil.getField(PlayerDataStorage.class, "fixerUpper");
            }else{
                RuntimeException e = new RuntimeException("无法获取PlayerDataStorage的fixerUpper字段");
                log.error(e);
                throw e;
            }
            fixerUpperField.setAccessible(true);
            try {
                fixerUpper = (DataFixer) fixerUpperField.get(dataStorage);
            } catch (IllegalAccessException e) {
                log.error("反射fixerUpper异常",e);
                throw new RuntimeException(e);
            }

        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            InterconnectionPlayerCommon.onStop();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerTimestampCache.put(handler.getPlayer().getStringUUID(), System.currentTimeMillis());
            if(CONFIG.isPlayerWhitelist()){
                if (CONFIG.isPlayerWhitelistUseReverse()){
                    if (CONFIG.getPlayerWhitelistList().contains(handler.getPlayer().getStringUUID()))return;
                }else{
                    if (!CONFIG.getPlayerWhitelistList().contains(handler.getPlayer().getStringUUID()))return;
                }
            }
            String uuid = handler.getPlayer().getStringUUID();
            handler.getPlayer().sendSystemMessage(Component.literal("正在与尝试同步数据..."));
            InterconnectionPlayerCommon.sendTimestampRequest(uuid, (playerTimestampDataAggregator) -> {
                String node;
                String [] filter = new String[0];
                if (CONFIG.isNodeWhitelist()){
                    if (CONFIG.isNodeWhitelistUseReverse()){
                        filter = CONFIG.getNodeWhitelistList().toArray(new String[0]);
                    }else{
                        HashSet<String>nodes = new HashSet<>();
                        ConnectManager.getConnectMap().forEach((k,v)->{
                            if (!CONFIG.getNodeWhitelistList().contains(k)){
                                nodes.add(k);
                            }
                        });
                        filter = nodes.toArray(new String[0]);
                    }
                }
                node = playerTimestampDataAggregator.getMaxPlayerTimestamp(filter);
                if (node==null)return;
                ThreadUtil.execute(()->{
                    ThreadUtil.safeSleep(200);
                    InterconnectionPlayerCommon.sendDataRequest(node, uuid, (playerData) -> {
                        if (playerData.getData().length<=1){
                            return;
                        }
                        if (server==null){
                            return;
                        }
                        ThreadUtil.execute(()->{
                            Tag data;
                            try {
                                data = NBTSendUtil.parseNBT(playerData.getData());
                            } catch (IOException e) {
                                log.error("解析NBT异常",e);
                                return;
                            }
                            CompoundTag compoundTag;
                            if (data.getType()==CompoundTag.TYPE){
                                compoundTag = (CompoundTag)data;
                            }else{
                                log.error("转换NBT异常，不是目标类型");
                                return;
                            }
                            server.execute(()->{
                                save(node, handler.getPlayer(), compoundTag);
                            });
                        });
                    });
                });
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerTimestampCache.put(handler.getPlayer().getStringUUID(), System.currentTimeMillis());
        });

    }

    @Nullable
    public CompoundTag load(String uuid) {
        CompoundTag compoundTag = null;
        try {
            File file = new File(this.playerDir, uuid + ".dat");
            if (file.exists() && file.isFile()) {
                compoundTag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            }
        } catch (Exception exception) {
            log.warn("Failed to load player data for {}", uuid);
        }
        if (compoundTag != null) {
            int i = NbtUtils.getDataVersion(compoundTag, -1);
            compoundTag = DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, compoundTag, i);

            CompoundTag compoundTag2 = new CompoundTag();

            compoundTag2.put("Inventory", compoundTag.getList("Inventory", 10));
            compoundTag2.putInt("SelectedItemSlot",compoundTag.getInt("SelectedItemSlot"));

            compoundTag2.putFloat("XpP",compoundTag.getFloat("XpP"));
            compoundTag2.putInt("XpLevel",compoundTag.getInt("XpLevel"));
            compoundTag2.putInt("XpTotal",compoundTag.getInt("XpTotal"));

            if (compoundTag.contains("Health", 99)) {
                compoundTag2.putFloat("Health",compoundTag.getFloat("Health"));
            }

            if(compoundTag.contains("EnderItems", 9)){
                compoundTag2.put("EnderItems",compoundTag.getList("EnderItems", 10));
            }

            compoundTag = compoundTag2;
        }
        return compoundTag;
    }

    public void save(String node,Player player, CompoundTag compoundTag) {
        if (server==null)return;

        int i = NbtUtils.getDataVersion(compoundTag, -1);
        compoundTag = DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, compoundTag, i);

        if(CONFIG.isEnableBackpack()){
            ListTag listTag = compoundTag.getList("Inventory", 10);
            player.getInventory().load(listTag);

            player.getInventory().selected = compoundTag.getInt("SelectedItemSlot");
        }

        if (CONFIG.isEnableXp()){
            player.experienceProgress = compoundTag.getFloat("XpP");
            player.experienceLevel = compoundTag.getInt("XpLevel");
            player.totalExperience = compoundTag.getInt("XpTotal");
        }

        if (CONFIG.isEnableHealth()) {
            if (compoundTag.contains("Health", 99)) {
                player.setHealth(compoundTag.getFloat("Health"));
            }
        }

        if (CONFIG.isEnableEnderItems()){
            if(compoundTag.contains("EnderItems", 9)){
                player.getEnderChestInventory().fromTag(compoundTag.getList("EnderItems", 10));
            }
        }


        player.sendSystemMessage(Component.literal(StrUtil.format("已接收来自{}节点的数据",node)));

    }

    @Override
    public String[] getPlayerList() {
        if (dataStorage !=null){
            return dataStorage.getSeenPlayers();
        }
        return new String[0];
    }

    @Override
    public byte[] getData(String s) {

        CompoundTag compoundTag = load(s);

        if (compoundTag == null){
            return new byte[0];
        }
        return NBTSendUtil.NBT2BytesAny(compoundTag);
    }

}
