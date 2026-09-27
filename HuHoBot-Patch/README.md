# HuHoBot-Patch (企鹅QQ机器人补丁)

对 `HuHoBotPenguin-1.14.0.jar` 的补丁源码。官方发行版存在以下问题:

1. **MC→QQ 消息不同步**: 原版监听已废弃的 `AsyncPlayerChatEvent`,
   Paper 26.2 不再触发该事件 → 改为监听现代 `AsyncChatEvent`(GameChat.java)
2. **QQ平台主动消息限制**: 转发时无返回码检查, 平台拒绝时静默失败。
   补丁附带最近群消息的 `msg_id` 作为"被动回复", 并记录失败错误码(GameChat.java + MsgIdCapture.java)
3. **颜色字符串泄漏**: QQ群收到 `§` 颜色代码 → 过滤 `§[0-9a-fk-orxA-FK-ORX#]` 与 ANSI 转义
   (CommandOutputAppender.java / BukkitConsoleSender.java)

## 应用补丁

```bash
# 编译(需要官方 HuHoBot jar + paper-api 作为 classpath)
javac --release 21 -encoding UTF-8 -cp "HuHoBotPenguin-1.14.0.jar:paper-api-26.2.jar:<adventure等库>" \
      -d out src/cn/huohuas001/huhobotPenguin/spigot/**/*.java

# 写入jar
jar uf HuHoBotPenguin-1.14.0.jar \
  cn/huohuas001/huhobotPenguin/spigot/events/GameChat.class \
  cn/huohuas001/huhobotPenguin/spigot/events/MsgIdCapture.class \
  'cn/huohuas001/huhobotPenguin/spigot/events/MsgIdCapture$Cached.class' \
  cn/huohuas001/huhobotPenguin/spigot/commands/CommandOutputAppender.class \
  'cn/huohuas001/huhobotPenguin/spigot/commands/CommandOutputAppender$Companion.class' \
  cn/huohuas001/huhobotPenguin/spigot/commands/BukkitConsoleSender.class \
  'cn/huohuas001/huhobotPenguin/spigot/commands/BukkitConsoleSender$1.class'
```
