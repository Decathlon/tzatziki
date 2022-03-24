package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.decathlon.tzatziki.steps.ObjectSteps;
import io.restassured.specification.RequestSpecification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mockserver.model.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

import static com.decathlon.tzatziki.utils.Types.rawTypeOf;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Interaction {

    public static boolean printResponses;

    @Builder.Default
    public Request request = new Request();
    @Builder.Default
    public Response response = new Response();

    public static Interaction fromRequest(Request request) {
        return Interaction.builder().request(request).build();
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class Request {

        @Builder.Default
        public Map<String, String> headers = new LinkedHashMap<>();
        @Builder.Default
        public Body body = new Body();
        @Builder.Default
        public Method method = Method.GET;

        public static Request fromHttpRequest(HttpRequest httpRequest) {
            return Request.builder()
                    .method(Method.of(httpRequest.getMethod().getValue()))
                    .headers(asMap(httpRequest.getHeaders()))
                    .body(Body.builder().payload(httpRequest.getBodyAsString()).build())
                    .build();
        }

        public HttpRequest toHttpRequestIn(ObjectSteps objects, Matcher uri) {
            HttpRequest httpRequest = HttpRequest.request()
                    .withMethod(method.name())
                    .withHeaders(headers.entrySet().stream()
                            .map(e -> new Header(e.getKey(), e.getValue()))
                            .collect(toList()));
            addBodyWithType(httpRequest, objects);
            Parameters parameters = new Parameters(MockFaster.toParameters(uri.group(5)));
            parameters.withKeyMatchStyle(KeyMatchStyle.MATCHING_KEY);
            return httpRequest.withPath(uri.group(4)).withQueryStringParameters(parameters);
        }

        private void addBodyWithType(HttpRequest httpRequest, ObjectSteps objects) {
            String bodyStr = this.body.toString(objects);
            if (bodyStr == null) {
                return;
            }

            final String contentType = this.headers.get("Content-Type");
            MediaType mediaType = MediaType.parse(contentType);
            if (mediaType.isXml()) {
                httpRequest.withBody(XmlBody.xml(bodyStr));
            } else if (mediaType.isJson() || !"String".equalsIgnoreCase(this.body.type)) {
                httpRequest.withBody(JsonBody.json(bodyStr));
            } else {
                httpRequest.withBody(bodyStr);
            }
        }

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
                request.body(body.toString(objects));
            }
            return request.request(method.name(), path);
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

        public static Response fromHttpResponse(HttpResponse httpResponse) {
            return Response.builder()
                    .headers(asMap(httpResponse.getHeaders()))
                    .status(HttpStatusCode.code(httpResponse.getStatusCode()).name())
                    .body(Body.builder()
                            .payload(httpResponse.getBodyAsString())
                            .build())
                    .build();
        }


        public HttpResponse toHttpResponseIn(ObjectSteps objects) {
            return HttpResponse.response()
                    .withStatusCode(status != null ? HttpSteps.getHttpStatusCode(status).code() : 200)
                    .withHeaders(headers.entrySet().stream()
                            .map(e -> new Header(e.getKey(), e.getValue()))
                            .collect(toList()))
                    .withDelay(Delay.milliseconds(delay))
                    .withBody(toBodyWithContentType(body.toString(objects)));
        }

        private BodyWithContentType<?> toBodyWithContentType(String body) {
            String contentType = headers.get("Content-Type");
            if (StringUtils.isNotBlank(contentType)) {
                MediaType mediaType = MediaType.parse(contentType);
                if (mediaType.isJson()) {
                    if (mediaType.getCharset() == null) {
                        mediaType = mediaType.withCharset(JsonBody.DEFAULT_JSON_CONTENT_TYPE.getCharset());
                    }
                    return JsonBody.json(body, mediaType);
                }
                return StringBody.exact(body, mediaType);
            }
            return StringBody.exact(body);
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
            if (payload instanceof String) {
                String resolvedPayload = objects.resolve(payload);
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

    @NotNull
    private static Map<String, String> asMap(Headers headers) {
        return headers == null ? Collections.emptyMap() : headers.getEntries().stream().collect(toMap(e -> e.getName().getValue(), e -> e.getValues().get(0).getValue()));
    }
}


