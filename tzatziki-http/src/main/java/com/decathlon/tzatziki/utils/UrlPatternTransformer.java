package com.decathlon.tzatziki.utils;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class UrlPatternTransformer implements ResponseTransformerV2 {

    public static final String INVALID_REGEX_PATTERN = "(\\[])";

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        return Response.Builder.like(response)
                .body(replaceCapturingGroup(response.getBodyAsString(), serveEvent))
                .build();
    }


    @Override
    public String getName() {
        return "url-pattern-transformer";
    }

    private String replaceCapturingGroup(String responsePayload, ServeEvent serveEvent) {
        if (responsePayload != null) {
            String expected = serveEvent.getStubMapping().getRequest().getUrlMatcher().getPattern().getExpected();
            if (serveEvent.getStubMapping().getRequest().getQueryParameters() != null) {
                expected += "\\?" + getExpectedFromQueryParameters(serveEvent);
            }
            Pattern urlPattern = Pattern.compile(escapeBrackets(expected));
            String url = serveEvent.getRequest().getUrl();
            Matcher matcher = urlPattern.matcher(url);
            if (matcher.matches() && matcher.groupCount() > 0) {
                try {
                    return matcher.replaceAll(responsePayload);
                } catch (Exception e) {
                    log.error(e.getMessage(), e); // let's warn in the test logs and continue
                }
            }
        }
        return responsePayload;
    }

    private String getExpectedFromQueryParameters(ServeEvent serveEvent) {
        return serveEvent.getStubMapping().getRequest().getQueryParameters().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().getExpected()).collect(Collectors.joining("&"));
    }

    private String escapeBrackets(String string) {
        Matcher matcher = Pattern.compile(INVALID_REGEX_PATTERN).matcher(string);
        return matcher.replaceAll("\\\\[\\\\]");
    }
}
