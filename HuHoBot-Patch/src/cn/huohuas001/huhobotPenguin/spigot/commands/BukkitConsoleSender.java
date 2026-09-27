package cn.huohuas001.huhobotPenguin.spigot.commands;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import cn.huohuas001.bot.provider.HExecution;
import cn.huohuas001.huhobotPenguin.spigot.HuHoBotSpigot;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Patched BukkitConsoleSender for HuHoBot-Penguin 1.4.0
 * Original logic preserved; every captured message is cleaned of
 * Minecraft section color codes and ANSI escape sequences so that
 * command output sent back to the QQ group never contains raw
 * color strings.
 */
public final class BukkitConsoleSender implements ConsoleCommandSender, HExecution {

    @Deprecated
    public static final long COMMAND_OUTPUT_DELAY_TICKS = 40L;

    private final HuHoBotSpigot plugin;
    private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<String>();
    private final CommandSender.Spigot spigotSender;

    public BukkitConsoleSender(HuHoBotSpigot plugin) {
        this.plugin = plugin;
        this.spigotSender = new CommandSender.Spigot() {
            @Override
            public void sendMessage(BaseComponent component) {
                if (component == null) {
                    return;
                }
                BukkitConsoleSender.this.messages.add(CommandOutputAppender.cleanText(component.toLegacyText()));
            }

            @Override
            public void sendMessage(BaseComponent... components) {
                if (components == null) {
                    return;
                }
                for (BaseComponent component : components) {
                    if (component == null) {
                        continue;
                    }
                    BukkitConsoleSender.this.messages.add(CommandOutputAppender.cleanText(component.toLegacyText()));
                }
            }
        };
    }

    public final void clearMessages() {
        this.messages.clear();
    }

    public final List<String> getAndClearMessages() {
        List<String> result = this.messages.stream().collect(Collectors.toList());
        this.messages.clear();
        return result;
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {
    }

    @Override
    public boolean isPermissionSet(String name) {
        return false;
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return false;
    }

    @Override
    public boolean hasPermission(String name) {
        return true;
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return true;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
    }

    @Override
    public void recalculatePermissions() {
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return new LinkedHashSet<PermissionAttachmentInfo>();
    }

    @Override
    public void sendMessage(String message) {
        if (message == null) {
            return;
        }
        this.messages.add(CommandOutputAppender.cleanText(message));
    }

    @Override
    public void sendMessage(String... messages) {
        if (messages == null) {
            return;
        }
        for (String message : messages) {
            if (message == null) {
                continue;
            }
            this.messages.add(CommandOutputAppender.cleanText(message));
        }
    }

    @Override
    public void sendMessage(UUID sender, String message) {
        this.sendMessage(message);
    }

    @Override
    public void sendMessage(UUID sender, String... messages) {
        if (messages == null) {
            return;
        }
        for (String message : messages) {
            if (message == null) {
                continue;
            }
            this.messages.add(CommandOutputAppender.cleanText(message));
        }
    }

    @Override
    public Server getServer() {
        return Bukkit.getServer();
    }

    @Override
    public String getName() {
        return "CONSOLE";
    }

    @Override
    public net.kyori.adventure.text.Component name() {
        return net.kyori.adventure.text.Component.text("CONSOLE");
    }

    @Override
    public CommandSender.Spigot spigot() {
        return this.spigotSender;
    }

    @Override
    public boolean isConversing() {
        return false;
    }

    @Override
    public void acceptConversationInput(String input) {
    }

    @Override
    public boolean beginConversation(Conversation conversation) {
        return false;
    }

    @Override
    public void abandonConversation(Conversation conversation) {
    }

    @Override
    public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
    }

    @Override
    public void sendRawMessage(String message) {
        this.sendMessage(message);
    }

    @Override
    public void sendRawMessage(UUID sender, String message) {
        this.sendMessage(message);
    }

    @Override
    public String getRawString() {
        return String.join("\n", this.messages);
    }

    @Override
    public CompletableFuture<HExecution> execute(String command) {
        CompletableFuture<HExecution> result = new CompletableFuture<HExecution>();
        this.clearMessages();
        final String cmd = command;
        final CompletableFuture<HExecution> fut = result;
        this.plugin.submit(() -> {
            try {
                Bukkit.dispatchCommand((CommandSender) this, cmd);
                Bukkit.getScheduler().runTaskLater((Plugin) this.plugin, () -> fut.complete(this), 40L);
            } catch (Exception error) {
                fut.completeExceptionally(error);
            }
        });
        return result;
    }
}
