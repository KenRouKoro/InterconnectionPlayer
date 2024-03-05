package com.foxapplication.mc.interconnectionplayer.common.config;

import com.foxapplication.mc.core.config.interfaces.FieldAnnotation;
import com.foxapplication.mc.core.config.interfaces.FileType;
import com.foxapplication.mc.core.config.interfaces.FileTypeInterface;
import lombok.Data;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@FileTypeInterface(type = FileType.TOML)
public class InterconnectionPlayerConfig {
    @FieldAnnotation(value = "总开关，关闭后本mod/plugin的传输将不再进行",name = "总开关")
    boolean enable = true;
    @FieldAnnotation(value = "时间戳保存文件路径，无特殊需求保持默认",name = "时间戳保存文件路径")
    String saveFile = "config/InterconnectionPlayerTimestampData.json";
    @FieldAnnotation(value = "是否启用背包同步",name = "背包同步功能开关")
    boolean enableBackpack = true;
    @FieldAnnotation(value = "是否启用生命值同步",name = "生命值同步功能开关")
    boolean enableHealth = true;
    @FieldAnnotation(value = "是否启用末影箱同步",name = "末影箱同步功能开关")
    boolean enableEnderItems = true;
    @FieldAnnotation(value = "是否启用经验同步",name = "经验同步功能开关")
    boolean enableXp = true;
    @FieldAnnotation(value = "是否启用节点白名单",name = "节点白名单功能开关")
    boolean nodeWhitelist = false;
    @FieldAnnotation(value = "节点白名单使用黑名单模式",name = "节点黑名单功能开关")
    boolean nodeWhitelistUseReverse = true;
    @FieldAnnotation(value = "节点白名单列表",name = "节点白名单列表")
    List<String> nodeWhitelistList = new CopyOnWriteArrayList<String>();
    @FieldAnnotation(value = "是否启用玩家白名单",name = "玩家白名单功能开关")
    boolean playerWhitelist = false;
    @FieldAnnotation(value = "玩家白名单使用黑名单模式",name = "玩家黑名单功能开关")
    boolean playerWhitelistUseReverse = true;
    @FieldAnnotation(value = "玩家白名单列表，使用的是***UUID***匹配",name = "玩家白名单列表")
    List<String> playerWhitelistList = new CopyOnWriteArrayList<String>();
}
