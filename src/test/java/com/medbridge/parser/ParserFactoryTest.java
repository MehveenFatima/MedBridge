package com.medbridge.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbridge.entity.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserFactoryTest {

    private final ParserFactory factory = new ParserFactory(List.of(
            new CsvRecordParser(), new JsonRecordParser(new ObjectMapper()), new Hl7RecordParser()));

    @Test
    void returnsTheParserForEachFormat() {
        assertInstanceOf(CsvRecordParser.class, factory.getParser(SourceFormat.CSV));
        assertInstanceOf(JsonRecordParser.class, factory.getParser(SourceFormat.JSON));
        assertInstanceOf(Hl7RecordParser.class, factory.getParser(SourceFormat.HL7));
    }

    @Test
    void refusesTwoParsersForTheSameFormat() {
        assertThrows(IllegalStateException.class,
                () -> new ParserFactory(List.of(new Hl7RecordParser(), new Hl7RecordParser())));
    }
}
