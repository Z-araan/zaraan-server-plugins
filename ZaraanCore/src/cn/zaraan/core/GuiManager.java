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

    public enum MenuType { TPA, PAY_SELECT, HOME, MSG_SELECT }

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
