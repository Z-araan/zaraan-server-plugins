/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.jetbrains.annotations.NotNull
 */
package dev.novaskin.command;

import dev.novaskin.NovaSkin;
import dev.novaskin.data.PlayerSkinProfile;
import dev.novaskin.data.SkinData;
import dev.novaskin.data.SkinDatabase;
import dev.novaskin.util.MessageUtil;
import dev.novaskin.util.TextureUtil;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class SkinCommand
implements CommandExecutor {
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final String DEFAULT_STEVE_SKIN_URL = "http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810";
    private final NovaSkin plugin;

    public SkinCommand(@NotNull NovaSkin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub;
        if (args.length == 0) {
            this.usage(sender);
            return true;
        }
        switch (sub = args[0].toLowerCase(Locale.ROOT)) {
            case "set": {
                this.handleSet(sender, args);
                break;
            }
            case "reset": {
                this.handleReset(sender, args);
                break;
            }
            case "copy": {
                this.handleCopy(sender, args);
                break;
            }
            case "info": {
                this.handleInfo(sender, args);
                break;
            }
            case "update": {
                this.handleUpdate(sender, args);
                break;
            }
            case "clear": {
                this.handleClear(sender, args);
                break;
            }
            case "history": {
                this.handleHistory(sender, args);
                break;
            }
            case "alias": {
                this.handleAlias(sender, args);
                break;
            }
            case "cape": {
                this.handleCape(sender, args);
                break;
            }
            case "upload": {
                this.handleUpload(sender, args);
                break;
            }
            case "reload": {
                this.handleReload(sender);
                break;
            }
            default: {
                this.usage(sender);
            }
        }
        return true;
    }

    private void handleSet(@NotNull CommandSender sender, @NotNull String[] args) {
        UUID uUID;
        CompletableFuture<SkinData> fetch;
        String spec;
        String targetName;
        if (args.length < 2) {
            sender.sendMessage(this.plugin.getMessageUtil().render("usage-line", MessageUtil.map("usage", "/skin set [target] <skin|url:URL>", "desc", "Apply a skin")));
            return;
        }
        if (!this.hasPermission(sender, "novaskin.use")) {
            return;
        }
        if (args.length == 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(this.plugin.getMessageUtil().render("no-permission"));
                return;
            }
            Player p = (Player)sender;
            targetName = p.getName();
            spec = args[1];
        } else {
            targetName = args[1];
            spec = args[2];
            if (!sender.getName().equalsIgnoreCase(targetName) && !this.hasPermission(sender, "novaskin.others")) {
                return;
            }
        }
        int cd = this.cooldownOrNotify(sender);
        if (cd > 0) {
            return;
        }
        String specLower = spec.toLowerCase(Locale.ROOT);
        if (specLower.equals("cape:remove") || specLower.equals("cape:none")) {
            if (!this.hasPermission(sender, "novaskin.cape")) {
                return;
            }
            this.stripCapeFromStored(sender, targetName);
            return;
        }
        if (specLower.startsWith("url:")) {
            if (!this.hasPermission(sender, "novaskin.url")) {
                return;
            }
            String url = spec.substring(4);
            sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-start", MessageUtil.map("skin", url)));
            fetch = this.plugin.getSkinFetcher().fetchFromURL(url);
        } else {
            sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-start", MessageUtil.map("skin", spec)));
            fetch = this.plugin.getSkinFetcher().fetchByUsername(spec);
        }
        if (sender instanceof Player) {
            Player p = (Player)sender;
            uUID = p.getUniqueId();
        } else {
            uUID = null;
        }
        UUID actor = uUID;
        fetch.whenComplete((data, err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (err != null || data == null) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-failed", MessageUtil.map("reason", err == null ? "unknown" : err.getMessage())));
                return;
            }
            this.applyResolvedSkin(sender, targetName, (SkinData)data, actor);
        }));
    }

    private void applyResolvedSkin(@NotNull CommandSender sender, @NotNull String targetName, @NotNull SkinData data, UUID actor) {
        Player onlineTarget = Bukkit.getPlayerExact((String)targetName);
        if (onlineTarget != null) {
            this.plugin.getSkinManager().applySkin(onlineTarget, data, actor);
            if (sender.equals((Object)onlineTarget)) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-set-self", MessageUtil.map("skin", data.name())));
            } else {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-set-other", MessageUtil.map("target", targetName, "skin", data.name())));
                onlineTarget.sendMessage(this.plugin.getMessageUtil().render("skin-set-other-notify", MessageUtil.map("skin", data.name(), "actor", sender.getName())));
            }
            return;
        }
        this.resolveOfflineUuid(targetName).whenComplete((opt, err) -> {
            if (err != null || opt == null || opt.isEmpty()) {
                sender.sendMessage(this.plugin.getMessageUtil().render("player-not-found", MessageUtil.map("name", targetName)));
                return;
            }
            this.plugin.getSkinManager().applySkinOffline((UUID)opt.get(), targetName, data, actor);
            sender.sendMessage(this.plugin.getMessageUtil().render("skin-set-other", MessageUtil.map("target", targetName, "skin", data.name())));
        });
    }

    private void handleReset(@NotNull CommandSender sender, @NotNull String[] args) {
        String targetName;
        if (args.length >= 2) {
            targetName = args[1];
        } else if (sender instanceof Player) {
            Player p = (Player)sender;
            targetName = p.getName();
        } else {
            targetName = null;
        }
        if (targetName == null) {
            this.usage(sender);
            return;
        }
        if (!sender.getName().equalsIgnoreCase(targetName) && !this.hasPermission(sender, "novaskin.others")) {
            return;
        }
        if (!this.hasPermission(sender, "novaskin.use")) {
            return;
        }
        Player onlineTarget = Bukkit.getPlayerExact((String)targetName);
        if (onlineTarget == null) {
            this.resolveOfflineUuid(targetName).whenComplete((opt, err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                if (opt == null || opt.isEmpty()) {
                    sender.sendMessage(this.plugin.getMessageUtil().render("player-not-found", MessageUtil.map("name", targetName)));
                    return;
                }
                this.plugin.getDatabase().clearSkin((UUID)opt.get()).thenRun(() -> sender.sendMessage(this.plugin.getMessageUtil().render("skin-reset-other", MessageUtil.map("target", targetName))));
            }));
            return;
        }
        UUID u = onlineTarget.getUniqueId();
        this.plugin.getMojangAPI().fetchProfile(u, targetName).whenComplete((data, err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (err != null || data == null) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-failed", MessageUtil.map("reason", err == null ? "unknown" : err.getMessage())));
                return;
            }
            this.plugin.getSkinManager().installProfile(onlineTarget, (SkinData)data);
            this.plugin.getSkinManager().refreshFor(onlineTarget);
            this.plugin.getDatabase().clearSkin(u).thenRun(() -> {
                Player p;
                if (sender instanceof Player && (p = (Player)sender).getName().equalsIgnoreCase(targetName)) {
                    sender.sendMessage(this.plugin.getMessageUtil().render("skin-reset-self"));
                } else {
                    sender.sendMessage(this.plugin.getMessageUtil().render("skin-reset-other", MessageUtil.map("target", targetName)));
                }
            });
        }));
    }

    private void handleCopy(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!this.hasPermission(sender, "novaskin.copy")) {
            return;
        }
        if (args.length < 3) {
            this.usage(sender);
            return;
        }
        String source = args[1];
        String target = args[2];
        this.plugin.getSkinFetcher().fetchByUsername(source).whenComplete((data, err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            UUID uUID;
            if (err != null || data == null) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-failed", MessageUtil.map("reason", err == null ? "unknown" : err.getMessage())));
                return;
            }
            if (sender instanceof Player) {
                Player p = (Player)sender;
                uUID = p.getUniqueId();
            } else {
                uUID = null;
            }
            this.applyResolvedSkin(sender, target, (SkinData)data, uUID);
            sender.sendMessage(this.plugin.getMessageUtil().render("skin-copy-done", MessageUtil.map("source", source, "target", target)));
        }));
    }

    private void handleInfo(@NotNull CommandSender sender, @NotNull String[] args) {
        String targetName;
        if (args.length >= 2) {
            targetName = args[1];
        } else if (sender instanceof Player) {
            Player p = (Player)sender;
            targetName = p.getName();
        } else {
            targetName = null;
        }
        if (targetName == null) {
            this.usage(sender);
            return;
        }
        ((CompletableFuture<Optional<PlayerSkinProfile>>)this.resolveOfflineUuid(targetName).thenCompose((Optional<UUID> opt) -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return this.plugin.getDatabase().loadSkin((UUID)opt.get());
        })).whenComplete((Optional<PlayerSkinProfile> opt, Throwable err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (opt == null || opt.isEmpty() || !((PlayerSkinProfile)opt.get()).hasSkin()) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-info", MessageUtil.map("player", targetName, "name", "-", "source", "DEFAULT", "cape", "no", "setby", "-", "setat", "-")));
                return;
            }
            PlayerSkinProfile p = (PlayerSkinProfile)opt.get();
            SkinData d = p.skin();
            String setBy = p.setBy() == null ? "console" : Bukkit.getOfflinePlayer((UUID)p.setBy()).getName();
            sender.sendMessage(this.plugin.getMessageUtil().render("skin-info", MessageUtil.map("player", targetName, "name", d.name(), "source", d.source(), "cape", d.hasCape() ? "yes" : "no", "setby", setBy == null ? "-" : setBy, "setat", DATE_FMT.format(new Date(p.setAt())))));
        }));
    }

    private void handleUpdate(@NotNull CommandSender sender, @NotNull String[] args) {
        String targetName;
        if (!this.hasPermission(sender, "novaskin.update")) {
            return;
        }
        if (args.length >= 2) {
            targetName = args[1];
        } else if (sender instanceof Player) {
            Player p = (Player)sender;
            targetName = p.getName();
        } else {
            targetName = null;
        }
        if (targetName == null) {
            this.usage(sender);
            return;
        }
        this.plugin.getSkinCache().invalidate(targetName);
        this.plugin.getSkinFetcher().fetchByUsername(targetName, true).whenComplete((data, err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            UUID uUID;
            if (err != null) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-failed", MessageUtil.map("reason", err.getMessage())));
                return;
            }
            if (sender instanceof Player) {
                Player p = (Player)sender;
                uUID = p.getUniqueId();
            } else {
                uUID = null;
            }
            this.applyResolvedSkin(sender, targetName, (SkinData)data, uUID);
        }));
    }

    private void handleClear(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            this.usage(sender);
            return;
        }
        if (!this.hasPermission(sender, "novaskin.others")) {
            return;
        }
        String name = args[1];
        this.resolveOfflineUuid(name).whenComplete((opt, err) -> {
            if (opt == null || opt.isEmpty()) {
                sender.sendMessage(this.plugin.getMessageUtil().render("player-not-found", MessageUtil.map("name", name)));
                return;
            }
            this.plugin.getDatabase().clearSkin((UUID)opt.get()).thenRun(() -> sender.sendMessage(this.plugin.getMessageUtil().render("skin-reset-other", MessageUtil.map("target", name))));
        });
    }

    private void handleHistory(@NotNull CommandSender sender, @NotNull String[] args) {
        String perm;
        Player p;
        String targetName;
        if (args.length >= 2) {
            targetName = args[1];
        } else if (sender instanceof Player) {
            Player p2 = (Player)sender;
            targetName = p2.getName();
        } else {
            targetName = null;
        }
        if (targetName == null) {
            this.usage(sender);
            return;
        }
        boolean self = sender instanceof Player && (p = (Player)sender).getName().equalsIgnoreCase(targetName);
        String string = perm = self ? "novaskin.history" : "novaskin.history.others";
        if (!this.hasPermission(sender, perm)) {
            return;
        }
        ((CompletableFuture<List<SkinDatabase.HistoryEntry>>)this.resolveOfflineUuid(targetName).thenCompose((Optional<UUID> opt) -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            return this.plugin.getDatabase().getHistory((UUID)opt.get(), 10);
        })).whenComplete((List<SkinDatabase.HistoryEntry> entries, Throwable err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            sender.sendMessage(this.plugin.getMessageUtil().render("skin-history-header", MessageUtil.map("player", targetName)));
            if (entries == null || entries.isEmpty()) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-history-empty"));
                return;
            }
            int i = 1;
            for (SkinDatabase.HistoryEntry e : entries) {
                sender.sendMessage(this.plugin.getMessageUtil().render("skin-history", MessageUtil.map("index", String.valueOf(i++), "name", "entry", "source", e.source(), "date", DATE_FMT.format(new Date(e.appliedAt())))));
            }
        }));
    }

    private void handleAlias(@NotNull CommandSender sender, @NotNull String[] args) {
        String op;
        if (args.length < 2) {
            this.usage(sender);
            return;
        }
        switch (op = args[1].toLowerCase(Locale.ROOT)) {
            case "set": {
                if (!this.hasPermission(sender, "novaskin.alias.manage")) {
                    return;
                }
                if (args.length < 4) {
                    this.usage(sender);
                    return;
                }
                String name = args[2];
                String skinName = args[3];
                this.plugin.getSkinFetcher().fetchByUsername(skinName).whenComplete((d, err) -> {
                    if (err != null || d == null) {
                        sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-failed", MessageUtil.map("reason", err == null ? "unknown" : err.getMessage())));
                        return;
                    }
                    this.plugin.getDatabase().saveAlias(name, (SkinData)d).thenRun(() -> sender.sendMessage(this.plugin.getMessageUtil().render("alias-created", MessageUtil.map("name", name))));
                });
                break;
            }
            case "remove": {
                if (!this.hasPermission(sender, "novaskin.alias.manage")) {
                    return;
                }
                if (args.length < 3) {
                    this.usage(sender);
                    return;
                }
                String name = args[2];
                this.plugin.getDatabase().deleteAlias(name).thenRun(() -> sender.sendMessage(this.plugin.getMessageUtil().render("alias-removed", MessageUtil.map("name", name))));
                break;
            }
            case "list": {
                this.plugin.getDatabase().listAliases().thenAccept(list -> {
                    sender.sendMessage(this.plugin.getMessageUtil().render("alias-list-header"));
                    if (list.isEmpty()) {
                        sender.sendMessage(this.plugin.getMessageUtil().render("alias-list-empty"));
                        return;
                    }
                    for (String a : list) {
                        sender.sendMessage(this.plugin.getMessageUtil().render("alias-list-entry", MessageUtil.map("name", a)));
                    }
                });
                break;
            }
            case "apply": {
                if (!this.hasPermission(sender, "novaskin.alias")) {
                    return;
                }
                if (args.length < 4) {
                    this.usage(sender);
                    return;
                }
                String targetName = args[2];
                String name = args[3];
                this.plugin.getDatabase().loadAlias(name).whenComplete((opt, err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                    UUID uUID;
                    if (opt == null || opt.isEmpty()) {
                        sender.sendMessage(this.plugin.getMessageUtil().render("alias-not-found", MessageUtil.map("name", name)));
                        return;
                    }
                    SkinData skinData = (SkinData)opt.get();
                    if (sender instanceof Player) {
                        Player p = (Player)sender;
                        uUID = p.getUniqueId();
                    } else {
                        uUID = null;
                    }
                    this.applyResolvedSkin(sender, targetName, skinData, uUID);
                }));
                break;
            }
            default: {
                this.usage(sender);
            }
        }
    }

    private void handleCape(@NotNull CommandSender sender, @NotNull String[] args) {
        String op;
        if (!this.hasPermission(sender, "novaskin.cape")) {
            return;
        }
        if (args.length < 2) {
            this.usage(sender);
            return;
        }
        switch (op = args[1].toLowerCase(Locale.ROOT)) {
            case "set": {
                String targetName;
                if (args.length < 3) {
                    this.usage(sender);
                    return;
                }
                String capeSource = args[2];
                if (args.length >= 4) {
                    targetName = args[3];
                    if (sender instanceof Player) {
                        Player p = (Player)sender;
                        if (!p.getName().equalsIgnoreCase(targetName) && !this.hasPermission(sender, "novaskin.others")) {
                            return;
                        }
                    }
                } else {
                    if (!(sender instanceof Player)) {
                        this.usage(sender);
                        return;
                    }
                    Player p = (Player)sender;
                    targetName = p.getName();
                }
                CompletionStage<String> capeUrlFuture = capeSource.toLowerCase(Locale.ROOT).startsWith("url:") ? CompletableFuture.completedFuture(capeSource.substring(4)) : this.plugin.getSkinFetcher().fetchByUsername(capeSource).thenApply(d -> TextureUtil.extractCapeUrl(d.value()).orElseThrow(() -> new RuntimeException(capeSource + " has no cape")));
                String finalTargetName = targetName;
                capeUrlFuture.whenComplete((capeUrl, capeErr) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                    if (capeErr != null) {
                        sender.sendMessage(this.plugin.getMessageUtil().render("skin-fetch-failed", MessageUtil.map("reason", capeErr.getMessage())));
                        return;
                    }
                    this.resolveOnlineOrOffline(sender, finalTargetName, (targetUuid, isOnline) -> this.isPremium(finalTargetName, (UUID)targetUuid).whenComplete((premium, premErr) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                        if (premErr != null || premium == null || !premium.booleanValue()) {
                            sender.sendMessage(this.plugin.getMessageUtil().render("cape-premium-only", MessageUtil.map("target", finalTargetName)));
                            return;
                        }
                        this.plugin.getDatabase().loadSkin((UUID)targetUuid).whenComplete((profOpt, dbErr) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.applyCapeWithResolvedSkin(sender, finalTargetName, (UUID)targetUuid, (String)capeUrl, (Optional<PlayerSkinProfile>)profOpt)));
                    })));
                }));
                break;
            }
            case "remove": {
                String targetName;
                if (args.length >= 3) {
                    Player p;
                    targetName = args[2];
                    if (sender instanceof Player && !(p = (Player)sender).getName().equalsIgnoreCase(targetName) && !this.hasPermission(sender, "novaskin.others")) {
                        return;
                    }
                } else {
                    if (!(sender instanceof Player)) {
                        this.usage(sender);
                        return;
                    }
                    Player p = (Player)sender;
                    targetName = p.getName();
                }
                this.stripCapeFromStored(sender, targetName);
                break;
            }
            default: {
                this.usage(sender);
            }
        }
    }

    private void applyCapeWithResolvedSkin(@NotNull CommandSender sender, @NotNull String finalTargetName, @NotNull UUID targetUuid, @NotNull String capeUrl, Optional<PlayerSkinProfile> profOpt) {
        String skinUrl = null;
        Player onlinePlayer = Bukkit.getPlayer((UUID)targetUuid);
        if (onlinePlayer != null) {
            skinUrl = onlinePlayer.getPlayerProfile().getProperties().stream().filter(pr -> pr.getName().equals("textures")).findFirst().flatMap(pr -> TextureUtil.extractSkinUrl(pr.getValue())).orElse(null);
        }
        if (skinUrl == null && profOpt != null && profOpt.isPresent() && profOpt.get().hasSkin()) {
            skinUrl = TextureUtil.extractSkinUrl(profOpt.get().skin().value()).orElse(null);
        }
        if (skinUrl != null) {
            this.finishCapeApply(sender, finalTargetName, targetUuid, capeUrl, skinUrl, profOpt);
            return;
        }
        this.plugin.getMojangAPI().fetchUuid(finalTargetName).thenCompose(mojangUuid -> this.plugin.getMojangAPI().fetchProfile((UUID)mojangUuid, finalTargetName)).whenComplete((SkinData mojangData, Throwable err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            String resolved = null;
            if (err == null && mojangData != null) {
                resolved = TextureUtil.extractSkinUrl(mojangData.value()).orElse(null);
            }
            if (resolved == null) {
                resolved = DEFAULT_STEVE_SKIN_URL;
            }
            this.finishCapeApply(sender, finalTargetName, targetUuid, capeUrl, resolved, profOpt);
        }));
    }

    private void finishCapeApply(@NotNull CommandSender sender, @NotNull String finalTargetName, @NotNull UUID targetUuid, @NotNull String capeUrl, @NotNull String skinUrl, Optional<PlayerSkinProfile> profOpt) {
        UUID uUID;
        String newValue = TextureUtil.buildUnsignedValue(skinUrl, capeUrl);
        String skinSource = "CUSTOM";
        String skinName = "custom";
        if (profOpt != null && profOpt.isPresent() && profOpt.get().hasSkin()) {
            skinSource = profOpt.get().skin().source();
            skinName = profOpt.get().skin().name();
        }
        SkinData updated = new SkinData(newValue, "", null, null, skinSource, System.currentTimeMillis(), skinName);
        if (sender instanceof Player) {
            Player p = (Player)sender;
            uUID = p.getUniqueId();
        } else {
            uUID = null;
        }
        UUID actor = uUID;
        Player onlinePlayer = Bukkit.getPlayer((UUID)targetUuid);
        if (onlinePlayer != null) {
            this.plugin.getSkinManager().applySkin(onlinePlayer, updated, actor);
        } else {
            this.plugin.getSkinManager().applySkinOffline(targetUuid, finalTargetName, updated, actor);
        }
        sender.sendMessage(this.plugin.getMessageUtil().render("cape-attached", MessageUtil.map("target", finalTargetName)));
    }

    private void stripCapeFromStored(@NotNull CommandSender sender, @NotNull String targetName) {
        this.resolveOnlineOrOffline(sender, targetName, (targetUuid, isOnline) -> this.plugin.getDatabase().loadSkin((UUID)targetUuid).whenComplete((profOpt, dbErr) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            UUID actor;
            String newValue;
            boolean hasCapeInDb = profOpt != null && profOpt.isPresent() && ((PlayerSkinProfile)profOpt.get()).hasSkin() && TextureUtil.hasCapeInValue(((PlayerSkinProfile)profOpt.get()).skin().value());
            Player onlinePlayer = Bukkit.getPlayer((UUID)targetUuid);
            boolean hasCapeInLive = false;
            String liveTextureValue = null;
            if (onlinePlayer != null) {
                liveTextureValue = onlinePlayer.getPlayerProfile().getProperties().stream().filter(pr -> pr.getName().equals("textures")).findFirst().map(pr -> pr.getValue()).orElse(null);
                boolean bl = hasCapeInLive = liveTextureValue != null && TextureUtil.hasCapeInValue(liveTextureValue);
            }
            if (!hasCapeInDb && !hasCapeInLive) {
                sender.sendMessage(this.plugin.getMessageUtil().render("cape-none"));
                return;
            }
            String sourceValue = liveTextureValue;
            if (sourceValue == null && profOpt != null && profOpt.isPresent() && ((PlayerSkinProfile)profOpt.get()).hasSkin()) {
                sourceValue = ((PlayerSkinProfile)profOpt.get()).skin().value();
            }
            String string = newValue = sourceValue == null ? null : (String)TextureUtil.stripCapeFromValue(sourceValue).orElse(null);
            if (newValue == null) {
                sender.sendMessage(this.plugin.getMessageUtil().render("cape-remove-failed", MessageUtil.map("target", targetName, "reason", "Cannot resolve skin texture")));
                return;
            }
            String skinSource = "CUSTOM";
            String skinName = "custom";
            if (profOpt != null && profOpt.isPresent() && ((PlayerSkinProfile)profOpt.get()).hasSkin()) {
                skinSource = ((PlayerSkinProfile)profOpt.get()).skin().source();
                skinName = ((PlayerSkinProfile)profOpt.get()).skin().name();
            }
            SkinData stripped = new SkinData(newValue, "", null, null, skinSource, System.currentTimeMillis(), skinName);
            UUID v2;
            if (sender instanceof Player) {
                Player p = (Player)sender;
                v2 = p.getUniqueId();
                actor = v2;
            } else {
                v2 = null;
                actor = null;
            }
            if (onlinePlayer != null) {
                this.plugin.getSkinManager().applySkin(onlinePlayer, stripped, actor);
            } else {
                this.plugin.getSkinManager().applySkinOffline((UUID)targetUuid, targetName, stripped, actor);
            }
            sender.sendMessage(this.plugin.getMessageUtil().render("cape-removed", MessageUtil.map("target", targetName)));
        })));
    }

    private void resolveOnlineOrOffline(@NotNull CommandSender sender, @NotNull String name, @NotNull BiConsumer<UUID, Boolean> callback) {
        Player online = Bukkit.getPlayer((String)name);
        if (online != null) {
            callback.accept(online.getUniqueId(), true);
            return;
        }
        this.resolveOfflineUuid(name).whenComplete((opt, err) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (opt == null || opt.isEmpty()) {
                sender.sendMessage(this.plugin.getMessageUtil().render("player-not-found", MessageUtil.map("name", name)));
                return;
            }
            callback.accept((UUID)opt.get(), false);
        }));
    }

    private void handleUpload(@NotNull CommandSender sender, @NotNull String[] args) {
        UUID targetUuid;
        if (!this.hasPermission(sender, "novaskin.upload")) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.getMessageUtil().render("no-permission"));
            return;
        }
        Player admin = (Player)sender;
        if (args.length < 2) {
            this.usage(sender);
            return;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayerExact((String)targetName);
        UUID uUID = targetUuid = target != null ? target.getUniqueId() : null;
        if (targetUuid == null) {
            sender.sendMessage(this.plugin.getMessageUtil().render("player-not-found", MessageUtil.map("name", targetName)));
            return;
        }
        admin.sendMessage(this.plugin.getMessageUtil().render("upload-prompt"));
        this.plugin.getUploadSession().start(admin, targetUuid, targetName, (value, signature) -> {
            SkinData data = new SkinData((String)value, (String)signature, null, null, "UPLOAD", System.currentTimeMillis(), "upload");
            Player t = Bukkit.getPlayer((UUID)targetUuid);
            if (t != null) {
                this.plugin.getSkinManager().applySkin(t, data, admin.getUniqueId());
            } else {
                this.plugin.getSkinManager().applySkinOffline(targetUuid, targetName, data, admin.getUniqueId());
            }
            admin.sendMessage(this.plugin.getMessageUtil().render("upload-done"));
        });
    }

    private void handleReload(@NotNull CommandSender sender) {
        if (!this.hasPermission(sender, "novaskin.reload")) {
            return;
        }
        this.plugin.reloadAll();
        sender.sendMessage(this.plugin.getMessageUtil().render("reload-done"));
    }

    private boolean hasPermission(@NotNull CommandSender sender, @NotNull String node) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (sender.hasPermission(node) || sender.hasPermission("novaskin.admin")) {
            return true;
        }
        sender.sendMessage(this.plugin.getMessageUtil().render("no-permission"));
        return false;
    }

    private int cooldownOrNotify(@NotNull CommandSender sender) {
        if (!(sender instanceof Player)) {
            return 0;
        }
        Player p = (Player)sender;
        if (p.hasPermission("novaskin.bypass.cooldown") || p.hasPermission("novaskin.admin")) {
            return 0;
        }
        int remaining = this.plugin.getSkinManager().remainingCooldown(p.getUniqueId());
        if (remaining > 0) {
            sender.sendMessage(this.plugin.getMessageUtil().render("cooldown-active", MessageUtil.map("seconds", String.valueOf(remaining))));
        }
        return remaining;
    }

    private CompletableFuture<Boolean> isPremium(@NotNull String name, @NotNull UUID serverUuid) {
        // [zaraan补丁] 离线模式服务器无法通过Mojang UUID校验, 直接放行披风功能
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    private CompletableFuture<Optional<UUID>> resolveOfflineUuid(@NotNull String name) {
        Player online = Bukkit.getPlayerExact((String)name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }
        return this.plugin.getDatabase().findUuidByName(name).thenCompose(opt -> {
            if (opt.isPresent()) {
                return CompletableFuture.completedFuture(opt);
            }
            return ((CompletableFuture)this.plugin.getMojangAPI().fetchUuid(name).thenApply(Optional::of)).exceptionally(t -> Optional.empty());
        });
    }

    private void usage(@NotNull CommandSender sender) {
        String[][] lines;
        sender.sendMessage(this.plugin.getMessageUtil().render("usage-header"));
        for (String[] l : lines = new String[][]{{"/skin set [player] <skin|url:URL>", "Apply a skin (includes cape if they have one)"}, {"/skin set [player] cape:remove", "Remove cape from current skin"}, {"/skin reset [player]", "Restore default Mojang skin"}, {"/skin copy <source> <target>", "Copy a skin between players"}, {"/skin info [player]", "Show skin info"}, {"/skin update [player]", "Force re-fetch from Mojang"}, {"/skin clear <player>", "Delete stored skin"}, {"/skin history [player]", "Show recent skin changes"}, {"/skin alias set|remove|list|apply", "Manage saved skins"}, {"/skin cape set <username|url:URL> [player]", "Attach cape from Mojang user or URL"}, {"/skin cape remove [player]", "Detach cape only"}, {"/skin upload <player>", "Paste base64 manually"}, {"/skin reload", "Reload configuration"}}) {
            sender.sendMessage(this.plugin.getMessageUtil().render("usage-line", MessageUtil.map("usage", l[0], "desc", l[1])));
        }
    }

    @NotNull
    public static Component placeholderDate(long ms) {
        return Component.text((String)DATE_FMT.format(new Date(ms)));
    }
}
