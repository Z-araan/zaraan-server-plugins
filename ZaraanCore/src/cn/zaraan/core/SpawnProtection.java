package cn.zaraan.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 出生点保护: 防破坏 / 防放置 / 防伤害 / 阻止怪物生成。
 * 区域 = 世界出生点为中心的正方形/圆形, 参数可配置。
 */
public class SpawnProtection implements Listener {

    private final ZaraanCore plugin;

    public SpawnProtection(ZaraanCore plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("spawn-protection.enabled", true);
    }

    private World world() {
        String w = plugin.getConfig().getString("spawn-protection.world", "world");
        World world = plugin.getServer().getWorld(w);
        return world != null ? world : (plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0));
    }

    /** 是否在保护区内(以出生点为中心的立方体范围) */
    public boolean inRegion(Location loc) {
        if (!enabled() || loc == null) return false;
        World w = world();
        if (w == null || !loc.getWorld().equals(w)) return false;
        Location spawn = w.getSpawnLocation();
        int r = plugin.getConfig().getInt("spawn-protection.radius", 32);
        return Math.abs(loc.getBlockX() - spawn.getBlockX()) <= r
                && Math.abs(loc.getBlockZ() - spawn.getBlockZ()) <= r;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("spawn-protection.prevent-build", true)) return;
        Player p = event.getPlayer();
        if (p.hasPermission("zaraancore.buildspawn")) return;
        if (inRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
            p.sendMessage(ZaraanCore.color(plugin.getConfig().getString("spawn-protection.message", "&c这里是出生点保护区,不能破坏方块!")));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("spawn-protection.prevent-build", true)) return;
        Player p = event.getPlayer();
        if (p.hasPermission("zaraancore.buildspawn")) return;
        if (inRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
            p.sendMessage(ZaraanCore.color(plugin.getConfig().getString("spawn-protection.message", "&c这里是出生点保护区,不能放置方块!")));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("spawn-protection.prevent-damage", true)) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getEntity().hasPermission("zaraancore.buildspawn")) return;
        if (inRegion(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("spawn-protection.prevent-mob-spawn", true)) return;
        EntityType type = event.getEntityType();
        // 只拦怪物, 不拦动物/村民等
        if (!isMonster(type)) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG) return;
        if (inRegion(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isMonster(EntityType type) {
        switch (type) {
            case ZOMBIE: case SKELETON: case CREEPER: case SPIDER: case ENDERMAN:
            case WITCH: case SLIME: case PHANTOM: case DROWNED: case HUSK:
            case STRAY: case CAVE_SPIDER: case ZOMBIE_VILLAGER: case PILLAGER:
            case VINDICATOR: case EVOKER: case RAVAGER: case GUARDIAN:
            case ELDER_GUARDIAN: case SHULKER: case SILVERFISH: case ENDERMITE:
            case BLAZE: case GHAST: case MAGMA_CUBE: case WITHER_SKELETON:
            case PIGLIN: case PIGLIN_BRUTE: case HOGLIN: case ZOGLIN:
            case ZOMBIFIED_PIGLIN: case WARDEN: case BREEZE: case BOGGED:
                return true;
            default:
                return false;
        }
    }
}
