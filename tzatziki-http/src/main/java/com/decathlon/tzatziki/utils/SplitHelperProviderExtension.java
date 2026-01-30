package com.decathlon.tzatziki.utils;

import com.github.jknack.handlebars.Helper;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import com.google.common.base.Splitter;

import java.util.Map;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

public class SplitHelperProviderExtension implements TemplateHelperProviderExtension {
    @Override
    public Map<String, Helper<?>> provideTemplateHelpers() {
        Helper<String> helper = (context, options) -> {
            String on = options.params.length > 0 ? options.param(0) : ",";
            return Splitter.on(on)
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToStream(decode(context, UTF_8))
                    .map(value -> unchecked(() -> options.fn(value)))
                    .collect(joining());
        };
        return Map.of("split", helper);
    }

    @Override
    public String getName() {
        return "custom-helpers";
    }
}
