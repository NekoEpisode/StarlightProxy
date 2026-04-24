package io.slidermc.starlight.command.console;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CommandCompleter implements Completer {
    private final CommandDispatcher<IStarlightCommandSource> dispatcher;
    private final IStarlightCommandSource source;

    public CommandCompleter(CommandDispatcher<IStarlightCommandSource> dispatcher, IStarlightCommandSource source) {
        this.dispatcher = dispatcher;
        this.source = source;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        int cursor = line.cursor();

        if (buffer.isEmpty()) return;

        try {
            ParseResults<IStarlightCommandSource> parse = dispatcher.parse(buffer, source);
            Suggestions suggestions = dispatcher.getCompletionSuggestions(parse, cursor).get(100, TimeUnit.MILLISECONDS);

            for (Suggestion s : suggestions.getList()) {
                String text = s.getText();
                if (text != null && !text.isEmpty()) {
                    candidates.add(new Candidate(text, text, null, null, null, null, true));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
