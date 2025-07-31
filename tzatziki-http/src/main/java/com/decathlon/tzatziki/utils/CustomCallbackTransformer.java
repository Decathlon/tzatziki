package com.decathlon.tzatziki.utils;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class CustomCallbackTransformer implements ResponseTransformerV2 {

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        Parameters parameters = serveEvent.getTransformerParameters();
        if (parameters.get("callback") != null && parameters.get("callback") instanceof Function callbackFunction) {
            @SuppressWarnings("unchecked")
            Function<Interaction.Request, Interaction.Response> typedCallback = (Function<Interaction.Request, Interaction.Response>) callbackFunction;
            Interaction.Response transformedResponse = typedCallback.apply(Interaction.Request.fromLoggedRequest(serveEvent.getRequest()));
            return transformedResponse.toWiremockResponse();
        }
        log.warn("No callback function provided or invalid type. Response will not be transformed.");
        return response;
    }


    @Override
    public String getName() {
        return "custom-callback-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }
}
