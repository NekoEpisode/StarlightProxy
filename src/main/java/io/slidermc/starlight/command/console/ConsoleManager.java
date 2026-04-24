package io.slidermc.starlight.command.console;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.command.source.ConsoleCommandSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsoleManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ConsoleManager.class);
    private static final String PROMPT = "> ";

    private final StarlightProxy proxy;
    private final ConsoleCommandSource consoleSource;
    private final Terminal terminal;
    private final LineReader reader;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ConsoleManager(StarlightProxy proxy) throws IOException {
        this.proxy = proxy;
        this.consoleSource = new ConsoleCommandSource();

        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new CommandCompleter(proxy.getCommandDispatcher(), consoleSource))
                .highlighter(new CommandHighlighter(proxy.getCommandDispatcher(), consoleSource))
                .variable(LineReader.HISTORY_FILE, Path.of(".console_history"))
                .variable(LineReader.HISTORY_SIZE, 1000)
                .option(LineReader.Option.HISTORY_BEEP, false)
                .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                .option(LineReader.Option.HISTORY_IGNORE_SPACE, true)
                .build();

        redirectLog4jOutput();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "console-shutdown"));

        Thread.ofVirtual()
                .name("console-reader")
                .start(this::readLoop);
    }

    private void redirectLog4jOutput() {
        OutputStream consoleStream = new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    flushLine();
                } else if (b != '\r') {
                    buffer.append((char) b);
                }
            }

            @Override
            public void write(byte @NonNull [] b, int off, int len) {
                int start = off;
                for (int i = off; i < off + len; i++) {
                    if (b[i] == '\n') {
                        if (i > start) {
                            buffer.append(new String(b, start, i - start, StandardCharsets.UTF_8));
                        }
                        flushLine();
                        start = i + 1;
                    }
                }
                if (start < off + len) {
                    buffer.append(new String(b, start, off + len - start, StandardCharsets.UTF_8));
                }
            }

            @Override
            public void flush() {
                if (!buffer.isEmpty()) {
                    String line = buffer.toString();
                    buffer.setLength(0);
                    reader.printAbove(line);
                }
            }

            private void flushLine() {
                String line = buffer.toString();
                buffer.setLength(0);
                reader.printAbove(line);
            }
        };

        PrintStream ps = new PrintStream(consoleStream, true, StandardCharsets.UTF_8);
        System.setOut(ps);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss} %-5level]: %msg%n")
                .build();

        OutputStreamAppender appender = OutputStreamAppender.newBuilder()
                .setName("JLineConsole")
                .setLayout(layout)
                .setTarget(ps)
                .build();
        appender.start();
        config.addAppender(appender);

        for (LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.removeAppender("Console");
            loggerConfig.removeAppender("PluginConsole");
            loggerConfig.addAppender(appender, null, null);
        }
        config.getRootLogger().removeAppender("Console");
        config.getRootLogger().removeAppender("PluginConsole");
        config.getRootLogger().addAppender(appender, null, null);

        ctx.updateLoggers();
    }

    private void readLoop() {
        try {
            while (running.get()) {
                try {
                    String line = reader.readLine(PROMPT);
                    if (line == null) break;
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    proxy.getCommandManager().execute(trimmed, consoleSource);
                } catch (UserInterruptException | EndOfFileException e) {
                    shutdown();
                    break;
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                log.warn("Console reader thread exited abnormally", e);
            }
        } finally {
            cleanup();
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        try {
            terminal.close();
        } catch (Exception ignored) {
        }
    }

    private void cleanup() {
        try {
            terminal.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}
