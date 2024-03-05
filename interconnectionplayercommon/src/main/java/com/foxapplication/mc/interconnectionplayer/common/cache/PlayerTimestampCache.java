package com.foxapplication.mc.interconnectionplayer.common.cache;

import com.foxapplication.embed.hutool.core.io.FileUtil;
import com.foxapplication.embed.hutool.core.io.file.FileReader;
import com.foxapplication.embed.hutool.core.io.file.FileWriter;
import com.foxapplication.embed.hutool.core.lang.Console;
import com.foxapplication.embed.hutool.core.map.SafeConcurrentHashMap;
import com.foxapplication.embed.hutool.core.util.ArrayUtil;
import com.foxapplication.embed.hutool.core.util.CharsetUtil;
import com.foxapplication.embed.hutool.json.JSONObject;
import com.foxapplication.embed.hutool.json.JSONUtil;
import com.foxapplication.mc.interconnectionplayer.common.InterconnectionPlayerCommon;

import java.io.File;

public class PlayerTimestampCache {

    private static SafeConcurrentHashMap<String, Long> playerTimestampMap = new SafeConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PlayerTimestampCache::save));
    }

    public static void load(){
        JSONObject jsonObject;
        String file = InterconnectionPlayerCommon.getCONFIG().getSaveFile().replaceAll("\\\\", "/");
        InterconnectionPlayerCommon.getLog().info(file);
        if (!FileUtil.isEmpty(new File(file))){
            jsonObject = JSONUtil.readJSONObject(new File(file), CharsetUtil.CHARSET_UTF_8);
        }else {
            FileUtil.touch(new File(file));
            save();
            return;
        }

        SafeConcurrentHashMap<String, Long> map = new SafeConcurrentHashMap<>();
        jsonObject.keySet().forEach(key -> {
            map.put(key, jsonObject.getLong(key));
        });
        SafeConcurrentHashMap<String, Long> deleteMap = playerTimestampMap;
        playerTimestampMap = map;
        deleteMap.clear();

    }
    public static synchronized void save(){
        JSONObject jsonObject = JSONUtil.parseObj(playerTimestampMap);
        FileWriter fileWriter = new FileWriter(new File(InterconnectionPlayerCommon.getCONFIG().getSaveFile().replaceAll("\\\\", "/")), CharsetUtil.CHARSET_UTF_8);
        fileWriter.write(jsonObject.toString());
    }

    public static void put(String uuid, long timestamp){
        playerTimestampMap.put(uuid, timestamp);
    }
    public static long get(String uuid){
        if (!ArrayUtil.contains(InterconnectionPlayerCommon.getInterconnectionPlayer().getPlayerList(), uuid)){
            return -1;
        }
        Long re = playerTimestampMap.get(uuid);
        if (re == null){
            return -1;
        }
        return re;
    }

    public static void clear(){
        playerTimestampMap.clear();
    }

    public static void remove(String uuid){
        playerTimestampMap.remove(uuid);
    }


}
