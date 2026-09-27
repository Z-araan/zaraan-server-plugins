package cn.huohuas001.huhobotPenguin.spigot.events;

import cn.huohuas001.bot.HuHoBot;
import cn.huohuas001.bot.QClient;
import cn.huohuas001.bot.provider.BotShared;
import cn.huohuas001.bot.provider.ChatFormat;
import com.alibaba.fastjson.JSON;
import io.github.kloping.qqbot.Starter;
import io.github.kloping.qqbot.entities.ex.Markdown;
import io.github.kloping.qqbot.entities.qqpd.Channel;
import io.github.kloping.qqbot.http.data.V2MsgData;
import io.github.kloping.qqbot.http.data.V2Result;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 兼容补丁 (zaraan 2026-09-27):
 * 1. 原版监听已废弃的 AsyncPlayerChatEvent, Paper 26.2 不再触发 → 改监听现代 AsyncChatEvent
 * 2. 自己实现转发, 可记录平台返回错误码, 便于排查
 * 3. 若群里最近5分钟有消息, 转发时附带 msg_id 作为"被动回复"(平台一定允许)
 */
public final class GameChat implements Listener {

    /** 被动回复有效期(5分钟) */
    private static final long MSG_ID_TTL_MILLIS = 5 * 60 * 1000L;

    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zaraan-mc2qq");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean captureRegistered = false;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        final String name = event.getPlayer().getName();
        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        ensureCapture();
        SENDER.execute(() -> {
            try {
                forwardGameMessage(name, message);
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[HuHoBot] 游戏消息转发QQ异常: " + t);
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            QClient.INSTANCE.broadcastPlayerJoin(event.getPlayer().getName());
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        try {
            QClient.INSTANCE.broadcastPlayerQuit(event.getPlayer().getName());
        } catch (Throwable ignored) {
        }
    }

    /** 注册消息ID捕获器(懒加载, 只需一次) */
    private void ensureCapture() {
        if (captureRegistered) return;
        Starter starter = QClient.INSTANCE.getStarter();
        if (starter == null) return;
        synchronized (GameChat.class) {
            if (captureRegistered) return;
            try {
                starter.registerListenerHost(new MsgIdCapture());
                captureRegistered = true;
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[HuHoBot] 注册消息ID捕获器失败: " + t);
            }
        }
    }

    private void forwardGameMessage(String playerName, String message) {
        HuHoBot plugin = BotShared.INSTANCE.getPlugin();
        if (plugin == null) return;
        ChatFormat format = plugin.getChatFormat();
        if (format == null || !format.getPostChat()) return;
        String startWith = format.getStartWith() == null ? "" : format.getStartWith();
        if (!message.startsWith(startWith)) return;
        String withoutPrefix = message.substring(startWith.length());
        String filtered = plugin.auditText(withoutPrefix);
        if (filtered == null) filtered = withoutPrefix;
        String content = plugin.applyPlaceholders(playerName, plugin.formatGameMessage(playerName, filtered));
        Markdown markdown = new Markdown().setContent(content);
        Starter starter = QClient.INSTANCE.getStarter();
        if (starter == null) return;
        for (String groupId : plugin.getGroupOpenIdList()) {
            V2MsgData payload = new V2MsgData().setContent(content).setMsg_type(Integer.valueOf(2)).setMarkdown(markdown);
            boolean passive = false;
            MsgIdCapture.Cached cached = MsgIdCapture.get(groupId, MSG_ID_TTL_MILLIS);
            if (cached != null) {
                payload.setMsg_id(cached.messageId).setMsg_seq(Integer.valueOf(cached.msgSeq));
                passive = true;
            }
            try {
                V2Result result = starter.getBot().groupBaseV2.send(groupId, JSON.toJSONString(payload), Channel.SEND_MESSAGE_HEADERS);
                Integer ret = result == null ? null : result.getRet();
                // ret=200(HTTP OK) 或 0 均为成功
                boolean ok = ret != null && (ret.intValue() == 200 || ret.intValue() == 0);
                if (!ok) {
                    String msg = result == null ? "无返回" : result.getMsg();
                    plugin.log_error("向QQ群转发游戏聊天失败(被动=" + passive + ", ret=" + ret + ", msg=" + msg + ")");
                }
            } catch (Exception e) {
                plugin.log_error("向QQ群转发游戏聊天失败(被动=" + passive + "): " + e.getMessage());
            }
        }
    }
}
