# InterconnectionPlayer 互联玩家同步
**InterconnectionPlayer 互联玩家同步**是一个用于在多个不同服务端的Minecraft服务器间同步玩家数据的Mod/Plugin。  
在默认情况下同步玩家的背包，生命值，末影箱，经验值。
## 依赖
请安装前置依赖 [FoxCore](https://github.com/KenRouKoro/FoxMCProject) 和 [Interconnection](https://github.com/KenRouKoro/Interconnection)。
## 使用
推荐使用Web配置系统。

| 配置项        | 类型           | 默认值   | 说明     |
|------------|--------------|-------|---------------------|
| 总开关 | bool         | true  | 总开关，关闭后本mod/plugin的传输将不再进行     |
| 时间戳保存文件路径 | String         | config/InterconnectionPlayerTimestampData.json  | 时间戳保存文件路径，无特殊需求保持默认     |
| 节点白名单功能开关  | bool         | false | 是否启用节点白名单           |
| 节点黑名单功能开关  | bool         | true  | 节点白名单使用黑名单模式        |
| 节点白名单列表    | String Array |       | 节点白名单列表             |
| 玩家白名单功能开关  | bool         | false | 是否启用玩家白名单           |
| 玩家黑名单功能开关  | bool         | true  | 玩家白名单使用黑名单模式        |
| 玩家白名单列表    | String Array |       | 玩家白名单列表，使用的是UUID匹配  |
| 背包同步功能开关  | bool         | true  | 是否启用背包同步        |
| 生命值同步功能开关  | bool         | true  | 是否启用生命值同步        |
| 末影箱同步功能开关  | bool         | true  | 是否启用末影箱同步        |
| 经验同步功能开关  | bool         | true  | 是否启用经验同步        |