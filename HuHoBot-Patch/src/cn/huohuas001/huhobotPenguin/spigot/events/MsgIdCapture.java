package cn.huohuas001.huhobotPenguin.spigot.events;

import io.github.kloping.qqbot.api.v2.GroupMessageEvent;
import io.github.kloping.qqbot.impl.ListenerHost;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 捕获 QQ 群最近一条消息的 msg_id / msg_seq,
 * 供游戏→QQ 转发时作为"被动回复"发送(绕过平台主动消息限制)。
 * zaraan 2026-09-27
 */
public final class MsgIdCapture extends ListenerHost {

    public static final class Cached {
        public final String messageId;
        public final int msgSeq;
        public final long time;

        Cached(String messageId, int msgSeq, long time) {
            this.messageId = messageId;
            this.msgSeq = msgSeq;
            this.time = time;
        }
    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    public static Cached get(String groupId, long maxAgeMillis) {
        Cached c = CACHE.get(groupId);
        if (c == null) return null;
        if (System.currentTimeMillis() - c.time > maxAgeMillis) return null;
        return c;
    }

    @ListenerHost.EventReceiver
    public void onGroupMessage(GroupMessageEvent event) {
        try {
            String groupId = event.getGroupOpenId();
            if (groupId == null) groupId = event.getGroupId();
            String msgId = null;
            if (event.getRawMessage() != null) {
                msgId = event.getRawMessage().getId();
            }
            if (groupId == null || msgId == null || msgId.isEmpty()) return;
            Integer seq = event.getMsgSeq();
            CACHE.put(groupId, new Cached(msgId, seq == null ? 0 : seq, System.currentTimeMillis()));
        } catch (Throwable ignored) {
        }
    }
}
