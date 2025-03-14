package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.decathlon.tzatziki.steps.ObjectSteps;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.specification.RequestSpecification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.http.entity.ContentType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.decathlon.tzatziki.utils.Types.rawTypeOf;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

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
        public RequestPatternBuilder toRequestPatternBuilder(ObjectSteps objects, Matcher uri, Comparison comparison, Boolean withBody, Boolean withHeaders) {

            RequestPatternBuilder request = new RequestPatternBuilder(RequestMethod.fromString(method.name()), WireMock.urlPathMatching(uri.group(4)));
            headers.forEach((key, value) -> request.withHeader(key, equalTo(value)));

            if (withBody) {
                addBodyWithType(request, objects, comparison);
            }

            if (withHeaders) {
                HttpUtils.parseQueryParams(uri.group(5), false).forEach((pair) -> request.withQueryParam(pair.getKey(), matching(pair.getValue())));
            }

            return request;
        }

        public RequestPatternBuilder toRequestPatternBuilder(ObjectSteps objects, Matcher uri, Comparison comparison) {
            return toRequestPatternBuilder(objects, uri, comparison, true, true);
        }

        public MappingBuilder toMappingBuilder(ObjectSteps objects, Matcher uri, Comparison comparison) {
            RequestPatternBuilder request = toRequestPatternBuilder(objects, uri, comparison);
            return convertToMappingBuilder(request);
        }

        public MappingBuilder convertToMappingBuilder(RequestPatternBuilder requestPatternBuilder) {
            RequestPattern build = requestPatternBuilder.build();
            // Create a new MappingBuilder using the method and URL from the RequestPatternBuilder
            MappingBuilder mappingBuilder = WireMock.request(build.getMethod().getName(), build.getUrlMatcher());

            // Copy headers from the RequestPatternBuilder to the MappingBuilder
            if (build.getHeaders() != null)
                build.getHeaders().forEach(mappingBuilder::withHeader);

            // Copy body patterns from the RequestPatternBuilder to the MappingBuilder
            if (build.getBodyPatterns() != null)
                build.getBodyPatterns().forEach(mappingBuilder::withRequestBody);

            // Copy query parameters from the RequestPatternBuilder to the MappingBuilder
            if (build.getQueryParameters() != null)
                build.getQueryParameters().forEach(mappingBuilder::withQueryParam);

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
            } catch (Exception e) {
                // ignore
            }

            if (ContentType.APPLICATION_XML.getMimeType().equals(contentType)) {
                requestPatternBuilder.withRequestBody(WireMock.equalToXml(bodyStr));
            } else if (ContentType.APPLICATION_JSON.getMimeType().equals(contentType) || !"String".equalsIgnoreCase(this.body.type)) {
                boolean inOrder = comparison.equals(Comparison.IS_EXACTLY) || comparison.equals(Comparison.CONTAINS_IN_ORDER) || comparison.equals(Comparison.CONTAINS_ONLY_IN_ORDER);
                boolean strictMatch = comparison.equals(Comparison.IS_EXACTLY) || comparison.equals(Comparison.CONTAINS_ONLY) || comparison.equals(Comparison.CONTAINS_ONLY_IN_ORDER);
                requestPatternBuilder.withRequestBody(WireMock.equalToJson(bodyStr, !inOrder, !strictMatch));
            } else {
                requestPatternBuilder.withRequestBody(WireMock.equalTo(bodyStr));
            }
        }

        public static Request fromLoggedRequest(LoggedRequest loggedRequest) {
            RequestBuilder builder = Request.builder()
                    .path(loggedRequest.getUrl())
                    .method(Method.of(loggedRequest.getMethod().getName()));

            if (loggedRequest.getHeaders() != null) {
                builder.headers(HttpUtils.asMap(loggedRequest.getHeaders().all()));
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

            String bodyString = body.toString(objects, urlParamMatcher);

            if (bodyString != null) {
                bodyString = bodyString.replace("{w", "{").replace("w}", "}");
            }
            responseDefinitionBuilder.withBody(bodyString);
            responseDefinitionBuilder.withFixedDelay((int) delay);
            return responseDefinitionBuilder;
        }

        public static Response fromLoggedResponse(LoggedResponse loggedResponse) {
            ResponseBuilder builder = Response.builder()
                    .status(HttpStatusCode.code(loggedResponse.getStatus()).name());

            if (loggedResponse.getHeaders() != null) {
                builder.headers(HttpUtils.asMap(loggedResponse.getHeaders().all()));
            }
            if (loggedResponse.getBodyAsString() != null) {
                builder.body(Body.builder().payload(loggedResponse.getBodyAsString()).build());
            }

            return builder.build();
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
            return toString(objects, null);
        }

        public String toString(ObjectSteps objects, Matcher replacer) {
            Class<?> clazz = rawTypeOf(TypeParser.parse(type));
            if (payload == null) {
                return null;
            }

            if (payload instanceof String) {
                String resolvedPayload = objects.resolve(payload);
                if (replacer != null && replacer.matches()) resolvedPayload = replacer.replaceAll(resolvedPayload);
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
    }
}


