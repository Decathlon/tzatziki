package com.decathlon.tzatziki.utils;

import com.github.tomakehurst.wiremock.common.ContentTypes;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
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
        if (!isTextual(response.getHeaders().getContentTypeHeader())) {
            return response;
        }
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
                    String result = responsePayload;
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        // Replace $i with the actual captured group value
                        String groupValue = matcher.group(i);
                        result = result.replace("$" + i, groupValue);
                    }
                    return result;
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

    private boolean isTextual(ContentTypeHeader contentTypeHeader) {
        // Treat a missing Content-Type header as textual: users who mock plain text responses
        // often omit the Content-Type header, so we must default to allowing transformation
        // rather than silently skipping it and breaking their stubs.
        if (contentTypeHeader == null) {
            return true;
        }

        // Same assumption when the mime type part cannot be parsed from the header value.
        String mimeType = contentTypeHeader.mimeTypePart();
        if (mimeType == null) {
            return true;
        }

        return ContentTypes.determineIsTextFromMimeType(mimeType);
    }

    private String escapeBrackets(String string) {
        Matcher matcher = Pattern.compile(INVALID_REGEX_PATTERN).matcher(string);
        return matcher.replaceAll("\\\\[\\\\]");
    }
}
