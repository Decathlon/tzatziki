package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.google.common.base.Splitter;
import edu.utexas.tacc.MathHelper;
import io.cucumber.core.backend.TestCaseState;
import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.runtime.SynchronizedEventBus;
import io.cucumber.datatable.DataTable;
import io.cucumber.docstring.DocString;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import io.cucumber.plugin.event.TestSourceParsed;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Assume;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.decathlon.tzatziki.steps.DynamicTransformers.register;
import static com.decathlon.tzatziki.utils.Comparison.IS_COMPARED_TO;
import static com.decathlon.tzatziki.utils.Fields.*;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Methods.findMethod;
import static com.decathlon.tzatziki.utils.Methods.invoke;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Time.TIME;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@Slf4j
public class ObjectSteps {

    public static final Pattern LIST = Pattern.compile("(.+)\\[(\\d+)]");
    public static final Pattern SUBSTRING = Pattern.compile("(.+)\\[(\\d+)-(\\d*)]");
    public static final Pattern INLINE_VARIABLE = Pattern.compile(VARIABLE + ": (.*)");

    @SuppressWarnings("UnstableApiUsage")
    public static final Handlebars handlebars = new Handlebars()
            .with((value, next) -> Mapper.toJson(value))
            .with(EscapingStrategy.NOOP) // we don't want to escape the templated content, it will be written as it is
            .registerHelpers(ConditionalHelpers.class)
            .registerHelper("math", new MathHelper())
            .registerHelper("split", (Helper<String>) (context, options) -> {
                String on = options.params.length > 0 ? options.param(0) : ",";
                return Splitter.on(on)
                        .trimResults()
                        .omitEmptyStrings()
                        .splitToStream(decode(context, UTF_8))
                        .map(value -> unchecked(() -> options.fn(value)))
                        .collect(joining());
            })
            .registerHelper("foreach", (context, options) -> {
                if (!(context instanceof Collection)) {
                    context = Mapper.read(context.toString(), List.class);
                }
                return ((Collection<?>) context).stream()
                        .map(value -> unchecked(() -> {
                                    final String placeholder = "_placeholder";
                                    final String strWithPlaceholder = options.fn(placeholder).toString();
                                    if (Mapper.isJson(strWithPlaceholder)) {
                                        return options.fn(value);
                                    }

                                    Pattern indentPattern = Pattern.compile("(\s*)" + placeholder);
                                    final Matcher indentMatcher = indentPattern.matcher(strWithPlaceholder);

                                    final String yamlStr = Mapper.toYaml(value);
                                    if (indentMatcher.find()) {
                                        return options.fn(yamlStr.lines().collect(joining("\n" + indentMatcher.group(1))));
                                    }

                                    return options.fn(value);
                                }
                        )).collect(Collectors.joining());
            })
            .registerHelper("concat", (firstArray, options) -> {
                if (options.params.length <= 0) {
                    return null;
                }

                List<Collection<?>> collectionsToConcat = Stream.concat(Stream.of(firstArray), Arrays.stream(options.params))
                        .map(arrayToConcat -> {
                            if (arrayToConcat instanceof Collection<?> array) {
                                return array;
                            } else {
                                return Mapper.<Collection<?>>read(arrayToConcat.toString(), List.class);
                            }
                        }).toList();

                return options.fn(collectionsToConcat.stream().flatMap(Collection::stream).collect(Collectors.toList()));
            });

    static {
        register(Type.class, TypeParser::parse);
        register(Comparison.class, Comparison::parse);
        register(Guard.class, Guard::parse);
        // we will return true no matter the value as long as it is not null or "false"
        register(boolean.class, value -> value != null && !value.equalsIgnoreCase("false"));
        // any null value will default to 0
        register(long.class, value -> ofNullable(value).map(Long::parseLong).orElse(0L));
        register(int.class, value -> ofNullable(value).map(Integer::parseInt).orElse(0));
        register(double.class, value -> ofNullable(value).map(Double::parseDouble).orElse(0d));
        register(float.class, value -> ofNullable(value).map(Float::parseFloat).orElse(0f));
        register(short.class, value -> ofNullable(value).map(Short::parseShort).orElse((short) 0));
        register(byte.class, value -> ofNullable(value).map(Byte::parseByte).orElse((byte) 0));
        register(Number.class, value -> ofNullable(value)
                .map(s -> s.matches("\\d+") ? (Number) Integer.parseInt(s) : (Number) Double.parseDouble(s))
                .orElse(0));
    }

    private final Map<String, Object> context = new LinkedHashMap<>();
    // Handlebars will use the property names to lookup values, we can hijack this proxy to use properties as helpers
    private final Map<String, Object> dynamicContext = (Map<String, Object>) Proxy.newProxyInstance(Map.class.getClassLoader(), new Class[]{Map.class}, (proxy, method, args) -> {
        if ("get".equals(method.getName())) {
            String property = (String) args[0];
            String variable = null;
            Matcher inlineVariable = INLINE_VARIABLE.matcher(property);
            if (inlineVariable.matches()) {
                variable = inlineVariable.group(1);
                property = inlineVariable.group(2);
            }

            Object value;
            if (property.startsWith("@")) {
                // this is a time to parse!
                value = Time.parse(property.substring(1));
            } else if (property.startsWith("&")) {
                // this is a file to load
                value = load(property.substring(1));
            } else {
                // let's get the value in the context, or fallback on the name of the property
                value = getOrSelf(property);
            }
            if (variable != null) {
                add(variable, value);
            }
            if (value instanceof String) {
                value = resolve(value);
            }
            return value;
        }
        return invoke(context, method, args);
    });

    @Before(order = 1)
    public void before(Scenario scenario) {
        Time.setToNow();
        add("_scenario", scenario);
        add("_env", Proxy.newProxyInstance(Map.class.getClassLoader(), new Class[]{Map.class}, (proxy, method, args) -> {
            String name = String.valueOf(args[0]);
            return switch (method.getName()) {
                case "get" -> System.getenv(name);
                case "containsKey" -> System.getenv(name) != null;
                case "put" -> {
                    Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
                    yield Env.export(name, String.valueOf(args[1]));
                }
                default -> invoke(new LinkedHashMap<>(), method, args);
            };
        }));
        add("_properties", Proxy.newProxyInstance(Map.class.getClassLoader(), new Class[]{Map.class}, (proxy, method, args) -> {
            String key = String.valueOf(args[0]);
            return switch (method.getName()) {
                case "get" -> System.getProperty(key);
                case "containsKey" -> System.getProperty(key) != null;
                case "put" -> System.setProperty(key, String.valueOf(args[1]));
                default -> invoke(new LinkedHashMap<>(), method, args);
            };
        }));
        add("_examples", getExamples(scenario));
        add("randomUUID", (Supplier<UUID>) UUID::randomUUID);
    }

    private Map<String, String> getExamples(Scenario scenario) {
        try {
            TestCaseState delegate = getValue(scenario, "delegate");
            EventBus bus = getValue(delegate, "bus");
            if (bus.getClass().getSimpleName().equals("LocalEventBus")) {
                bus = getValue(bus, "parent");
            }
            assertThat(bus.getClass()).isEqualTo(SynchronizedEventBus.class);
            bus = getValue(bus, "delegate");
            Map<Class<?>, List<Object>> handlers = getValue(bus, "handlers");
            return handlers.entrySet().stream()
                    .filter(e -> e.getKey().equals(TestSourceParsed.class))
                    .map(Map.Entry::getValue)
                    .map(l -> getValue(l.get(0), "arg$1"))
                    .map(plugin -> getValue(plugin, "currentStack"))
                    .map(currentStack -> currentStack instanceof ThreadLocal threadLocal ? threadLocal.get() : currentStack)
                    .map(currentStack -> (List<?>) currentStack)
                    .filter(stack -> stack.stream().anyMatch(s -> s.getClass().getSimpleName().startsWith("GherkinMessagesExamples")))
                    .findFirst()
                    .map(currentStack -> {
                        List<TableCell> headers = currentStack.stream()
                                .filter(stack -> stack.getClass().getSimpleName().equals("GherkinMessagesExamples"))
                                .map(o -> getValue(o, "examples"))
                                .map(examples -> (Examples) examples)
                                .map(examples -> examples.getTableHeader().getCells())
                                .findFirst().orElseThrow();
                        List<TableCell> values = currentStack.stream()
                                .filter(stack -> stack.getClass().getSimpleName().equals("GherkinMessagesExample"))
                                .map(o -> getValue(o, "tableRow"))
                                .map(tableRow -> (TableRow) tableRow)
                                .map(TableRow::getCells)
                                .findFirst().orElseThrow();
                        assertThat(headers).hasSameSizeAs(values);
                        Map<String, String> examples = new LinkedHashMap<>();
                        for (int i = 0; i < headers.size(); i++) {
                            examples.put(headers.get(i).getValue(), values.get(i).getValue());
                        }
                        return examples;
                    })
                    .orElseGet(Map::of);
        } catch (Throwable throwable) {
            log.warn(throwable.getMessage());
            return Map.of();
        }
    }

    @Given(THAT + GUARD + VARIABLE + " is(?: called with)?(?: " + A + TYPE + ")?:$")
    public void add_(Guard guard, String name, Type type, Object value) {
        add(guard, name, type, value);
    }

    @Given(THAT + GUARD + VARIABLE + " (?:=|is(?: called with)?)(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void add(Guard guard, String name, Type type, Object value) {
        guard.in(this, () -> {
            Object typedValue = resolvePossiblyTypedObject(type, value);
            getSetter(name, Types.rawTypeOf(type)).accept(typedValue);
        });
    }

    @Given(THAT + GUARD + VARIABLE + " (?:=|is) null$")
    public void nullify(Guard guard, String name) {
        guard.in(this, () -> getSetter(name, Object.class).accept(null));
    }

    @Given(THAT + GUARD + VARIABLE + " (?:=|is) " + NUMBER + "$")
    public void add(Guard guard, String name, Number value) {
        guard.in(this, () -> getSetter(name, value.getClass()).accept(value));
    }

    @Then(THAT + GUARD + VARIABLE + " (?:==|is equal to) " + NUMBER + "$")
    public void something_is_equal_to(Guard guard, String name, Number value) {
        guard.in(this, () -> assertThat(String.valueOf(this.<Object>get(name))).isEqualTo(String.valueOf(value)));
    }

    @Then(THAT + GUARD + VARIABLE + " (?:==|is equal to) null$")
    public void something_is_equal_to_null(Guard guard, String name) {
        guard.in(this, () -> assertThat(this.<Object>get(name)).isNull());
    }

    @Then(THAT + GUARD + VARIABLE + " (?:==|is equal to) (true|false)$")
    public void something_is_equal_to(Guard guard, String name, Boolean value) {
        guard.in(this, () -> assertThat(this.<Boolean>get(name)).isEqualTo(value));
    }

    @Then(THAT + GUARD + VARIABLE + IS_COMPARED_TO + "(?: " + A + TYPE + ")?:$")
    public void something_is_compared_(Guard guard, String name, Comparison comparison, Type type, Object value) {
        something_is_compared(guard, name, comparison, type, value);
    }

    @Then(THAT + GUARD + VARIABLE + IS_COMPARED_TO + "(?: " + A + TYPE + ")? " + QUOTED_CONTENT + "$")
    public void something_is_compared(@NotNull Guard guard, String name, Comparison comparison, Type type, Object value) {
        guard.in(this, () -> comparison.compare(get(name), resolvePossiblyTypedObject(type, value)));
    }

    @Given(THAT + GUARD + "the current time is " + TIME + "$")
    public void the_current_time_is(Guard guard, String expression, String timezone, String type) {
        guard.in(this, () -> {
            if (type != null) {
                throw new UnsupportedOperationException(
                        "custom type of time is not supported in this step. The result must be an Instant.");
            }
            Time.set(Time.parse(expression + ofNullable(timezone).map(v -> " (" + v + ")").orElse("")));
            add("now", resolve("{{@now}}"));
        });
    }

    @Given(THAT + GUARD + A_USER + "(?:output|write)s? in " + QUOTED_CONTENT + "(?: " + A + TYPE + ")?:$")
    public void output_in(Guard guard, String sourcePath, Type type, Object sourceValue) {
        guard.in(this, () -> {
            Object value = resolvePossiblyTypedObject(type, sourceValue);
            if (!(value instanceof String)) {
                value = Mapper.toJson(value);
            }
            Path resourcePath;
            try {
                resourcePath = Paths.get(requireNonNull(requireNonNull(this.getClass().getResource("/")).toURI()));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            try {
                Path path = Paths.get(resourcePath.toString(), sourcePath).normalize();
                if (!Paths.get(path.toString()).normalize().startsWith(resourcePath)) {
                    throw new AssertionError("no escape from the resource folder is allowed!");
                }
                File file = new File(path.toString());
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                Files.writeString(path, (String) value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @SneakyThrows
    public String resolve(Object content) {
        if (content instanceof DataTable dataTable) {
            content = Mapper.toJson(dataTable
                    .getTableConverter()
                    .<String, String>toMaps((DataTable) content, String.class, String.class)
                    .stream()
                    .map(map -> map.entrySet().stream().collect(HashMap<String, Object>::new,
                            (newMap, entry) -> newMap.put(entry.getKey(), resolve(entry.getValue())), HashMap::putAll))
                    .map(ObjectSteps::dotToMap)
                    .collect(Collectors.toList()));
        } else if (content instanceof DocString docString) {
            content = docString.getContent();
        } else if (!(content instanceof String)) {
            throw new AssertionError();
        }
        return resolve((String) content);
    }

    @SneakyThrows
    public String resolve(String content) {
        if (content != null && content.contains("{{")) {
            content = handlebars.compileInline(content).apply(dynamicContext);
        }
        return content;
    }

    public void add(String name, Object value) {
        Map<String, Object> host = context;
        if (name.contains(".")) {
            int split = name.indexOf(".");
            do {
                host = (Map<String, Object>) host.computeIfAbsent(name.substring(0, split), k -> new LinkedHashMap<>());
                name = name.substring(split + 1);
                split = name.indexOf(".");
            } while (split > -1);
        }
        host.put(name, value);
    }

    public static <E> E getHost(Object host, String property, boolean instanciateIfNotFound) {
        int split = property.indexOf(".");
        while (split > -1) {
            host = getProperty(host, property.substring(0, split), instanciateIfNotFound);
            property = property.substring(split + 1);
            split = property.indexOf(".");
        }
        return getProperty(host, property, instanciateIfNotFound);
    }

    public <E> E applyToHost(String hostName, boolean instanciateIfNotFound, BiFunction<Object, String, E> function) {
        return applyToHost(context, hostName, instanciateIfNotFound, function);
    }

    public static <E> E applyToHost(Object host, String hostName, boolean instanciateIfNotFound, BiFunction<Object, String, E> function) {
        int split = hostName.lastIndexOf(".");
        if (split > -1) {
            host = getHost(host, hostName.substring(0, split), instanciateIfNotFound);
            hostName = hostName.substring(split + 1);
        }
        return function.apply(host, hostName);
    }

    public <E> E get(String name) {
        return getOrDefault(name, null);
    }

    public <E> E getOrSelf(String name) {
        return (E) getOrDefault(name, name);
    }

    public <E> E getOrDefault(String name, E value) {
        return (E) ofNullable(applyToHost(name, false, (host, property) -> getProperty(host, property, false))).orElse(value);
    }

    public Object resolvePossiblyTypedObject(Type type, Object value) {
        if (type != null) {
            return Mapper.read(resolve(value), type);
        }
        return resolve(value);
    }

    @NotNull
    public static Map<String, ?> dotToMap(Map<String, ?> input) {
        Map<String, Object> output = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            Object object = null;
            if (value instanceof String string) {
                try {
                    if (Mapper.isList(string)) {
                        object = Mapper.read(string, List.class);
                    } else if (Mapper.firstNonWhitespaceCharacterIs(string, '{')) {
                        object = Mapper.read(string, Map.class);
                    } else if (!value.equals("null")) {
                        object = value;
                    }
                } catch (Exception e) {
                    // our assumptions were incorrect, ignoring the error and keeping the value as String
                    object = value;
                }
            }
            if (object != null) {
                Class<?> parameterType = object.getClass();
                applyToHost(output, key, true, (o, s) -> getSetter(o, s, parameterType)).accept(object);
            }
        });
        return output;
    }

    public Consumer<Object> getSetter(String name, Class<?> parameterType) {
        return applyToHost(name, true, (host, property) -> getSetter(host, property, parameterType));
    }


    @NotNull
    public static Consumer<Object> getSetter(Object host, String property, Class<?> parameterType) {
        Matcher isList = LIST.matcher(property);
        if (isList.matches()) {
            List<Object> list = getProperty(host, isList.group(1), true);
            if (list == null) {
                list = new ArrayList<>();
                getSetter(host, isList.group(1), parameterType).accept(list);
            }
            List<Object> target = list;
            return value -> target.set(Integer.parseInt(isList.group(2)), value);
        } else if (host instanceof Map map) {
            return value -> map.put(property, value);
        } else if (hasField(host, property)) {
            return value -> setValue(host, property, value);
        } else if (findMethod(host.getClass(), property, parameterType).isPresent()) {
            return value -> invoke(host, property, value);
        } else if (findMethod(host.getClass(), "set" + capitalize(property), parameterType).isPresent()) {
            return value -> invoke(host, "set" + capitalize(property), value);
        }
        return value -> {
            throw new UnsupportedOperationException("Couldn't assign value %s to host %s".formatted(value, host + "." + property));
        };
    }

    private static <E> E getProperty(Object host, String property, boolean instanciateIfNotFound) {
        if (host == null) {
            return null;
        }
        Matcher isList = LIST.matcher(property);
        Matcher isSubString = SUBSTRING.matcher(property);
        if (isList.matches()) {
            host = getProperty(host, isList.group(1), instanciateIfNotFound);
            if (host != null) {
                if (host instanceof String) {
                    host = Mapper.read((String) host, List.class);
                }
                if (host instanceof List) {
                    return (E) ((List<?>) host).get(Integer.parseInt(isList.group(2)));
                }
                throw new IllegalArgumentException("host is not a list but a " + host.getClass());
            }
        } else if (isSubString.matches()) {
            host = getProperty(host, isSubString.group(1), instanciateIfNotFound);
            if (host != null) {
                if (!(host instanceof String)) {
                    host = Mapper.toJson(host);
                }
                int start = Integer.parseInt(isSubString.group(2));
                int end = Optional.ofNullable(isSubString.group(3))
                        .filter(StringUtils::isNotBlank)
                        .map(Integer::parseInt)
                        .orElse(((String) host).length());
                return (E) ((String) host).substring(start, Math.max(((String) host).length(), end));
            }
        } else if (host instanceof Map map && (map.containsKey(property) || instanciateIfNotFound)) {
            if (map.containsKey(property)) {
                return (E) map.get(property);
            } else if (instanciateIfNotFound) {
                Map<String, Object> newMap = new LinkedHashMap<>();
                map.put(property, newMap);
                return (E) newMap;
            }
        } else if (hasField(host, property)) {
            return getValue(host, property);
        } else if (findMethod(host.getClass(), property).isPresent()) {
            return invoke(host, property);
        } else if (findMethod(host.getClass(), "get" + capitalize(property)).isPresent()) {
            return invoke(host, "get" + capitalize(property));
        } else if (findMethod(host.getClass(), "is" + capitalize(property)).isPresent()) {
            return invoke(host, "is" + capitalize(property));
        } else if (property.matches("\\d+")) {
            if (host instanceof String) {
                host = Mapper.read((String) host, List.class);
            }
            if (host instanceof List) {
                return (E) ((List<?>) host).get(Integer.parseInt(property));
            }
        } else if (host instanceof String) {
            try {
                host = Mapper.read((String) host, Map.class);
                return (E) ((Map<?, ?>) host).get(property);
            } catch (Exception e) {
                // not a map
            }
        }
        return null;
    }

    @SneakyThrows
    public static String load(String resource) {
        if (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        try (InputStream inputStream = ObjectSteps.class.getClassLoader().getResourceAsStream(resource)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            if (inputStream == null) {
                Assert.fail("couldn't find resource: " + resource);
            }
            IOUtils.copy(inputStream, outputStream);
            return outputStream.toString(UTF_8).trim();
        }
    }
}
