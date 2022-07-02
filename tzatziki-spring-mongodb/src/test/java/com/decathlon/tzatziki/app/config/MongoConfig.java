package com.decathlon.tzatziki.app.config;

import com.decathlon.tzatziki.app.config.converters.OffsetDateTimeReadConverter;
import com.decathlon.tzatziki.app.config.converters.OffsetDateTimeWriterConverter;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {

        return new MongoCustomConversions(
                Arrays.asList(
                        new OffsetDateTimeReadConverter(),
                        new OffsetDateTimeWriterConverter()
                )
        );
    }

}
