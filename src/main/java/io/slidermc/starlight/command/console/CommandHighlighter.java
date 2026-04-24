package io.slidermc.starlight.command.console;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.List;
import java.util.regex.Pattern;

public class CommandHighlighter implements Highlighter {
    private final CommandDispatcher<IStarlightCommandSource> dispatcher;
    private final IStarlightCommandSource source;

    public CommandHighlighter(CommandDispatcher<IStarlightCommandSource> dispatcher, IStarlightCommandSource source) {
        this.dispatcher = dispatcher;
        this.source = source;
    }

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        if (buffer.isEmpty()) return AttributedString.EMPTY;

        try {
            ParseResults<IStarlightCommandSource> parse = dispatcher.parse(buffer, source);
            List<ParsedCommandNode<IStarlightCommandSource>> nodes = parse.getContext().getNodes();

            int errorPos = buffer.length();
            for (CommandSyntaxException ex : parse.getExceptions().values()) {
                errorPos = Math.min(errorPos, ex.getCursor());
            }
            boolean hasError = errorPos < buffer.length();

            AttributedStringBuilder sb = new AttributedStringBuilder();

            if (nodes.isEmpty()) {
                if (hasError && errorPos > 0) {
                    sb.append(buffer.substring(0, errorPos), AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    sb.append(buffer.substring(errorPos), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                } else if (hasError) {
                    sb.append(buffer, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                } else {
                    sb.append(buffer, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                }
                return sb.toAttributedString();
            }

            int pos = 0;
            for (ParsedCommandNode<IStarlightCommandSource> pcn : nodes) {
                int nodeStart = pcn.getRange().getStart();
                int segEnd = Math.min(pcn.getRange().getEnd(), buffer.length());

                if (nodeStart > pos) {
                    sb.append(buffer.substring(pos, nodeStart),
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                    pos = nodeStart;
                }

                AttributedStyle style = styleForNode(pcn.getNode());
                if (pos < segEnd) {
                    if (hasError && pos >= errorPos) {
                        sb.append(buffer.substring(pos, segEnd),
                                AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                    } else if (hasError && errorPos < segEnd) {
                        sb.append(buffer.substring(pos, errorPos), style);
                        sb.append(buffer.substring(errorPos, segEnd),
                                AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                    } else {
                        sb.append(buffer.substring(pos, segEnd), style);
                    }
                    pos = segEnd;
                }
            }

            if (pos < buffer.length()) {
                AttributedStyle tailStyle;
                if (hasError) {
                    tailStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
                } else {
                    tailStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).faint();
                }
                sb.append(buffer.substring(pos), tailStyle);
            }

            return sb.toAttributedString();
        } catch (Exception e) {
            return new AttributedString(buffer);
        }
    }
    private static AttributedStyle styleForNode(CommandNode<IStarlightCommandSource> node) {
        if (node instanceof LiteralCommandNode) {
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
        }
        if (node instanceof ArgumentCommandNode) {
            ArgumentType<?> type = ((ArgumentCommandNode<IStarlightCommandSource, ?>) node).getType();
            if (type instanceof BoolArgumentType) {
                return AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA);
            }
            if (type instanceof StringArgumentType) {
                return AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
            }
            if (type instanceof IntegerArgumentType
                    || type instanceof FloatArgumentType
                    || type instanceof DoubleArgumentType
                    || type instanceof LongArgumentType) {
                return AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
            }
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
        }
        return AttributedStyle.DEFAULT;
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {
    }

    @Override
    public void setErrorIndex(int errorIndex) {
    }
}
