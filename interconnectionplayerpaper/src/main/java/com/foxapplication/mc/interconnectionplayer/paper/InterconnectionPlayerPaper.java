package com.foxapplication.mc.interconnectionplayer.paper;

import com.foxapplication.embed.hutool.core.thread.ThreadUtil;
import com.foxapplication.embed.hutool.core.util.ArrayUtil;
import com.foxapplication.embed.hutool.core.util.ReflectUtil;
import com.foxapplication.embed.hutool.core.util.StrUtil;
import com.foxapplication.embed.hutool.log.Log;
import com.foxapplication.embed.hutool.log.LogFactory;
import com.foxapplication.mc.interaction.base.service.ConnectManager;
import com.foxapplication.mc.interconnection.paper.util.NBTSendUtil;
import com.foxapplication.mc.interconnectionplayer.common.InterconnectionPlayer;
import com.foxapplication.mc.interconnectionplayer.common.InterconnectionPlayerCommon;
import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerTimestampCache;
import com.foxapplication.mc.interconnectionplayer.common.config.InterconnectionPlayerConfig;
import com.mojang.datafixers.DataFixer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

public final class InterconnectionPlayerPaper extends JavaPlugin implements @NotNull Listener , InterconnectionPlayer {

    private static  Log log;
    private static MinecraftServer server = null;
    private static CraftServer craftServer = null;

    private PlayerDataStorage dataStorage = null;
    private static InterconnectionPlayerConfig CONFIG;
    private File playerDir = null;
    private DataFixer fixerUpper = null;


    @Override
    public void onEnable() {
        log = LogFactory.get();
        getServer().getPluginManager().registerEvents(this, this);
        // Plugin startup logic
        InterconnectionPlayerCommon.Init(this);
        CONFIG = InterconnectionPlayerCommon.getCONFIG();

    }

    @Override
    public @NotNull ComponentLogger getComponentLogger() {
        return super.getComponentLogger();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        InterconnectionPlayerCommon.onStop();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDisConnect(PlayerQuitEvent event){
        PlayerTimestampCache.put(event.getPlayer().getUniqueId().toString(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConnect(PlayerJoinEvent event){
        if (event.getPlayer() instanceof CraftPlayer player){
            PlayerTimestampCache.put(player.getHandle().getStringUUID(), System.currentTimeMillis());
            if(CONFIG.isPlayerWhitelist()){
                if (CONFIG.isPlayerWhitelistUseReverse()){
                    if (CONFIG.getPlayerWhitelistList().contains(player.getHandle().getStringUUID()))return;
                }else{
                    if (!CONFIG.getPlayerWhitelistList().contains(player.getHandle().getStringUUID()))return;
                }
            }
            String uuid = player.getHandle().getStringUUID();
            player.getHandle().sendSystemMessage(Component.literal("正在与尝试同步数据..."));
            InterconnectionPlayerCommon.sendTimestampRequest(uuid, (playerTimestampDataAggregator) -> {
                String node;
                String [] filter = new String[0];
                if (CONFIG.isNodeWhitelist()){
                    if (CONFIG.isNodeWhitelistUseReverse()){
                        filter = CONFIG.getNodeWhitelistList().toArray(new String[0]);
                    }else{
                        HashSet<String> nodes = new HashSet<>();
                        ConnectManager.getConnectMap().forEach((k, v)->{
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
                                save(node, player.getHandle(), compoundTag);
                            });
                        });
                    });
                });
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerStart(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.RELOAD) return;

        if (getServer() instanceof CraftServer craftServer){
            InterconnectionPlayerPaper.craftServer = craftServer;
            server = craftServer.getServer();
        }else {
            log.error("服务器实例获取失败。");
            return;
        }

        PlayerList playerList = server.getPlayerList();

        Field datastorageField;
        if(ReflectUtil.hasField(PlayerList.class, "t")){
            datastorageField = ReflectUtil.getField(PlayerList.class, "t");
        }else if (ReflectUtil.hasField(PlayerList.class, "playerIo")){
            datastorageField = ReflectUtil.getField(PlayerList.class, "playerIo");
        }else{
            RuntimeException e = new RuntimeException("无法获取PlayerList的PlayerDataStorage字段");
            log.error("PlayerList Fields：{}", Arrays.asList(ReflectUtil.getFields(PlayerList.class)));
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
        if(ReflectUtil.hasField(PlayerDataStorage.class, "c")){
            playerDirField = ReflectUtil.getField(PlayerDataStorage.class, "c");
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
        if(ReflectUtil.hasField(PlayerDataStorage.class, "a")){
            fixerUpperField = ReflectUtil.getField(PlayerDataStorage.class, "a");
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
