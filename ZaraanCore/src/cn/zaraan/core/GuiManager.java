package cn.zaraan.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * 菜单 GUI: 玩家传送 / 家管理 / 转账
 */
public class GuiManager implements Listener {

    private final ZaraanCore plugin;

    public GuiManager(ZaraanCore plugin) {
        this.plugin = plugin;
    }

    public enum MenuType { TPA, PAY_SELECT, HOME, MSG_SELECT, TEAM, WARP, MARKET, MARKET_SELL, MARKET_MINE }

    public static class ZaraanHolder implements InventoryHolder {
        public final MenuType type;
        public int page;

        public ZaraanHolder(MenuType type, int page) {
            this.type = type;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // ==================== /mtpa ====================

    public void openTpaMenu(Player viewer, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(pl -> pl.getUniqueId().equals(viewer.getUniqueId()));
        int perPage = 36;
        int pages = Math.max(1, (int) Math.ceil(online.size() / (double) perPage));
        page = Math.min(page, pages - 1);
        ZaraanHolder holder = new ZaraanHolder(MenuType.TPA, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ZaraanCore.color("&8玩家传送 &7» &f第 " + (page + 1) + "/" + pages + " 页"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= online.size()) break;
            inv.setItem(i, playerHead(online.get(idx)));
        }
        if (online.isEmpty()) {
            inv.setItem(13, button(Material.GRAY_DYE, "&7现在没有其他玩家在线", ""));
        }
        inv.setItem(45, button(Material.LIME_DYE, "&a&l接受传送请求", "&e有人向你发了 /tpa ?点这里同意"));
        inv.setItem(46, button(Material.BARRIER, "&c&l拒绝传送请求", "&e点这里拒绝"));
        inv.setItem(48, button(Material.CLOCK, "&e&l刷新列表", ""));
        if (page > 0) inv.setItem(50, button(Material.ARROW, "&b&l上一页", ""));
        if (page < pages - 1) inv.setItem(52, button(Material.SPECTRAL_ARROW, "&b&l下一页", ""));
        inv.setItem(53, button(Material.BARRIER, "&c&l关闭", ""));
        viewer.openInventory(inv);
    }

    private ItemStack playerHead(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(ZaraanCore.color("&a" + target.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(ZaraanCore.color("&7世界: &f" + target.getWorld().getName()));
            lore.add(ZaraanCore.color("&7坐标: &f" + target.getLocation().getBlockX() + ", "
                    + target.getLocation().getBlockY() + ", " + target.getLocation().getBlockZ()));
            lore.add("");
            lore.add(ZaraanCore.color("&e点击发送传送请求"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== /mhome ====================

    public void openHomeMenu(Player viewer) {
        List<String> homes = new ArrayList<>(getEssentialsHomes(viewer));
        ZaraanHolder holder = new ZaraanHolder(MenuType.HOME, 0);
        Inventory inv = Bukkit.createInventory(holder, 27, ZaraanCore.color("&8我的家 &7» &f共 " + homes.size() + " 个"));
        int slot = 0;
        for (String home : homes) {
            if (slot >= 18) break;
            ItemStack item = new ItemStack(Material.RED_BED);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ZaraanCore.color("&a" + home));
                List<String> lore = new ArrayList<>();
                lore.add(ZaraanCore.color("&e左键: &7传送到该家"));
                lore.add(ZaraanCore.color("&cShift+右键: &7删除该家"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }
        if (homes.isEmpty()) {
            inv.setItem(13, button(Material.GRAY_DYE, "&7还没有家", "&e点击下方「设置家」创建你的第一个家"));
        }
        inv.setItem(22, button(Material.WRITABLE_BOOK, "&e&l设置家", "&7把当前位置设为你的家"));
        inv.setItem(26, button(Material.BARRIER, "&c&l关闭", ""));
        viewer.openInventory(inv);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getEssentialsHomes(Player player) {
        try {
            Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess == null) return List.of();
            Object user = ess.getClass().getMethod("getUser", UUID.class).invoke(ess, player.getUniqueId());
            if (user == null) return List.of();
            Object homes = user.getClass().getMethod("getHomes").invoke(user);
            if (homes instanceof Collection) return (Collection<String>) homes;
        } catch (Exception ignored) {}
        return List.of();
    }

    // ==================== /mpay ====================

    public void openPayMenu(Player viewer, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(pl -> pl.getUniqueId().equals(viewer.getUniqueId()));
        int perPage = 36;
        int pages = Math.max(1, (int) Math.ceil(online.size() / (double) perPage));
        page = Math.min(page, pages - 1);
        ZaraanHolder holder = new ZaraanHolder(MenuType.PAY_SELECT, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ZaraanCore.color("&8转账 &7» &f选择玩家 (" + (page + 1) + "/" + pages + ")"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= online.size()) break;
            Player target = online.get(idx);
            ItemStack item = playerHead(target);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ZaraanCore.color("&6" + target.getName()));
                meta.setLore(new ArrayList<>(List.of(ZaraanCore.color("&e点击选择 TA 作为转账对象"))));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        inv.setItem(49, button(Material.BARRIER, "&c&l关闭", ""));
        viewer.openInventory(inv);
    }

    // ==================== /mmsg 私聊 ====================

    public void openMsgMenu(Player viewer, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(pl -> pl.getUniqueId().equals(viewer.getUniqueId()));
        int perPage = 36;
        int pages = Math.max(1, (int) Math.ceil(online.size() / (double) perPage));
        page = Math.min(page, pages - 1);
        ZaraanHolder holder = new ZaraanHolder(MenuType.MSG_SELECT, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ZaraanCore.color("&8私聊 &7» &f选择玩家 (" + (page + 1) + "/" + pages + ")"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= online.size()) break;
            Player target = online.get(idx);
            ItemStack item = playerHead(target);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ZaraanCore.color("&d" + target.getName()));
                meta.setLore(new ArrayList<>(List.of(ZaraanCore.color("&e点击后,在聊天栏输入要发的私聊内容"))));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        inv.setItem(49, button(Material.BARRIER, "&c&l关闭", ""));
        viewer.openInventory(inv);
    }

    // ==================== /team 组队 ====================

    public void openTeamMenu(Player viewer) {
        ZaraanHolder holder = new ZaraanHolder(MenuType.TEAM, 0);
        Inventory inv = Bukkit.createInventory(holder, 27, ZaraanCore.color("&8组队系统"));
        PartyManager.Party party = plugin.parties().getPartyOf(viewer.getUniqueId());
        if (party == null) {
            // 未组队
            inv.setItem(11, button(Material.IRON_SWORD, "&6&l创建队伍", "&e点击后,在聊天栏输入队伍名字"));
            inv.setItem(13, button(Material.NAME_TAG, "&a&l加入队伍", "&7请队友用 /team invite 邀请你"));
            inv.setItem(15, button(Material.BOOK, "&7&l组队说明", "&7队伍最多8人,可队伍聊天"));
        } else {
            boolean leader = party.leader.equals(viewer.getUniqueId());
            inv.setItem(4, button(Material.PLAYER_HEAD, "&6队伍: &f" + party.name,
                    "&7成员: &f" + party.all().size() + " &7人" + (leader ? " &8| &e你是队长" : "")));
            // 成员列表(5-13 槽位)
            int slot = 9;
            for (UUID m : party.all()) {
                if (slot > 17) break;
                Player mp = Bukkit.getPlayer(m);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(m));
                    String nm = mp != null ? mp.getName() : Bukkit.getOfflinePlayer(m).getName();
                    meta.setDisplayName(ZaraanCore.color("&f" + nm + (m.equals(party.leader) ? " &e[队长]" : "")));
                    List<String> lore = new ArrayList<>();
                    lore.add(ZaraanCore.color(mp != null ? "&a● 在线" : "&8○ 离线"));
                    if (leader && !m.equals(viewer.getUniqueId())) {
                        lore.add("");
                        lore.add(ZaraanCore.color("&c左键: 踢出队伍"));
                    }
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
                inv.setItem(slot++, head);
            }
            if (leader) {
                inv.setItem(21, button(Material.PAPER, "&a&l邀请玩家", "&e点击后,在聊天栏输入玩家名"));
                inv.setItem(23, button(Material.BARRIER, "&c&l解散队伍", "&7所有队员都会离队"));
            } else {
                inv.setItem(22, button(Material.RED_DYE, "&c&l退出队伍", ""));
            }
            inv.setItem(25, button(Material.WRITABLE_BOOK, "&d&l队伍聊天开关", "&7点击切换 /tc 聊天模式"));
            inv.setItem(26, button(Material.BARRIER, "&c&l关闭", ""));
        }
        viewer.openInventory(inv);
    }

    // ==================== /mwarp 私人传送点 ====================

    public void openWarpMenu(Player viewer, int page) {
        List<String> warps = plugin.listPlayerWarps(viewer);
        int perPage = 36;
        int pages = Math.max(1, (int) Math.ceil(warps.size() / (double) perPage));
        page = Math.min(page, pages - 1);
        ZaraanHolder holder = new ZaraanHolder(MenuType.WARP, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ZaraanCore.color("&8私人传送点 &7» &f第 " + (page + 1) + "/" + pages + " 页"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= warps.size()) break;
            String name = warps.get(idx);
            inv.setItem(i, button(Material.ENDER_EYE, "&5" + name,
                    "&e左键: 传送过去\n&cShift+右键: 删除该传送点"));
        }
        if (warps.isEmpty()) {
            inv.setItem(13, button(Material.GRAY_DYE, "&7还没有传送点", "&e点击下方「创建传送点」!"));
        }
        inv.setItem(45, button(Material.NAME_TAG, "&a&l创建传送点", "&e点击后,在聊天栏输入名字"));
        inv.setItem(47, button(Material.BOOK, "&7&l使用说明", "&7创建后所有人可见,可传送参观"));
        inv.setItem(49, button(Material.CLOCK, "&e&l刷新", ""));
        if (page > 0) inv.setItem(50, button(Material.ARROW, "&b&l上一页", ""));
        if (page < pages - 1) inv.setItem(52, button(Material.SPECTRAL_ARROW, "&b&l下一页", ""));
        inv.setItem(53, button(Material.BARRIER, "&c&l关闭", ""));
        viewer.openInventory(inv);
    }

    // ==================== /market 玩家交易市场 ====================

    public void openMarketMenu(Player viewer, int page) {
        List<MarketManager.Listing> list = plugin.market().all();
        int perPage = 36;
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        page = Math.min(page, pages - 1);
        ZaraanHolder holder = new ZaraanHolder(MenuType.MARKET, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ZaraanCore.color("&8交易市场 &7» &f第 " + (page + 1) + "/" + pages + " 页"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= list.size()) break;
            MarketManager.Listing l = list.get(idx);
            ItemStack show = l.item.clone();
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ZaraanCore.color("&7售价: &6" + (plugin.eco() != null ? plugin.eco().format(l.price) : l.price)));
                lore.add(ZaraanCore.color("&7卖家: &f" + l.sellerName));
                lore.add("");
                lore.add(ZaraanCore.color(l.seller.equals(viewer.getUniqueId()) ? "&cShift+右键: 撤回商品" : "&e左键: 购买"));
                meta.setLore(lore);
                show.setItemMeta(meta);
            }
            inv.setItem(i, show);
        }
        if (list.isEmpty()) {
            inv.setItem(13, button(Material.GRAY_DYE, "&7市场空空的", "&e点击下方「出售物品」上架你的东西"));
        }
        inv.setItem(45, button(Material.EMERALD, "&a&l出售物品", "&7把主手上的物品上架出售"));
        inv.setItem(47, button(Material.CHEST, "&e&l我的商品", "&7查看/撤回自己上架的东西"));
        inv.setItem(49, button(Material.CLOCK, "&e&l刷新", ""));
        if (page > 0) inv.setItem(50, button(Material.ARROW, "&b&l上一页", ""));
        if (page < pages - 1) inv.setItem(52, button(Material.SPECTRAL_ARROW, "&b&l下一页", ""));
        inv.setItem(53, button(Material.BARRIER, "&c&l关闭", ""));
        viewer.openInventory(inv);
    }

    public void openMarketSellMenu(Player viewer, int page) {
        // 显示玩家背包可上架的物品(排除菜单物品)
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : viewer.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer()
                    .has(new org.bukkit.NamespacedKey(plugin, "menu_item"), org.bukkit.persistence.PersistentDataType.BYTE)) continue;
            items.add(it);
        }
        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) perPage));
        page = Math.min(page, pages - 1);
        ZaraanHolder holder = new ZaraanHolder(MenuType.MARKET_SELL, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ZaraanCore.color("&8出售物品 &7» &f选择要卖的物品"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= items.size()) break;
            ItemStack show = items.get(idx).clone();
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ZaraanCore.color("&e点击选择 → 聊天栏输入价格"));
                meta.setLore(lore);
                show.setItemMeta(meta);
            }
            inv.setItem(i, show);
        }
        inv.setItem(49, button(Material.BARRIER, "&c&l返回市场", ""));
        viewer.openInventory(inv);
    }

    public void openMyMarketMenu(Player viewer, int page) {
        List<MarketManager.Listing> mine = new ArrayList<>();
        for (MarketManager.Listing l : plugin.market().all()) {
            if (l.seller.equals(viewer.getUniqueId())) mine.add(l);
        }
        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(mine.size() / (double) perPage));
        page = Math.min(page, pages - 1);
        ZaraanHolder holder = new ZaraanHolder(MenuType.MARKET_MINE, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ZaraanCore.color("&8我的商品 &7» &f共 " + mine.size() + " 件"));
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= mine.size()) break;
            MarketManager.Listing l = mine.get(idx);
            ItemStack show = l.item.clone();
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ZaraanCore.color("&7售价: &6" + (plugin.eco() != null ? plugin.eco().format(l.price) : l.price)));
                lore.add(ZaraanCore.color("&e左键: 撤回商品"));
                meta.setLore(lore);
                show.setItemMeta(meta);
            }
            inv.setItem(i, show);
        }
        inv.setItem(49, button(Material.BARRIER, "&c&l返回市场", ""));
        viewer.openInventory(inv);
    }

    // ==================== 通用 ====================

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ZaraanCore.color(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(new ArrayList<>(List.of(ZaraanCore.color(lore))));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== 点击处理 ====================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ZaraanHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != top) return;
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        String plainName = "";
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta != null && clickedMeta.hasDisplayName()) {
            plainName = ChatColor.stripColor(clickedMeta.getDisplayName());
        }

        switch (holder.type) {
            case TPA -> {
                if (slot <= 35) {
                    if (plainName.isEmpty()) return;
                    player.closeInventory();
                    player.performCommand("tpa " + plainName);
                    player.sendMessage(ZaraanCore.color("&a已向 &f" + plainName + " &a发送传送请求,等待对方接受"));
                } else if (slot == 45) {
                    player.closeInventory();
                    player.performCommand("tpaccept");
                } else if (slot == 46) {
                    player.closeInventory();
                    player.performCommand("tpdeny");
                } else if (slot == 48) {
                    openTpaMenu(player, holder.page);
                } else if (slot == 50) {
                    openTpaMenu(player, holder.page - 1);
                } else if (slot == 52) {
                    openTpaMenu(player, holder.page + 1);
                } else if (slot == 53) {
                    player.closeInventory();
                }
            }
            case HOME -> {
                if (slot == 22) {
                    player.closeInventory();
                    player.performCommand("sethome");
                } else if (slot == 26) {
                    player.closeInventory();
                } else if (slot < 18 && !plainName.isEmpty()) {
                    if (event.getClick() == ClickType.SHIFT_RIGHT) {
                        player.closeInventory();
                        player.performCommand("delhome " + plainName);
                        player.sendMessage(ZaraanCore.color("&7已尝试删除家: &f" + plainName));
                    } else {
                        player.closeInventory();
                        player.performCommand("home " + plainName);
                    }
                }
            }
            case MSG_SELECT -> {
                if (slot <= 35 && !plainName.isEmpty()) {
                    String target = plainName;
                    player.closeInventory();
                    plugin.msgPending().put(player.getUniqueId(), target);
                    player.sendMessage(ZaraanCore.color("&d[私聊] &f对 &e" + target + " &f说些什么? 直接在聊天栏输入"));
                    player.sendMessage(ZaraanCore.color("&7(只有对方能看到,输入内容不会被公开)"));
                } else if (slot == 49) {
                    player.closeInventory();
                }
            }
            case TEAM -> {
                PartyManager.Party party = plugin.parties().getPartyOf(player.getUniqueId());
                if (party == null) {
                    if (slot == 11) {
                        player.closeInventory();
                        plugin.requestChatInput(player, "&6请输入队伍名字(最多12字):", name -> {
                            name = name.trim();
                            if (name.isEmpty() || name.length() > 12) {
                                player.sendMessage(ZaraanCore.color("&c名字无效"));
                                return;
                            }
                            if (name.contains(" ")) name = name.replace(" ", "_");
                            player.performCommand("team create " + name);
                        });
                    } else if (slot == 15) {
                        player.closeInventory();
                        player.sendMessage(ZaraanCore.color("&6找队友邀请你: &f/team invite <你> &6,或者用 &f/team create <名字> &6自己建队"));
                    }
                } else {
                    if (slot >= 9 && slot <= 17) {
                        // 成员: 队长左键踢人
                        String nm = plainName.replace(" [队长]", "").trim();
                        if (party.leader.equals(player.getUniqueId()) && !nm.isEmpty() && !nm.equalsIgnoreCase(player.getName())) {
                            player.closeInventory();
                            player.performCommand("team kick " + nm);
                        }
                    } else if (slot == 21) {
                        player.closeInventory();
                        plugin.requestChatInput(player, "&6请输入要邀请的玩家名:", name -> {
                            player.performCommand("team invite " + name.trim());
                            player.performCommand("team");
                        });
                    } else if (slot == 23) {
                        player.closeInventory();
                        player.performCommand("team disband");
                    } else if (slot == 22) {
                        player.closeInventory();
                        player.performCommand("team leave");
                    } else if (slot == 25) {
                        player.closeInventory();
                        player.performCommand("tc");
                    } else if (slot == 26) {
                        player.closeInventory();
                    }
                }
            }
            case WARP -> {
                if (slot <= 35) {
                    if (plainName.isEmpty()) return;
                    if (event.getClick() == ClickType.SHIFT_RIGHT) {
                        player.closeInventory();
                        plugin.deletePlayerWarp(player, plainName);
                    } else {
                        player.closeInventory();
                        plugin.teleportPlayerWarp(player, plainName);
                    }
                } else if (slot == 45) {
                    player.closeInventory();
                    plugin.requestChatInput(player, "&6请输入传送点名字:", name -> {
                        name = name.trim();
                        if (name.isEmpty()) {
                            player.sendMessage(ZaraanCore.color("&c名字无效"));
                            return;
                        }
                        plugin.createPlayerWarp(player, name);
                    });
                } else if (slot == 49) {
                    openWarpMenu(player, holder.page);
                } else if (slot == 50) {
                    openWarpMenu(player, holder.page - 1);
                } else if (slot == 52) {
                    openWarpMenu(player, holder.page + 1);
                } else if (slot == 53) {
                    player.closeInventory();
                }
            }
            case MARKET -> {
                if (slot <= 35) {
                    List<MarketManager.Listing> list = plugin.market().all();
                    int idx = holder.page * 36 + slot;
                    if (idx >= list.size()) return;
                    MarketManager.Listing l = list.get(idx);
                    if (l.seller.equals(player.getUniqueId())) {
                        if (event.getClick() == ClickType.SHIFT_RIGHT) {
                            player.getInventory().addItem(l.item.clone());
                            plugin.market().remove(l);
                            player.sendMessage(ZaraanCore.color("&a已撤回商品"));
                            openMarketMenu(player, holder.page);
                        }
                    } else {
                        String err = plugin.market().buy(player, l);
                        if (err != null) {
                            player.sendMessage(ZaraanCore.color("&c" + err));
                        } else {
                            player.sendMessage(ZaraanCore.color("&a购买成功!"));
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                        }
                        openMarketMenu(player, holder.page);
                    }
                } else if (slot == 45) {
                    if (plugin.market().countBySeller(player.getUniqueId()) >= plugin.getConfig().getInt("market.max-listings-per-player", 10)) {
                        player.sendMessage(ZaraanCore.color("&c你的上架数量已达上限"));
                        return;
                    }
                    openMarketSellMenu(player, 0);
                } else if (slot == 47) {
                    openMyMarketMenu(player, 0);
                } else if (slot == 49) {
                    openMarketMenu(player, holder.page);
                } else if (slot == 50) {
                    openMarketMenu(player, holder.page - 1);
                } else if (slot == 52) {
                    openMarketMenu(player, holder.page + 1);
                } else if (slot == 53) {
                    player.closeInventory();
                }
            }
            case MARKET_SELL -> {
                if (slot <= 44) {
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem == null || clickedItem.getType().isAir()) return;
                    ItemStack toSell = clickedItem.clone();
                    player.closeInventory();
                    plugin.requestChatInput(player, "&6请输入售价(金币),输入 0 取消:", input -> {
                        double price;
                        try {
                            price = Double.parseDouble(input.trim());
                        } catch (NumberFormatException ex) {
                            player.sendMessage(ZaraanCore.color("&c价格无效,已取消"));
                            return;
                        }
                        if (price <= 0) {
                            player.sendMessage(ZaraanCore.color("&7已取消"));
                            return;
                        }
                        // 从背包扣除1个该物品
                        ItemStack invItem = null;
                        for (ItemStack it : player.getInventory().getStorageContents()) {
                            if (it != null && it.isSimilar(toSell)) {
                                invItem = it;
                                break;
                            }
                        }
                        if (invItem == null) {
                            player.sendMessage(ZaraanCore.color("&c背包里找不到该物品了"));
                            return;
                        }
                        ItemStack single = invItem.clone();
                        single.setAmount(1);
                        invItem.setAmount(invItem.getAmount() - 1);
                        if (!plugin.market().add(player, single, price)) {
                            player.getInventory().addItem(single); // 失败退回
                            player.sendMessage(ZaraanCore.color("&c上架失败(价格超出上限?)"));
                            return;
                        }
                        player.sendMessage(ZaraanCore.color("&a上架成功!售价 &6"
                                + (plugin.eco() != null ? plugin.eco().format(price) : price)));
                    });
                } else if (slot == 49) {
                    openMarketMenu(player, 0);
                }
            }
            case MARKET_MINE -> {
                if (slot <= 44) {
                    List<MarketManager.Listing> mine = new ArrayList<>();
                    for (MarketManager.Listing l : plugin.market().all()) {
                        if (l.seller.equals(player.getUniqueId())) mine.add(l);
                    }
                    int idx = holder.page * 45 + slot;
                    if (idx >= mine.size()) return;
                    MarketManager.Listing l = mine.get(idx);
                    player.getInventory().addItem(l.item.clone());
                    plugin.market().remove(l);
                    player.sendMessage(ZaraanCore.color("&a已撤回商品"));
                    openMyMarketMenu(player, holder.page);
                } else if (slot == 49) {
                    openMarketMenu(player, 0);
                }
            }
            case PAY_SELECT -> {
                if (slot <= 35 && !plainName.isEmpty()) {
                    String target = plainName;
                    player.closeInventory();
                    plugin.payPending().put(player.getUniqueId(), target);
                    player.sendMessage(ZaraanCore.color("&6转账给 &f" + target));
                    player.sendMessage(ZaraanCore.color("&e请在聊天栏输入转账金额,输入 &c0 &e取消"));
                } else if (slot == 49) {
                    player.closeInventory();
                }
            }
        }
    }
}
