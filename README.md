# zaraan星火之域 服务器插件

Paper 26.2 Minecraft 服务器「zaraan星火之域」的自研插件与补丁源码。

## 目录结构

| 目录 | 说明 |
|------|------|
| `ZaraanCore/` | 核心整合插件: 菜单物品/经济/传送/组队/私聊/进服体验/自动公告/聊天称号 |
| `NovaSkin-Patch/` | NovaSkin 皮肤插件补丁: 解除离线模式披风限制 |
| `HuHoBot-Patch/` | HuHoBot 企鹅 QQ 机器人补丁: 修复 MC↔QQ 消息同步、颜色过滤 |
| `DeluxeMenus-Menus/` | 服务器菜单配置(主菜单/商店/传送/个人中心/玩法/杂项等8个菜单) |

## 功能一览

### ZaraanCore
- **菜单物品**: 下界之星右键打开主菜单(兼容旧物品)
- **传送菜单**: `/mtpa` 玩家列表点击传送、`/mhome` 家管理、`/mpay` 转账、`/mmsg` 私聊
- **随机传送**: `/rtp` 800-5000格安全落点, 3秒预热, 180秒冷却
- **组队系统**: `/team` 创建/邀请/踢人/解散, `/tc` 队伍聊天
- **私聊**: `/mmsg` 选人 → 聊天栏输入 → 仅对方可见
- **进服体验**: 全屏标题 + 音效 + 首次进服礼包/烟花/广播 + 上次登录提示
- **自动公告**: 每5分钟轮播
- **聊天称号**: 在聊天栏玩家名前显示佩戴称号(渲染器方式, 不受旧版事件限制)
- **死亡保护**: 死亡花金币保住物品
- **经济**: 皮肤/披风购买, 自定义皮肤链接
- **玩家交易市场**: `/market` 菜单化买卖玩家物品(上架/浏览/购买/撤回)
- **私人传送点**: `/mwarp` 菜单管理 PlayerWarps 传送点
- **组队GUI**: `/team` 图形化组队(创建/邀请/成员管理/队聊)
- **出生点保护**: 防破坏/防伤害/阻止怪物生成(半径可配)
- **AI助手**(合并自ZaraanAI): `/ai <问题>` 调用云端API(DeepSeek)
- **PAPI变量**: `%zaraancore_balance%`

> AI 功能已合并进 ZaraanCore (v2.2+), 在 `ZaraanCore/config.yml` 的 `ai` 段填入 `api-key` 并设 `enabled: true`

### HuHoBot-Patch
- `GameChat`/`MsgIdCapture`: 改用 Paper 现代 `AsyncChatEvent`, 修复 MC→QQ 转发;
  支持"被动回复"(附带最近群消息 msg_id, 绕过平台主动消息限制); 转发失败记录错误码
- `CommandOutputAppender`/`BukkitConsoleSender`: 过滤 `§` 颜色符号与 ANSI 转义,
  修复QQ群收到颜色字符串的问题

## 构建

见 `build.sh`。依赖 Paper API 26.2 与服务端 `libraries/` 下的库。

```bash
./build.sh
```

产物: `ZaraanCore/build/ZaraanCore-x.y.z.jar`, `ZaraanAI/build/ZaraanAI-1.0.0.jar`

HuHoBot 补丁通过 `jar uf` 写入官方 HuHoBot 发行版 jar(见 `HuHoBot-Patch/README.md`)。

## 部署

将构建产物放入服务器 `plugins/` 目录, 重启服务器。

## 环境

- Paper 26.2-129
- Java 21+
- 依赖: Vault, PlaceholderAPI, EssentialsX, DeluxeMenus(菜单), PlayerTitle(称号)
