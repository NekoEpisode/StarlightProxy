package io.slidermc.starlight.command.console;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

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
        if (buffer.isEmpty()) return new AttributedString(buffer);

        try {
            ParseResults<IStarlightCommandSource> parse = dispatcher.parse(buffer, source);
            if (parse.getExceptions().isEmpty() && !parse.getReader().canRead()) {
                return new AttributedString(buffer, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
            }

            int errorPos = buffer.length();
            for (CommandSyntaxException ex : parse.getExceptions().values()) {
                errorPos = Math.min(errorPos, ex.getCursor());
            }

            if (errorPos <= 0) {
                return new AttributedString(buffer);
            }

            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.append(buffer.substring(0, errorPos), AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
            sb.append(buffer.substring(errorPos), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
            return sb.toAttributedString();
        } catch (Exception e) {
            return new AttributedString(buffer);
        }
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {
    }

    @Override
    public void setErrorIndex(int errorIndex) {
    }
}
