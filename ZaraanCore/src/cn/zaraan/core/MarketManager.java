package cn.zaraan.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 玩家交易市场(菜单化): 上架 / 浏览 / 购买 / 撤回
 * 数据存 market.yml, 物品通过 Bukkit 原生 ItemStack 序列化保存。
 */
public class MarketManager {

    public static class Listing {
        public int id;
        public UUID seller;
        public String sellerName;
        public ItemStack item;
        public double price;
        public long time;
    }

    private final ZaraanCore plugin;
    private final List<Listing> listings = new ArrayList<>();
    private int nextId = 1;

    public MarketManager(ZaraanCore plugin) {
        this.plugin = plugin;
        load();
    }

    public List<Listing> all() {
        return listings;
    }

    public int nextId() {
        return nextId++;
    }

    /** 上架物品 */
    public boolean add(Player seller, ItemStack item, double price) {
        if (price <= 0) return false;
        if (price > plugin.getConfig().getDouble("market.max-price", 1000000)) return false;
        Listing l = new Listing();
        l.id = nextId();
        l.seller = seller.getUniqueId();
        l.sellerName = seller.getName();
        l.item = item.clone();
        l.item.setAmount(1);
        l.price = price;
        l.time = System.currentTimeMillis();
        listings.add(l);
        save();
        return true;
    }

    public Listing byId(int id) {
        for (Listing l : listings) {
            if (l.id == id) return l;
        }
        return null;
    }

    public void remove(Listing l) {
        listings.remove(l);
        save();
    }

    /** 购买: 检查余额 → 扣钱 → 给物品 → 给卖家打钱 */
    public String buy(Player buyer, Listing l) {
        if (l == null) return "该商品不存在";
        if (l.seller != null && l.seller.equals(buyer.getUniqueId())) return "不能购买自己的东西";
        if (plugin.eco() == null) return "经济系统未就绪";
        if (!plugin.eco().has(buyer, l.price)) return "金币不足(需要 " + plugin.eco().format(l.price) + ")";
        if (buyer.getInventory().firstEmpty() < 0) return "背包已满,先清出空位";
        plugin.eco().withdrawPlayer(buyer, l.price);
        // 给卖家打钱(扣 5% 手续费)
        double tax = plugin.getConfig().getDouble("market.tax-percent", 5) / 100.0;
        double income = Math.max(0, l.price * (1 - tax));
        if (plugin.eco().getBalance(Bukkit.getOfflinePlayer(l.seller)) >= 0) {
            plugin.eco().depositPlayer(Bukkit.getOfflinePlayer(l.seller), income);
        }
        buyer.getInventory().addItem(l.item.clone());
        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) {
            seller.sendMessage(ZaraanCore.color("&a[市场] &f有人买了你的 &e" + itemName(l.item)
                    + " &a,收入 &6" + plugin.eco().format(income)));
        }
        remove(l);
        return null;
    }

    public int countBySeller(UUID uuid) {
        int n = 0;
        for (Listing l : listings) {
            if (l.seller.equals(uuid)) n++;
        }
        return n;
    }

    public static String itemName(ItemStack item) {
        if (item == null) return "未知物品";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    // ==================== 存储 ====================

    @SuppressWarnings("unchecked")
    public void load() {
        listings.clear();
        File f = new File(plugin.getDataFolder(), "market.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("listings");
        if (root == null) return;
        int maxId = 0;
        for (String key : root.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                Listing l = new Listing();
                l.id = id;
                l.seller = UUID.fromString(s.getString("seller", ""));
                l.sellerName = s.getString("seller-name", "?");
                Object item = s.get("item");
                if (!(item instanceof ItemStack)) continue;
                l.item = (ItemStack) item;
                l.price = s.getDouble("price", 0);
                l.time = s.getLong("time", 0);
                listings.add(l);
                maxId = Math.max(maxId, id);
            } catch (Exception ignored) {
            }
        }
        nextId = maxId + 1;
    }

    public void save() {
        File f = new File(plugin.getDataFolder(), "market.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (Listing l : listings) {
            String path = "listings." + l.id;
            y.set(path + ".seller", l.seller.toString());
            y.set(path + ".seller-name", l.sellerName);
            y.set(path + ".item", l.item);
            y.set(path + ".price", l.price);
            y.set(path + ".time", l.time);
        }
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("市场数据保存失败: " + e.getMessage());
        }
    }
}
