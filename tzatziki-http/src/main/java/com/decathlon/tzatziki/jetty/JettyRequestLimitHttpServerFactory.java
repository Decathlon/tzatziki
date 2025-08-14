package com.decathlon.tzatziki.jetty;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.*;

public class JettyRequestLimitHttpServerFactory implements HttpServerFactory, DefaultFactory {
    @Override
    public String getName() {
        return "jetty-request-limit-http-server-factory";
    }

    public HttpServer buildHttpServer(Options options, AdminRequestHandler adminRequestHandler, StubRequestHandler stubRequestHandler) {
        return new JettyRequestLimitHttpServer(options, adminRequestHandler, stubRequestHandler);
    }
}