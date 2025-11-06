package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.decathlon.tzatziki.steps.ObjectSteps;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.specification.RequestSpecification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.entity.ContentType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.decathlon.tzatziki.utils.Types.rawTypeOf;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.stream.Collectors.toList;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Interaction {
    public static boolean printResponses;

    @Builder.Default
    public Request request = new Request();
    @Builder.Default
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<Response> response = List.of(new Response());

    public static String wrapAsInteractionJson(String request) {
        return "{\"request\": %s}".formatted(request);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class Request {

        public String path;
        @Builder.Default
        public Map<String, String> headers = new LinkedHashMap<>();
        @Builder.Default
        public Body body = new Body();
        @Builder.Default
        public Method method = Method.GET;

        public io.restassured.response.Response send(RequestSpecification request, String path, ObjectSteps objects) {
            headers.forEach(request::header);
            if (body.payload != null) {
                String contentType = headers.get("Content-Type");
                if (contentType == null) {
                    if (!(body.payload instanceof String) || !body.type.equals(String.class.getSimpleName())) {
                        contentType = "application/json";
                    } else {
                        contentType = "text/plain";
                    }
                }
                request.contentType(contentType);

                if (body.payload instanceof byte[] payload) {
                    request.body(payload);
                } else {
                    request.body(body.toString(objects));
                }
            }
            return request.request(method.name(), path);
        }

        /**
         * Converts the current interaction request to an toRequestPatternBuilder suitable for Wiremock
         *
         * @return the requestMapping under Wiremock format
         */
        public RequestPatternBuilder toRequestPatternBuilder(ObjectSteps objects, Matcher uri, Comparison comparison, RequestMethod requestMethod) {

            RequestPatternBuilder request = new RequestPatternBuilder(requestMethod, WireMock.urlPathMatching(uri.group(4) + "\\/?"));
            addBodyWithType(request, objects, comparison);
            headers.forEach((key, value) -> request.withHeader(key, new FlagPattern(value)));
            if (uri.group(5) != null) {
                HttpWiremockUtils.parseQueryParams(uri.group(5), false)
                        .stream()
                        .collect(Collectors.groupingBy(Pair::getKey, LinkedHashMap::new,
                                Collectors.mapping(Pair::getValue, Collectors.toList())))
                        .forEach((key, value) -> {
                            if (value.size() > 1) {
                                request.withQueryParam(key, including(value.toArray(new String[0])));
                            } else {
                                request.withQueryParam(key, matching(value.get(0)));
                            }

                        });
            }

            return request;
        }

        public RequestPatternBuilder toRequestPatternBuilder(ObjectSteps objects, Matcher uri, Comparison comparison) {
            return toRequestPatternBuilder(objects, uri, comparison, RequestMethod.fromString(method.name()));
        }

        public MappingBuilder toMappingBuilder(ObjectSteps objects, Matcher uri, Comparison comparison) {
            RequestPatternBuilder request = toRequestPatternBuilder(objects, uri, comparison);
            return convertToMappingBuilder(request);
        }

        public MappingBuilder convertToMappingBuilder(RequestPatternBuilder requestPatternBuilder) {
            RequestPattern build = requestPatternBuilder.build();
            MappingBuilder mappingBuilder = WireMock.request(build.getMethod().getName(), build.getUrlMatcher());

            if (build.getHeaders() != null) {
                build.getHeaders().forEach(mappingBuilder::withHeader);
            }

            if (build.getBodyPatterns() != null) {
                build.getBodyPatterns().forEach(mappingBuilder::withRequestBody);
            }

            if (build.getQueryParameters() != null) {
                build.getQueryParameters().forEach(mappingBuilder::withQueryParam);
            }
            return mappingBuilder;
        }

        private void addBodyWithType(RequestPatternBuilder requestPatternBuilder, ObjectSteps objects, Comparison comparison) {
            String bodyStr = this.body.toString(objects);
            if (bodyStr == null) {
                return;
            }
            String contentType = null;
            try {
                contentType = ContentType.parse(this.headers.get("Content-Type")).getMimeType();
            } catch (Exception ignore) {
                // ignore
            }

            if (ContentType.APPLICATION_XML.getMimeType().equals(contentType)) {
                requestPatternBuilder.withRequestBody(WireMock.equalToXml(bodyStr));
            } else if (ContentType.APPLICATION_JSON.getMimeType().equals(contentType) || !"String".equalsIgnoreCase(this.body.type)) {
                requestPatternBuilder.withRequestBody(new BodyPattern(comparison, bodyStr));
            } else {
                requestPatternBuilder.withRequestBody(new BodyPattern(comparison, bodyStr));
            }
        }

        public static Request fromLoggedRequest(LoggedRequest loggedRequest) {
            RequestBuilder builder = Request.builder()
                    .path(loggedRequest.getUrl())
                    .method(Method.of(loggedRequest.getMethod().getName()));

            if (loggedRequest.getHeaders() != null) {
                builder.headers(HttpWiremockUtils.asMap(loggedRequest.getHeaders().all()));
            }
            if (loggedRequest.getBodyAsString() != null) {
                builder.body(Body.builder().payload(loggedRequest.getBodyAsString()).build());
            }

            return builder.build();
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class Response {

        @Builder.Default
        public Map<String, String> headers = new LinkedHashMap<>();
        @Builder.Default
        public Body body = new Body();
        public String status;
        public int consumptions = 1;
        public long delay;
        public long time;

        public static Response fromResponse(io.restassured.response.Response response) {
            return Response.builder()
                    .status(HttpStatusCode.code(response.getStatusCode()).name())
                    .body(Body.builder()
                            .payload(printResponses ? response.prettyPrint() : response.asString())
                            .build())
                    .time(response.time())
                    .headers(response.getHeaders().asList().stream().collect(
                            LinkedHashMap::new, (map, header) -> map.put(header.getName(), header.getValue()), LinkedHashMap::putAll))
                    .build();
        }

        public ResponseDefinitionBuilder toResponseDefinitionBuilder(ObjectSteps objects, Matcher urlParamMatcher) {
            ResponseDefinitionBuilder responseDefinitionBuilder = aResponse()
                    .withStatus(status != null ? HttpSteps.getHttpStatusCode(status).getCode() : 200)
                    .withTransformers("response-template");
            headers.forEach(responseDefinitionBuilder::withHeader);

            String bodyString = body.toString(objects);
            responseDefinitionBuilder.withBody(bodyString);
            responseDefinitionBuilder.withFixedDelay((int) delay);
            return responseDefinitionBuilder;
        }

        public static Response fromLoggedResponse(LoggedResponse loggedResponse) {
            ResponseBuilder builder = Response.builder()
                    .status(HttpStatusCode.code(loggedResponse.getStatus()).name());

            if (loggedResponse.getHeaders() != null) {
                builder.headers(HttpWiremockUtils.asMap(loggedResponse.getHeaders().all()));
            }
            if (loggedResponse.getBodyAsString() != null) {
                builder.body(Body.builder().payload(loggedResponse.getBodyAsString()).build());
            }

            return builder.build();
        }

        public com.github.tomakehurst.wiremock.http.Response toWiremockResponse() {
            return com.github.tomakehurst.wiremock.http.Response.response()
                    .status(status != null ? Integer.parseInt(status) : 200)
                    .headers(new HttpHeaders(headers.entrySet().stream()
                            .map(e -> new HttpHeader(e.getKey(), e.getValue()))
                            .collect(toList())))
                    .body(body.toString()).build();
        }

    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class Body {

        @Builder.Default
        public String type = String.class.getSimpleName();

        public Object payload;

        public String toString(ObjectSteps objects) {
            Class<?> clazz = rawTypeOf(TypeParser.parse(type));
            if (payload == null) {
                return null;
            }

            if (payload instanceof String payloadString) {
                String resolvedPayload = objects != null ? objects.resolve(payload) : payloadString;
                try {
                    return clazz.equals(String.class) ? resolvedPayload : Mapper.toJson(Mapper.read(resolvedPayload, clazz));
                } catch (Throwable throwable) {
                    return resolvedPayload;
                }
            } else {
                String body = Mapper.toJson(payload);
                if (!clazz.equals(String.class)) {
                    body = Mapper.toJson(Mapper.read(body, clazz));
                }
                return body;
            }
        }

        public String toString() {
            return toString(null);
        }
    }
}
