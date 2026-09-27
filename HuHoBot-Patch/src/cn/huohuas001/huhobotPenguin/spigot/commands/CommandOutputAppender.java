package cn.huohuas001.huhobotPenguin.spigot.commands;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Patched CommandOutputAppender for HuHoBot-Penguin 1.4.0
 * Original logic preserved; captured log lines are now cleaned of
 * Minecraft section color codes and ANSI escape sequences before
 * being queued, so command output forwarded to QQ contains no raw
 * color strings.
 */
public final class CommandOutputAppender extends AbstractAppender {

    private static final Pattern ANSI_ESCAPE_PATTERN =
            Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]");
    private static final Pattern SECTION_COLOR_PATTERN =
            Pattern.compile("\u00A7[0-9a-fk-orxA-FK-ORX#]");

    public static final Companion Companion = new Companion(null);

    private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<String>();
    private volatile boolean capturing;

    @SuppressWarnings("unused")
    private static CommandOutputAppender instance;

    private CommandOutputAppender() {
        super("CommandOutputAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        this.start();
    }

    /** Strips Minecraft legacy color codes and ANSI escape sequences. */
    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String cleaned = ANSI_ESCAPE_PATTERN.matcher(text).replaceAll("");
        cleaned = SECTION_COLOR_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned;
    }

    @Override
    public void append(LogEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        if (this.capturing) {
            this.messages.add(cleanText(event.getMessage().getFormattedMessage()));
        }
    }

    public final void startCapture() {
        this.messages.clear();
        this.capturing = true;
    }

    public final List<String> stopCapture() {
        this.capturing = false;
        return this.messages.stream().collect(java.util.stream.Collectors.toList());
    }

    /* Synthetic constructor kept for binary compatibility with the original Kotlin Companion. */
    public CommandOutputAppender(kotlin.jvm.internal.DefaultConstructorMarker marker) {
        this();
    }

    public static final class Companion {
        private Companion() {
        }

        public CommandOutputAppender getInstance() {
            CommandOutputAppender current = instance;
            if (current != null) {
                return current;
            }
            CommandOutputAppender appender = new CommandOutputAppender(null);
            Logger logger = LogManager.getRootLogger();
            if (logger instanceof org.apache.logging.log4j.core.Logger) {
                ((org.apache.logging.log4j.core.Logger) logger).addAppender((Appender) appender);
            }
            instance = appender;
            return appender;
        }

        public void removeInstance() {
            CommandOutputAppender current = instance;
            if (current != null) {
                Logger logger = LogManager.getRootLogger();
                if (logger instanceof org.apache.logging.log4j.core.Logger) {
                    ((org.apache.logging.log4j.core.Logger) logger).removeAppender((Appender) current);
                }
                current.stop();
            }
            instance = null;
        }

        public Companion(kotlin.jvm.internal.DefaultConstructorMarker marker) {
            this();
        }
    }
}
