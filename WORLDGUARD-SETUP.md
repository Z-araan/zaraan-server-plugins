# WorldGuard 出生点保护配置 (替代 ZaraanCore 内置实现)

## 区域: spawn (world世界)

坐标范围: (-48,-64,-48) → (48,320,48)，以世界出生点(0,74,0)为中心

标志:
| 标志 | 值 | 作用 |
|------|-----|------|
| build | deny | 禁止破坏/放置方块 |
| pvp | deny | 禁止玩家互相攻击 |
| invincible | allow | 玩家免疫一切伤害 |
| mob-spawning | deny | 阻止怪物生成 |
| creeper-explosion | deny | 阻止苦力怕爆炸破坏 |
| tnt | deny | 禁止TNT |
| other-explosion | deny | 阻止其他爆炸 |
| lighter | deny | 禁止打火石 |

## 管理命令

```bash
/rg info -w world spawn          # 查看区域信息
/rg flag -w world spawn <标志> <值>   # 修改标志
/rg addmember -w world spawn <玩家>   # 添加区域成员(可建筑)
/rg removemember -w world spawn <玩家>
```

管理员(OP)默认可通过 `worldguard.region.bypass.world` 权限绕过保护。

## 重新创建区域(如需调整大小)

```bash
# 在游戏内用WorldEdit选中区域:
//pos1 -48,-64,-48
//pos2 48,320,48
/rg define spawn
/rg flag spawn build deny
/rg flag spawn pvp deny
/rg flag spawn invincible allow
/rg flag spawn mob-spawning deny
```

## 备注

- 原 ZaraanCore 内置出生点保护已于 v2.2.1 移除，改用本 WorldGuard 区域
- 出生点坐标可从 `world/level.dat` 的 `Data.spawn.pos` 读取
