package com.medbridge.parser;

import com.medbridge.entity.SourceFormat;
import com.medbridge.exception.ParseException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Hands out the right parser for a format.
 *
 * Spring injects EVERY bean that implements RecordParser into the constructor's List.
 * This class never names CsvRecordParser, JsonRecordParser or Hl7RecordParser, so adding
 * a fourth parser requires no change here.
 */
@Component
public class ParserFactory {

    private final Map<SourceFormat, RecordParser> parsers = new EnumMap<>(SourceFormat.class);

    public ParserFactory(List<RecordParser> parserList) {
        for (RecordParser parser : parserList) {
            RecordParser previous = parsers.put(parser.getSupportedFormat(), parser);
            if (previous != null) {
                // fail at startup rather than silently letting one parser shadow another
                throw new IllegalStateException("Two parsers registered for " + parser.getSupportedFormat() + ": "
                        + previous.getClass().getSimpleName() + " and " + parser.getClass().getSimpleName());
            }
        }
    }

    public RecordParser getParser(SourceFormat format) {
        RecordParser parser = parsers.get(format);
        if (parser == null) {
            throw new ParseException("No parser registered for format " + format);
        }
        return parser;
    }
}
