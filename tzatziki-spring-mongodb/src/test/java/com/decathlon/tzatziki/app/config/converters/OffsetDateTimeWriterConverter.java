package com.decathlon.tzatziki.app.config.converters;

import java.time.OffsetDateTime;
import java.util.Date;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class OffsetDateTimeWriterConverter implements Converter<OffsetDateTime, Date> {

    @Override
    public Date convert(OffsetDateTime source) {
        return Date.from(source.toInstant());
    }

}