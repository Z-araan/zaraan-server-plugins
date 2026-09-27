package cn.zaraan.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 组队系统: 组队/邀请/踢人/队伍聊天
 */
public class PartyManager implements Listener {

    public static class Party {
        public String name;
        public UUID leader;
        public final Set<UUID> members = new HashSet<>();

        public List<UUID> all() {
            List<UUID> all = new ArrayList<>(members);
            all.add(leader);
            return all;
        }
    }

    public static class Invite {
        public final String partyName;
        public final UUID from;
        public final long expiresAt;

        public Invite(String partyName, UUID from, long expiresAt) {
            this.partyName = partyName;
            this.from = from;
            this.expiresAt = expiresAt;
        }
    }

    private final ZaraanCore plugin;
    private final Map<String, Party> parties = new HashMap<>();       // name(lower) -> Party
    private final Map<UUID, String> memberOf = new HashMap<>();       // uuid -> party name
    private final Map<UUID, Invite> invites = new HashMap<>();        // invited uuid -> invite
    private final Set<UUID> chatMode = new HashSet<>();               // party chat toggled

    public PartyManager(ZaraanCore plugin) {
        this.plugin = plugin;
        load();
    }

    // ==================== 命令 ====================

    public void handleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ZaraanCore.color("&c该命令只有玩家可以使用"));
            return;
        }
        if (args.length == 0) {
            help(p);
            return;
        }
        int max = plugin.getConfig().getInt("party.max-members", 8);
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) { p.sendMessage(ZaraanCore.color("&e用法: &f/team create <队伍名>")); return; }
                if (memberOf.containsKey(p.getUniqueId())) { p.sendMessage(ZaraanCore.color("&c你已经在一个队伍里了")); return; }
                String name = args[1];
                if (name.length() > 12) { p.sendMessage(ZaraanCore.color("&c队伍名太长(最多12字)")); return; }
                if (parties.containsKey(name.toLowerCase())) { p.sendMessage(ZaraanCore.color("&c这个名字已被使用")); return; }
                Party party = new Party();
                party.name = name;
                party.leader = p.getUniqueId();
                parties.put(name.toLowerCase(), party);
                memberOf.put(p.getUniqueId(), name.toLowerCase());
                p.sendMessage(ZaraanCore.color("&a队伍 &f" + name + " &a创建成功!用 &f/team invite <玩家> &a邀请队友"));
                save();
            }
            case "invite" -> {
                Party party = myParty(p, true);
                if (party == null) return;
                if (args.length < 2) { p.sendMessage(ZaraanCore.color("&e用法: &f/team invite <玩家>")); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { p.sendMessage(ZaraanCore.color("&c玩家不在线: " + args[1])); return; }
                if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(ZaraanCore.color("&c不能邀请自己")); return; }
                if (memberOf.containsKey(target.getUniqueId())) { p.sendMessage(ZaraanCore.color("&c对方已经在别的队伍里")); return; }
                if (party.all().size() >= max) { p.sendMessage(ZaraanCore.color("&c队伍已满(" + max + "人)")); return; }
                invites.put(target.getUniqueId(), new Invite(party.name, p.getUniqueId(), System.currentTimeMillis() + 60_000));
                p.sendMessage(ZaraanCore.color("&a已邀请 &f" + target.getName() + " &a加入队伍"));
                target.sendMessage(ZaraanCore.color("&6[组队] &f" + p.getName() + " &e邀请你加入队伍 &f" + party.name));
                target.sendMessage(ZaraanCore.color("&e输入 &a/team accept &e加入,或 &c/team deny &e拒绝(60秒内有效)"));
            }
            case "accept" -> {
                Invite invite = invites.get(p.getUniqueId());
                if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
                    invites.remove(p.getUniqueId());
                    p.sendMessage(ZaraanCore.color("&c没有有效的邀请"));
                    return;
                }
                invites.remove(p.getUniqueId());
                Party party = parties.get(invite.partyName.toLowerCase());
                if (party == null) { p.sendMessage(ZaraanCore.color("&c队伍已解散")); return; }
                if (party.all().size() >= max) { p.sendMessage(ZaraanCore.color("&c队伍已满")); return; }
                party.members.add(p.getUniqueId());
                memberOf.put(p.getUniqueId(), party.name.toLowerCase());
                broadcast(party, "&6[组队] &f" + p.getName() + " &e加入了队伍!");
                save();
            }
            case "deny" -> {
                if (invites.remove(p.getUniqueId()) != null) {
                    p.sendMessage(ZaraanCore.color("&7已拒绝邀请"));
                } else {
                    p.sendMessage(ZaraanCore.color("&c没有待处理的邀请"));
                }
            }
            case "leave" -> {
                Party party = myParty(p, false);
                if (party == null) { p.sendMessage(ZaraanCore.color("&c你不在任何队伍里")); return; }
                leave(p, party);
            }
            case "kick" -> {
                Party party = myParty(p, true);
                if (party == null) return;
                if (args.length < 2) { p.sendMessage(ZaraanCore.color("&e用法: &f/team kick <玩家>")); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                UUID tid = target != null ? target.getUniqueId() : lookupUuid(args[1]);
                if (tid == null || !party.members.contains(tid)) { p.sendMessage(ZaraanCore.color("&c对方不是你的队员")); return; }
                party.members.remove(tid);
                memberOf.remove(tid);
                chatMode.remove(tid);
                broadcast(party, "&6[组队] &c队长踢出了 &f" + args[1]);
                Player t = Bukkit.getPlayer(tid);
                if (t != null) t.sendMessage(ZaraanCore.color("&c你被移出了队伍 " + party.name));
                save();
            }
            case "disband" -> {
                Party party = myParty(p, true);
                if (party == null) return;
                broadcast(party, "&6[组队] &c队伍 &f" + party.name + " &c已解散!");
                for (UUID m : party.all()) {
                    memberOf.remove(m);
                    chatMode.remove(m);
                }
                parties.remove(party.name.toLowerCase());
                save();
            }
            case "list" -> {
                Party party = myParty(p, false);
                if (party == null) { p.sendMessage(ZaraanCore.color("&c你不在任何队伍里")); return; }
                p.sendMessage(ZaraanCore.color("&6=== 队伍 &f" + party.name + " &6(" + party.all().size() + "/" + max + ") ==="));
                for (UUID m : party.all()) {
                    Player mp = Bukkit.getPlayer(m);
                    String tag = m.equals(party.leader) ? " &e[队长]" : "";
                    String online = mp != null ? "&a● " : "&8● ";
                    String nm = mp != null ? mp.getName() : "?";
                    p.sendMessage(ZaraanCore.color(" " + online + "&f" + nm + tag));
                }
            }
            case "chat" -> toggleChat(p);
            default -> help(p);
        }
    }

    /** /tc [消息] — 无参数切换队伍聊天模式,有参数直接发一条 */
    public void handleChatCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ZaraanCore.color("&c该命令只有玩家可以使用"));
            return;
        }
        Party party = myParty(p, false);
        if (party == null) {
            p.sendMessage(ZaraanCore.color("&c你不在任何队伍里,先 &f/team create &c或接受邀请"));
            return;
        }
        if (args.length == 0) {
            toggleChat(p);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String a : args) sb.append(a).append(' ');
        sendPartyChat(p, sb.toString().trim());
    }

    private void toggleChat(Player p) {
        if (!memberOf.containsKey(p.getUniqueId())) {
            p.sendMessage(ZaraanCore.color("&c你不在任何队伍里"));
            return;
        }
        if (chatMode.contains(p.getUniqueId())) {
            chatMode.remove(p.getUniqueId());
            p.sendMessage(ZaraanCore.color("&e队伍聊天模式 &c关闭 &e,说话恢复全服可见"));
        } else {
            chatMode.add(p.getUniqueId());
            p.sendMessage(ZaraanCore.color("&e队伍聊天模式 &a开启 &e,你说话只有队友能看到!"));
            p.sendMessage(ZaraanCore.color("&7再次输入 &f/tc &7切回全服聊天"));
        }
    }

    private void help(Player p) {
        p.sendMessage(ZaraanCore.color("&6===== 组队系统 ====="));
        p.sendMessage(ZaraanCore.color("&f/team create <名字> &7- 创建队伍"));
        p.sendMessage(ZaraanCore.color("&f/team invite <玩家> &7- 邀请玩家"));
        p.sendMessage(ZaraanCore.color("&f/team accept&7/&fdeny &7- 接受/拒绝邀请"));
        p.sendMessage(ZaraanCore.color("&f/team list &7- 查看队员"));
        p.sendMessage(ZaraanCore.color("&f/team kick <玩家> &7- 踢人(队长)"));
        p.sendMessage(ZaraanCore.color("&f/team disband &7- 解散(队长)"));
        p.sendMessage(ZaraanCore.color("&f/team leave &7- 退出队伍"));
        p.sendMessage(ZaraanCore.color("&f/tc &7- 切换队伍聊天 &8| &f/tc <消息> &7- 发一条队聊"));
    }

    // ==================== 聊天拦截 ====================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!chatMode.contains(p.getUniqueId())) return;
        Party party = myParty(p, false);
        if (party == null) {
            chatMode.remove(p.getUniqueId());
            return;
        }
        event.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> sendPartyChat(p, msg));
    }

    private void sendPartyChat(Player from, String msg) {
        Party party = myParty(from, false);
        if (party == null) return;
        String line = ZaraanCore.color("&8[&d队伍&8] &f" + from.getName() + " &8» &7" + msg);
        for (UUID m : party.all()) {
            Player mp = Bukkit.getPlayer(m);
            if (mp != null) mp.sendMessage(line);
        }
        plugin.getLogger().info("[队伍:" + party.name + "] " + from.getName() + " » " + msg);
    }

    // ==================== 工具 ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID u = event.getPlayer().getUniqueId();
        chatMode.remove(u);
        invites.remove(u);
        String partyName = memberOf.get(u);
        if (partyName == null) return;
        Party party = parties.get(partyName);
        if (party == null) return;
        // 离线不退队,但队长离线不影响;若全员离线由登录时处理(简化:保留队伍)
        if (party.leader.equals(u) && party.members.isEmpty()) {
            // 队长离线且没队员 → 解散
            memberOf.remove(u);
            parties.remove(partyName);
        }
    }

    private Party myParty(Player p, boolean needLeader) {
        String name = memberOf.get(p.getUniqueId());
        if (name == null) {
            p.sendMessage(ZaraanCore.color("&c你不在任何队伍里,先 &f/team create <名字> &c或接受邀请"));
            return null;
        }
        Party party = parties.get(name);
        if (party == null) {
            memberOf.remove(p.getUniqueId());
            p.sendMessage(ZaraanCore.color("&c队伍数据异常,已重置"));
            return null;
        }
        if (needLeader && !party.leader.equals(p.getUniqueId())) {
            p.sendMessage(ZaraanCore.color("&c只有队长才能这么做"));
            return null;
        }
        return party;
    }

    private void leave(Player p, Party party) {
        if (party.leader.equals(p.getUniqueId())) {
            if (party.members.isEmpty()) {
                parties.remove(party.name.toLowerCase());
                memberOf.remove(p.getUniqueId());
                chatMode.remove(p.getUniqueId());
                p.sendMessage(ZaraanCore.color("&7队伍已解散"));
            } else {
                // 转给第一个队员
                UUID next = party.members.iterator().next();
                party.members.remove(next);
                party.leader = next;
                memberOf.remove(p.getUniqueId());
                chatMode.remove(p.getUniqueId());
                broadcast(party, "&6[组队] &e队长 &f" + p.getName() + " &e离开了,新队长: &f" + Bukkit.getOfflinePlayer(next).getName());
                p.sendMessage(ZaraanCore.color("&7你退出了队伍"));
            }
        } else {
            party.members.remove(p.getUniqueId());
            memberOf.remove(p.getUniqueId());
            chatMode.remove(p.getUniqueId());
            broadcast(party, "&6[组队] &f" + p.getName() + " &e退出了队伍");
            p.sendMessage(ZaraanCore.color("&7你退出了队伍"));
        }
        save();
    }

    private void broadcast(Party party, String colored) {
        String line = ZaraanCore.color(colored);
        for (UUID m : party.all()) {
            Player mp = Bukkit.getPlayer(m);
            if (mp != null) mp.sendMessage(line);
        }
    }

    private UUID lookupUuid(String name) {
        var offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline.getUniqueId() : null;
    }

    // ==================== 持久化 ====================

    public void save() {
        File f = new File(plugin.getDataFolder(), "teams.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (Party party : parties.values()) {
            String path = "parties." + party.name;
            y.set(path + ".leader", party.leader.toString());
            List<String> ms = new ArrayList<>();
            for (UUID m : party.members) ms.add(m.toString());
            y.set(path + ".members", ms);
        }
        try {
            y.save(f);
        } catch (IOException ignored) {}
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "teams.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("parties");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            String leaderStr = root.getString(name + ".leader");
            if (leaderStr == null) continue;
            try {
                Party party = new Party();
                party.name = name;
                party.leader = UUID.fromString(leaderStr);
                for (String m : root.getStringList(name + ".members")) {
                    party.members.add(UUID.fromString(m));
                    memberOf.put(UUID.fromString(m), name.toLowerCase());
                }
                memberOf.put(party.leader, name.toLowerCase());
                parties.put(name.toLowerCase(), party);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public Set<UUID> chatModePlayers() {
        return chatMode;
    }

    public Party getPartyOf(UUID u) {
        String name = memberOf.get(u);
        return name == null ? null : parties.get(name);
    }

    public Component memberStatus(UUID u) {
        Party p = getPartyOf(u);
        if (p == null) return Component.text(ZaraanCore.color("&7无队伍"));
        return Component.text(ZaraanCore.color("&d" + p.name + " &7(" + p.all().size() + "人)"));
    }
}
