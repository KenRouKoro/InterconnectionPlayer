package com.foxapplication.mc.interconnectionplayer.common;

import com.foxapplication.mc.interconnectionplayer.common.cache.PlayerData;

public interface InterconnectionPlayer {
    String[] getPlayerList();
    byte[] getData(String uuid);
}
