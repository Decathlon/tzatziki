package com.decathlon.tzatziki.utils;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContentTypeTransformer implements ResponseTransformerV2 {

    public static final String INVALID_REGEX_PATTERN = "(\\[])";

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        if (response.getBodyAsString() != null && response.getHeaders().getContentTypeHeader() != null
                && response.getHeaders().getContentTypeHeader().containsValue("application/json")) {
            return Response.Builder.like(response)
                    .body(Mapper.toJson(response.getBodyAsString()))
                    .build();
        }
        return response;
    }


    @Override
    public String getName() {
        return "content-type-transformer";
    }
}
