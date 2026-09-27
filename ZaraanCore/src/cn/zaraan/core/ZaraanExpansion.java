/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  net.milkbowl.vault.economy.Economy
 *  org.bukkit.OfflinePlayer
 *  org.jetbrains.annotations.NotNull
 */
package cn.zaraan.core;

import cn.zaraan.core.ZaraanCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class ZaraanExpansion
extends PlaceholderExpansion {
    private final ZaraanCore plugin;

    public ZaraanExpansion(ZaraanCore zaraanCore) {
        this.plugin = zaraanCore;
    }

    @NotNull
    public String getIdentifier() {
        return "zaraancore";
    }

    @NotNull
    public String getAuthor() {
        return "zaraan";
    }

    @NotNull
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String string) {
        if (offlinePlayer == null) {
            return "";
        }
        Economy economy = this.plugin.eco();
        double d = economy == null ? 0.0 : economy.getBalance(offlinePlayer);
        switch (string.toLowerCase()) {
            case "balance": {
                return economy == null ? "0" : economy.format(d);
            }
            case "balance_raw": {
                return String.valueOf((long)d);
            }
            case "balance_short": {
                return this.shortFormat(d);
            }
        }
        return null;
    }

    private String shortFormat(double d) {
        if (d >= 1.0E9) {
            return String.format("%.1fB", d / 1.0E9);
        }
        if (d >= 1000000.0) {
            return String.format("%.1fM", d / 1000000.0);
        }
        if (d >= 1000.0) {
            return String.format("%.1fk", d / 1000.0);
        }
        return String.valueOf((long)d);
    }
}

