# frp内网穿透 + PROXY协议v2 配置说明 (重要!)

## 网络拓扑

玩家 → 公网(116.62.236.141) → frps服务端 → frpc客户端(本机 /opt/frp/123.toml) → 本地Paper服务器

- **Java**: `116.62.236.141:25565` (TCP隧道 + PROXY v2)
- **基岩**: `116.62.236.141:25565` (UDP隧道 + PROXY v2)
- **语音**: 24454 (UDP隧道 + PROXY v2)

## 三方配置必须一致(缺一不可!)

| 位置 | 配置项 | 值 | 说明 |
|------|--------|-----|------|
| `/opt/frp/123.toml` | `transport.proxyProtocolVersion` | `"v2"` | frp隧道发送PROXY v2头 |
| `config/paper-global.yml` | `proxies.proxy-protocol` | `true` | Paper接收PROXY头(所有连接!) |
| `plugins/Geyser-Spigot/config.yml` | `java.use-haproxy-protocol` | `true` | Geyser后端连接Paper时携带PROXY头 |
| `plugins/Geyser-Spigot/config.yml` | `bedrock.use-haproxy-protocol` | `true` | 接收基岩UDP隧道的PROXY头 |
| `plugins/Geyser-Spigot/config.yml` | `haproxy-protocol-whitelisted-ips` | `["127.0.0.1","127.0.0.0/8"]` | 允许frpc(本机)来源 |

## 常见故障

1. **基岩版"数据流终止"**: Geyser的`java.use-haproxy-protocol`或`bedrock.use-haproxy-protocol`没开
2. **Java进不去/一直加载**: Paper的`proxy-protocol`没开
3. **所有直连(含本地测试)失败**: 这是正常的! 因为Paper要求PROXY头。
   本地测试需要先发送PROXY v2头:
   ```python
   sig = b'\x0d\x0a\x0d\x0a\x00\x0d\x0a\x51\x55\x49\x54\x0a'
   # ... 具体见 /tmp/proxytest.py
   ```

## 注意

- 开启`proxy-protocol: true`后,**任何不带PROXY头的直连都会被挂起**,包括局域网直连和本地测试工具
- 修改任一项后需重启对应服务(frp隧道需重启frpc)
