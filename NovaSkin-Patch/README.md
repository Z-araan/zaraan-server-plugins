# NovaSkin-Patch (皮肤插件补丁)

对 `NovaSkin-1.0.0.jar` 的补丁源码。

## 补丁内容

**解除离线模式下的披风正版限制**:
原版 `SkinCommand.isPremium()` 通过 Mojang API 校验玩家 UUID,
但离线模式(online-mode=false)服务器的 UUID 与正版 UUID 不一致,
导致**所有玩家都无法使用披风**。补丁改为直接放行:

```java
private CompletableFuture<Boolean> isPremium(...) {
    // [zaraan补丁] 离线模式服务器无法通过Mojang UUID校验, 直接放行披风功能
    return CompletableFuture.completedFuture(Boolean.TRUE);
}
```

## 应用补丁

```bash
javac --release 21 -encoding UTF-8 \
  -cp "NovaSkin-1.0.0.jar:paper-api-26.2.jar:<adventure等库>" \
  -d out src/dev/novaskin/command/SkinCommand.java
jar uf NovaSkin-1.0.0.jar dev/novaskin/command/SkinCommand.class
```

## 其他修复(服务器配置层面)

- 皮肤/披风购买改由**玩家身份执行自设语法**(`skin set <皮肤>`),
  避免控制台执行 `/skin set <玩家> <皮肤>` 时的 Paper 命令上下文报错
- LuckPerms 默认组解除了 `novaskin.use`/`novaskin.cape` 的拒绝
