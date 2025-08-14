package com.decathlon.tzatziki.jetty;

import com.decathlon.tzatziki.configuration.HttpConfigurationProperties;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty12.Jetty12HttpServer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.QoSHandler;

public class JettyRequestLimitHttpServer extends Jetty12HttpServer {
    public JettyRequestLimitHttpServer(Options options, AdminRequestHandler adminRequestHandler, StubRequestHandler stubRequestHandler) {
        super(options, adminRequestHandler, stubRequestHandler);
    }

    @Override
    protected Handler createHandler(Options options, AdminRequestHandler adminRequestHandler, StubRequestHandler stubRequestHandler) {
        Handler handler = super.createHandler(options, adminRequestHandler, stubRequestHandler);

        int maxConcurrentRequestsProperty = HttpConfigurationProperties.getMaxConcurrentRequestsProperty();
        if (maxConcurrentRequestsProperty > 0) {
            QoSHandler qosHandler = new QoSHandler();
            qosHandler.setMaxRequestCount(maxConcurrentRequestsProperty);
            qosHandler.setHandler(handler);
            return qosHandler;
        }

        return handler;
    }
}
