package cn.zaraan.core;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;


import net.milkbowl.vault.economy.Economy;

/**
 * ZaraanCore v2.0 | zaraan星火之域 核心整合插件
 * 菜单物品 / 皮肤购买 / 死亡保护 / 进服体验 / 自动公告 / 随机传送
 * 菜单: 玩家传送 / 家管理 / 转账 | 组队系统 | AI向导(答题 + 生成建筑)
 */
public class ZaraanCore extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Set<UUID> rtpWarming = new HashSet<>();
    private final Map<UUID, String> payPending = new HashMap<>();
    private final Map<UUID, String> msgPending = new HashMap<>();
    private final Map<UUID, Consumer<String>> chatInputHandlers = new HashMap<>();
    private int announceIndex = 0;

    private Economy economy;
    private File dataFile;
    private FileConfiguration data;
    private NamespacedKey menuItemKey;

    private GuiManager gui;
    private PartyManager parties;
    private MarketManager market;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupEconomy();
        loadData();
        menuItemKey = new NamespacedKey(this, "menu_item");
        gui = new GuiManager(this);
        parties = new PartyManager(this);
        market = new MarketManager(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(parties, this);
        startAnnounceTask();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new ZaraanExpansion(this).register();
                getLogger().info("已注册变量: %zaraancore_balance%");
            } catch (Exception ex) {
                getLogger().warning("PAPI 变量注册失败: " + ex.getMessage());
            }
        }
        getLogger().info("ZaraanCore v2.3 已启用 | /menu /mtpa /mhome /mpay /mmsg /market /rtp /team /ai");
    }

    @Override
    public void onDisable() {
        if (parties != null) {
            parties.save();
        }
        if (market != null) {
            market.save();
        }
        rtpWarming.clear();
        payPending.clear();
        msgPending.clear();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("未找到 Vault!");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("未找到 Vault 经济实现!");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("已连接经济插件: " + economy.getName());
    }

    public Economy eco() {
        return economy;
    }

    public PartyManager parties() {
        return parties;
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    private String money(double d) {
        return economy != null ? economy.format(d) : String.valueOf(d);
    }

    // ============================================================
    // 指令入口
    // ============================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "zaraancore" -> handleZc(sender, args);
            case "mtpa" -> {
                if (!(sender instanceof Player p)) return msgPlayerOnly(sender);
                int page = 0;
                if (args.length >= 1) {
                    try { page = Math.max(0, Integer.parseInt(args[0]) - 1); } catch (NumberFormatException ignored) {}
                }
                gui.openTpaMenu(p, page);
            }
            case "mhome" -> {
                if (!(sender instanceof Player p)) return msgPlayerOnly(sender);
                gui.openHomeMenu(p);
            }
            case "mpay" -> {
                if (!(sender instanceof Player p)) return msgPlayerOnly(sender);
                gui.openPayMenu(p, 0);
            }
            case "mmsg" -> {
                if (!(sender instanceof Player p)) return msgPlayerOnly(sender);
                gui.openMsgMenu(p, 0);
            }
            case "rtp" -> {
                if (!(sender instanceof Player p)) return msgPlayerOnly(sender);
                startRtp(p);
            }
            case "team" -> {
                if (args.length == 0 && sender instanceof Player tp) {
                    gui.openTeamMenu(tp);
                } else {
                    parties.handleCommand(sender, args);
                }
            }
            case "tc" -> parties.handleChatCommand(sender, args);
            case "market" -> {
                if (!(sender instanceof Player p)) return msgPlayerOnly(sender);
                int page = 0;
                if (args.length >= 1) {
                    try { page = Math.max(0, Integer.parseInt(args[0]) - 1); } catch (NumberFormatException ignored) {}
                }
                gui.openMarketMenu(p, page);
            }
            case "ai" -> handleAi(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private boolean msgPlayerOnly(CommandSender sender) {
        sender.sendMessage(color("&c该命令只有玩家可以使用"));
        return true;
    }

    // ==================== AI 助手(合并自 ZaraanAI) ====================

    public class AiService {
        private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30)).build();

        public boolean isEnabled() {
            return getConfig().getBoolean("ai.enabled", false)
                    && !getConfig().getString("ai.api-key", "").isEmpty();
        }

        private String body(String system, String user) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("model", getConfig().getString("ai.model", "deepseek-chat"));
            com.google.gson.JsonArray msgs = new com.google.gson.JsonArray();
            com.google.gson.JsonObject sys = new com.google.gson.JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", system);
            msgs.add(sys);
            com.google.gson.JsonObject usr = new com.google.gson.JsonObject();
            usr.addProperty("role", "user");
            usr.addProperty("content", user);
            msgs.add(usr);
            o.add("messages", msgs);
            o.addProperty("temperature", 0.7);
            o.addProperty("max_tokens", 500);
            return o.toString();
        }

        public void ask(Player p, String question) {
            String key = getConfig().getString("ai.api-key", "");
            if (key.isEmpty()) {
                p.sendMessage(color("&cAI未配置,请联系管理员填写 ai.api-key"));
                return;
            }
            p.sendMessage(color("&7[AI] &f思考中..."));
            String system = "你是Minecraft服务器「zaraan星火之域」的AI助手小星。用简短中文回答(80字内)。"
                    + "常用命令: /menu菜单 /mtpa传送列表 /mhome家 /mpay转账 /mmsg私聊 /rtp随机传送 /team组队 /tc队聊"
                    + " /psi签到 /quests任务 /market市场 /pwarp传送点 /shop商店 /sellall hand卖手上物品 /crates抽奖 /bp背包;"
                    + " 经济: 签到有金币和物品, 传送费10-20金币, 死亡保护100金币; 规则: 禁止偷窃破坏他人建筑。"
                    + "只回答服务器和Minecraft相关话题。";
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(getConfig().getString("ai.api-url", "https://api.deepseek.com/v1/chat/completions")))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body(system, question)))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .build();
            http.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> getServer().getScheduler().runTask(ZaraanCore.this, () -> {
                        if (resp.statusCode() == 200) {
                            String reply = extract(resp.body());
                            if (reply != null && !reply.isBlank()) {
                                for (String line : reply.split("\n")) {
                                    if (!line.isBlank()) p.sendMessage(color("&b[AI] &f" + line));
                                }
                            } else {
                                p.sendMessage(color("&c[AI] 回复为空"));
                            }
                        } else {
                            p.sendMessage(color("&c[AI] 请求失败: HTTP " + resp.statusCode()));
                            getLogger().warning("AI API错误: " + resp.statusCode() + " " + resp.body());
                        }
                    }))
                    .exceptionally(ex -> {
                        getServer().getScheduler().runTask(ZaraanCore.this, () ->
                                p.sendMessage(color("&c[AI] 请求异常: " + ex.getMessage())));
                        return null;
                    });
        }

        private String extract(String respBody) {
            try {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(respBody).getAsJsonObject();
                if (o.has("choices") && !o.getAsJsonArray("choices").isEmpty()) {
                    return o.getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    private final AiService aiService = new AiService();

    private void handleAi(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { msgPlayerOnly(sender); return; }
        if (!aiService.isEnabled()) {
            p.sendMessage(color("&cAI未配置(需要 config.yml 的 ai.api-key),请联系管理员"));
            return;
        }
        if (args.length == 0) {
            p.sendMessage(color("&6AI助手小星 &7» &f/ai <问题>"));
            p.sendMessage(color("&7例: &f/ai 怎么赚钱 &8| &f/ai 怎么组队"));
            return;
        }
        StringBuilder q = new StringBuilder();
        for (String a : args) q.append(a).append(' ');
        aiService.ask(p, q.toString().trim());
    }

    public MarketManager market() {
        return market;
    }

    // ==================== 聊天输入请求(通用) ====================

    /** 请求玩家在聊天栏输入一段内容, 输入会被拦截并交给 handler */
    public void requestChatInput(Player p, String prompt, Consumer<String> handler) {
        chatInputHandlers.put(p.getUniqueId(), handler);
        p.sendMessage(color(prompt));
        p.sendMessage(color("&7(输入的内容不会被公开,输入 &c取消 &7可放弃)"));
    }

    private void handleZc(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&6ZaraanCore &7» &f/zc reload &8| &f/zc menu &8| &f/zc buy <商品> &8| &f/zc skinurl <链接>"));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("zaraancore.admin")) {
                    sender.sendMessage(color("&c无权限"));
                    return;
                }
                reloadConfig();
                parties.load();
                setupEconomy();
                sender.sendMessage(color("&aZaraanCore 已重载"));
            }
            case "menu" -> {
                Player target;
                String sub;
                if (sender instanceof Player p) {
                    target = p;
                    sub = args.length >= 2 ? args[1] : "";
                } else {
                    target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                    sub = args.length >= 3 ? args[2] : "";
                }
                if (target == null) {
                    sender.sendMessage(color("&c玩家不在线或参数不足(用法: /zc menu <玩家> [item])"));
                    return;
                }
                if (sub.equalsIgnoreCase("item")) {
                    target.getInventory().addItem(buildMenuItem());
                    target.sendMessage(color("&a已获得菜单物品,右键即可打开菜单"));
                    return;
                }
                giveMenuItem(target);
                target.performCommand(getConfig().getString("menu-item.open-command", "menu"));
            }
            case "capeurl" -> {
                if (!(sender instanceof Player p)) { msgPlayerOnly(sender); return; }
                if (args.length < 2) {
                    p.sendMessage(color("&e用法: /zc capeurl <披风图片直链>"));
                    return;
                }
                double cost = getConfig().getDouble("skin-url.cape-cost", 1000.0);
                if (cost > 0.0) {
                    if (economy == null || !economy.has(p, cost)) {
                        p.sendMessage(color("&c金币不足!需要 &e" + money(cost)));
                        return;
                    }
                    economy.withdrawPlayer(p, cost);
                }
                String url = args[1];
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    p.sendMessage(color("&c链接必须以 http:// 或 https:// 开头"));
                    return;
                }
                p.performCommand("skin cape set url:" + url);
                p.sendMessage(color("&a已应用自定义披风,花费 &e" + money(cost)));
            }
            case "skinurl" -> {
                if (!(sender instanceof Player p)) { msgPlayerOnly(sender); return; }
                if (args.length < 2) {
                    p.sendMessage(color("&e用法: /zc skinurl <皮肤图片直链 或 正版玩家名>"));
                    p.sendMessage(color("&7示例: /zc skinurl Notch &8| &f/zc skinurl https://mc-heads.net/skin/Notch"));
                    return;
                }
                double cost = getConfig().getDouble("skin-url.cost", 800.0);
                if (cost > 0.0) {
                    if (economy == null || !economy.has(p, cost)) {
                        p.sendMessage(color("&c金币不足!需要 &e" + money(cost)));
                        return;
                    }
                    economy.withdrawPlayer(p, cost);
                }
                String url = args[1];
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    // 纯玩家名 → 第三方皮肤站(mc-heads.net)
                    if (url.matches("[A-Za-z0-9_]{2,16}")) {
                        url = "https://mc-heads.net/skin/" + url;
                        p.sendMessage(color("&7已转换为皮肤站链接: &f" + url));
                    } else {
                        p.sendMessage(color("&c请输入图片直链或正版玩家名"));
                        return;
                    }
                }
                // 以玩家身份执行(自设语法), 避免控制台上下文问题
                p.performCommand("skin set url:" + url);
                p.sendMessage(color(getConfig().getString("skin-url.message", "&a已应用你的皮肤,花费 &e%cost%").replace("%cost%", money(cost))));
            }
            case "buy" -> handleBuy(sender, args);
            default -> sender.sendMessage(color("&6ZaraanCore &7» &f/zc reload &8| &f/zc menu &8| &f/zc buy <商品>"));
        }
    }

    private void handleBuy(CommandSender sender, String[] args) {
        Player player;
        String itemKey;
        if (args.length >= 3) {
            player = Bukkit.getPlayerExact(args[1]);
            itemKey = args[2];
            if (player == null) {
                sender.sendMessage(color("&c玩家不在线: " + args[1]));
                return;
            }
        } else if (args.length == 2 && sender instanceof Player) {
            player = (Player) sender;
            itemKey = args[1];
        } else {
            sender.sendMessage(color("&e用法: /zc buy <商品>"));
            return;
        }
        ConfigurationSection section = getConfig().getConfigurationSection("purchases." + itemKey);
        if (section == null) {
            sender.sendMessage(color("&c没有这个商品: " + itemKey));
            return;
        }
        double cost = section.getDouble("cost", 0.0);
        if (cost > 0.0) {
            if (economy == null || !economy.has(player, cost)) {
                player.sendMessage(color("&c金币不足!需要 &e" + money(cost)));
                return;
            }
            economy.withdrawPlayer(player, cost);
        }
        String cmd = section.getString("command", "").replace("%player%", player.getName());
        if (cmd.isEmpty()) {
            sender.sendMessage(color("&c商品 " + itemKey + " 没有配置指令"));
            return;
        }
        if (section.getBoolean("as-player", true)) {
            player.performCommand(cmd);
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        player.sendMessage(color(section.getString("message", "&a购买成功!花费 &e%cost%").replace("%cost%", money(cost))));
    }


    // ============================================================
    // 菜单物品
    // ============================================================

    public ItemStack buildMenuItem() {
        Material material;
        try {
            material = Material.valueOf(getConfig().getString("menu-item.material", "NETHER_STAR").toUpperCase());
        } catch (Exception ex) {
            material = Material.NETHER_STAR;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(getConfig().getString("menu-item.name", "&6&l服务器菜单 &7(右键打开)")));
            List<String> lore = new ArrayList<>();
            for (String line : getConfig().getStringList("menu-item.lore")) {
                lore.add(color(line));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(menuItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE)) return true;
        if (item.getType() == Material.NETHER_STAR && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            return name != null && (name.contains("服务器菜单") || name.contains("服务器菜單"));
        }
        return false;
    }

    private void giveMenuItem(Player player) {
        if (!getConfig().getBoolean("menu-item.enabled", true)) return;
        if (!getConfig().getBoolean("menu-item.give-on-join", true)) return;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMenuItem(item)) return;
        }
        int slot = getConfig().getInt("menu-item.slot", 8);
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType() == Material.AIR) {
            player.getInventory().setItem(slot, buildMenuItem());
        } else {
            int firstEmpty = player.getInventory().firstEmpty();
            if (firstEmpty >= 0) {
                player.getInventory().setItem(firstEmpty, buildMenuItem());
            } else {
                player.getInventory().setItem(slot, buildMenuItem());
            }
        }
    }

    @EventHandler
    public void onJoinMenu(PlayerJoinEvent event) {
        giveMenuItem(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        boolean mainHand = isMenuItem(event.getItem());
        boolean offHand = isMenuItem(player.getInventory().getItemInOffHand());
        if (!mainHand && !offHand) return;
        event.setCancelled(true);
        final String cmd = getConfig().getString("menu-item.open-command", "menu");
        final Player p = player;
        getServer().getScheduler().runTask(this, () -> {
            if (!p.performCommand(cmd)) {
                p.sendMessage(color("&c菜单指令执行失败,请用 &f/menu &c打开"));
                getLogger().warning("执行菜单指令失败: " + cmd);
            }
        });
    }

    // ============================================================
    // 死亡保护
    // ============================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!getConfig().getBoolean("death-keep-inventory.enabled", true)) return;
        Player player = event.getEntity();
        if (getConfig().getBoolean("death-keep-inventory.need-permission", false)
                && !player.hasPermission("zaraancore.keepinv")) return;
        double cost = getConfig().getDouble("death-keep-inventory.cost", 100.0);
        if (cost > 0.0) {
            if (economy == null || !economy.has(player, cost)) {
                player.sendMessage(color(getConfig().getString("death-keep-inventory.message-fail", "&c金币不足!").replace("%cost%", money(cost))));
                return;
            }
            economy.withdrawPlayer(player, cost);
        }
        boolean keepLevel = getConfig().getBoolean("death-keep-inventory.keep-level", true);
        event.setKeepInventory(true);
        event.setKeepLevel(keepLevel);
        event.getDrops().clear();
        if (!keepLevel) event.setDroppedExp(0);
        player.sendMessage(color(getConfig().getString("death-keep-inventory.message-success", "&a花费 &e%cost% &a保住物品").replace("%cost%", money(cost))));
    }

    // ============================================================
    // 进服体验(标题+音效+首进+上次登录)
    // ============================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        boolean firstJoin = !p.hasPlayedBefore();
        if (firstJoin && getConfig().getBoolean("join.first-join.enabled", true)) {
            String bc = getConfig().getString("join.first-join.broadcast", "");
            if (bc != null && !bc.isEmpty()) {
                Bukkit.broadcast(Component.text(color(bc.replace("%player%", p.getName()))));
            }
            if (getConfig().getBoolean("join.first-join.firework", true)) {
                Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) spawnFirework(p); }, 10L);
            }
            for (String c : getConfig().getStringList("join.first-join.commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
            }
        }

        if (getConfig().getBoolean("join.title-enabled", true)) {
            String sub = getConfig().getString("join.title-sub", "&7欢迎回来, &f%player% &8| &7在线 &a%online%&7/&f%max%")
                    .replace("%player%", p.getName())
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
            p.showTitle(Title.title(
                    Component.text(color(getConfig().getString("join.title-main", "&e&lzaraan星火之域"))),
                    Component.text(color(sub)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2800), Duration.ofMillis(600))));
            try {
                p.playSound(p.getLocation(), Sound.valueOf(getConfig().getString("join.sound", "ENTITY_PLAYER_LEVELUP").toUpperCase()), 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }

        if (getConfig().getBoolean("last-login.enabled", true) && data != null) {
            String uuid = p.getUniqueId().toString();
            long last = data.getLong(uuid, 0L);
            String template = last <= 0L
                    ? getConfig().getString("last-login.first-join", "")
                    : getConfig().getString("last-login.welcome-back", "").replace("%date%",
                            new java.text.SimpleDateFormat(getConfig().getString("last-login.date-format", "yyyy-MM-dd HH:mm")).format(new java.util.Date(last)));
            if (template != null && !template.isEmpty()) {
                final String msg = template.replace("%player%", p.getName());
                Bukkit.getScheduler().runTaskLater(this, () -> p.sendMessage(color(msg)), 20L);
            }
        }
        if (data != null) {
            data.set(p.getUniqueId().toString(), System.currentTimeMillis());
            saveData();
        }
    }

    private void spawnFirework(Player p) {
        try {
            Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .withColor(org.bukkit.Color.ORANGE, org.bukkit.Color.YELLOW)
                    .withFade(org.bukkit.Color.WHITE)
                    .flicker(true).trail(true).build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
            fw.detonate();
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID u = event.getPlayer().getUniqueId();
        payPending.remove(u);
        msgPending.remove(u);
        chatInputHandlers.remove(u);
        rtpWarming.remove(u);
    }

    // ============================================================
    // 聊天: 转账金额输入(由 GuiManager 发起)
    // ============================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatPay(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String target = payPending.remove(uuid);
        if (target == null) return;
        event.setCancelled(true);
        String amountText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException ex) {
            event.getPlayer().sendMessage(color("&c金额无效(请输入数字),转账已取消"));
            return;
        }
        if (amount <= 0) {
            event.getPlayer().sendMessage(color("&7已取消转账"));
            return;
        }
        final Player payer = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> payer.performCommand("pay " + target + " " + amount));
    }

    /**
     * 聊天称号前缀: 在 EssentialsX 格式化后的 renderer 外层再包一层,
     * 把玩家当前佩戴的称号(通过 PAPI %playerTitle_use%)显示在名字前。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatTitle(AsyncChatEvent event) {
        if (!getConfig().getBoolean("chat.title-in-chat", true)) return;
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        Player p = event.getPlayer();
        String title;
        try {
            title = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%playerTitle_use%");
        } catch (Throwable t) {
            return;
        }
        if (title == null || title.isBlank()) return;
        net.kyori.adventure.text.Component prefix;
        try {
            prefix = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(title + " ");
        } catch (Throwable t) {
            return;
        }
        io.papermc.paper.chat.ChatRenderer original = event.renderer();
        net.kyori.adventure.text.Component reset = net.kyori.adventure.text.Component.text(" ");
        event.renderer((source, sourceDisplayName, message, viewer) ->
                prefix.append(reset).append(original.render(source, sourceDisplayName, message, viewer)));
    }

    /** 私聊消息输入捕获 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatMsg(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // 通用聊天输入(市场定价/队伍名/邀请等)
        Consumer<String> inputHandler = chatInputHandlers.remove(uuid);
        if (inputHandler != null) {
            event.setCancelled(true);
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.message()).trim();
            final Player pl = event.getPlayer();
            getServer().getScheduler().runTask(this, () -> {
                if (text.equalsIgnoreCase("取消") || text.equalsIgnoreCase("cancel")) {
                    pl.sendMessage(color("&7已取消输入"));
                    return;
                }
                try {
                    inputHandler.accept(text);
                } catch (Exception ex) {
                    pl.sendMessage(color("&c输入处理出错: " + ex.getMessage()));
                }
            });
            return;
        }
        String target = msgPending.remove(uuid);
        if (target == null) return;
        event.setCancelled(true);
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();
        if (text.isEmpty()) {
            event.getPlayer().sendMessage(color("&7已取消发送"));
            return;
        }
        final Player sender = event.getPlayer();
        final String msg = text.replace("|", " ");
        final String targetName = target;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!sender.performCommand("msg " + targetName + " " + msg)) {
                Player t = Bukkit.getPlayerExact(targetName);
                if (t != null) {
                    t.sendMessage(color("&8[&d私聊&8] &f" + sender.getName() + " &8» &7" + msg));
                }
                sender.sendMessage(color("&8[&d私聊&8] &f我 &8» &7" + msg));
            }
        });
    }

    public Map<UUID, String> payPending() {
        return payPending;
    }

    public Map<UUID, String> msgPending() {
        return msgPending;
    }

    // ============================================================
    // 随机传送
    // ============================================================

    private void startRtp(Player p) {
        if (!getConfig().getBoolean("rtp.enabled", true)) {
            p.sendMessage(color("&c随机传送已关闭"));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = getConfig().getLong("rtp.cooldown-seconds", 180) * 1000L;
        Long last = rtpCooldown.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            long remain = (cooldownMs - (now - last)) / 1000 + 1;
            p.sendMessage(color("&c随机传送冷却中,还需等待 &e" + remain + " &c秒"));
            return;
        }
        if (rtpWarming.contains(p.getUniqueId())) {
            p.sendMessage(color("&c随机传送已在进行中!"));
            return;
        }
        int warmup = Math.max(0, getConfig().getInt("rtp.warmup-seconds", 3));
        if (warmup > 0) {
            rtpWarming.add(p.getUniqueId());
            p.sendMessage(color("&7不要移动,随机传送将在 &e" + warmup + " &7秒后开始..."));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!rtpWarming.contains(p.getUniqueId())) return;
                    rtpWarming.remove(p.getUniqueId());
                    if (p.isOnline()) doRtpTeleport(p);
                }
            }.runTaskLater(this, warmup * 20L);
        } else {
            doRtpTeleport(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!rtpWarming.contains(uuid)) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()) {
            rtpWarming.remove(uuid);
            event.getPlayer().sendMessage(color("&c移动导致随机传送取消!"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (rtpWarming.contains(p.getUniqueId())) {
            rtpWarming.remove(p.getUniqueId());
            p.sendMessage(color("&c受伤导致随机传送取消!"));
        }
    }

    private void doRtpTeleport(Player p) {
        World world = Bukkit.getWorld(getConfig().getString("rtp.world", "world"));
        if (world == null) world = Bukkit.getWorlds().get(0);
        int min = getConfig().getInt("rtp.min-radius", 800);
        int max = getConfig().getInt("rtp.max-radius", 5000);
        Location safe = null;
        for (int i = 0; i < 12; i++) {
            int x = (int) ((min + random.nextDouble() * (max - min)) * (random.nextBoolean() ? 1 : -1));
            int z = (int) ((min + random.nextDouble() * (max - min)) * (random.nextBoolean() ? 1 : -1));
            Location candidate = findSafe(world, x, z);
            if (candidate != null) { safe = candidate; break; }
        }
        if (safe == null) {
            p.sendMessage(color("&c没有找到安全的落点,请稍后再试"));
            return;
        }
        rtpCooldown.put(p.getUniqueId(), System.currentTimeMillis());
        p.teleport(safe);
        p.sendMessage(color("&a已随机传送到 &f" + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ() + " &a附近!"));
        try {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    private Location findSafe(World world, int x, int z) {
        Block highest = world.getHighestBlockAt(x, z);
        if (highest.getY() <= world.getMinHeight() + 1) return null;
        Material type = highest.getType();
        if (type.isSolid() && !type.name().contains("LAVA") && !type.name().contains("CACTUS")
                && !type.name().contains("MAGMA") && !type.name().contains("POWDER_SNOW")) {
            Location loc = highest.getLocation().add(0.5, 1.05, 0.5);
            Block feet = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Block head = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
            if (!feet.getType().isSolid() && !head.getType().isSolid() && !feet.isLiquid() && !head.isLiquid()) {
                return loc;
            }
        }
        return null;
    }

    // ============================================================
    // 自动公告
    // ============================================================

    private void startAnnounceTask() {
        if (!getConfig().getBoolean("announce.enabled", true)) return;
        if (getConfig().getStringList("announce.messages").isEmpty()) return;
        long interval = Math.max(60, getConfig().getLong("announce.interval-seconds", 300)) * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                List<String> list = getConfig().getStringList("announce.messages");
                if (list.isEmpty()) return;
                if (announceIndex >= list.size()) announceIndex = 0;
                String prefix = getConfig().getString("announce.prefix", "&8[&6公告&8] &7");
                Bukkit.getServer().broadcast(Component.text(color(prefix + list.get(announceIndex++))));
            }
        }.runTaskTimer(this, interval, interval);
    }

    // ============================================================
    // 数据存取
    // ============================================================

    private void loadData() {
        dataFile = new File(getDataFolder(), "lastlogin.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException ex) {
                getLogger().log(Level.WARNING, "无法创建 lastlogin.yml", ex);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "无法保存 lastlogin.yml", ex);
        }
    }

}
