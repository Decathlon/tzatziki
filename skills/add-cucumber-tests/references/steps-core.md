# User Provided Header
Tzatziki Core module reference.
- ObjectSteps.java defines foundational @Given/@When/@Then patterns for variables, assertions, and data manipulation.
- .feature files demonstrate valid Gherkin structure and DSL usage for core operations.
- These steps are always available in any Tzatziki project.


# Directory Structure
```
tzatziki-core/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                ObjectSteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                objects.feature
                scenario-with-background.feature
README.md
```

# Files

## File: tzatziki-core/src/main/java/com/decathlon/tzatziki/steps/ObjectSteps.java
````java
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
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import io.cucumber.plugin.event.TestSourceParsed;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.decathlon.tzatziki.steps.DynamicTransformers.register;
import static com.decathlon.tzatziki.utils.Comparison.IS_COMPARED_TO;
import static com.decathlon.tzatziki.utils.Fields.*;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Methods.findMethod;
import static com.decathlon.tzatziki.utils.Methods.invoke;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Time.TIME;
import static com.decathlon.tzatziki.utils.Types.wrap;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"unchecked",
        "java:S100", // Allow method names with underscores for BDD steps.
        "java:S5960" // Address Sonar warning: False positive assertion check on non-production code.
})  
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

                                    Pattern indentPattern = Pattern.compile("([\s-]*)" + placeholder);
                                    final Matcher indentMatcher = indentPattern.matcher(strWithPlaceholder);

                                    final String yamlStr = Mapper.toYaml(value);
                                    if (indentMatcher.find()) {
                                        return options.fn(yamlStr.lines().collect(joining("\n" + StringUtils.repeat(" ", indentMatcher.group(1).length()))));
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

                return options.fn(collectionsToConcat.stream().flatMap(Collection::stream).toList());
            })
            // We don't need Sonar to check this line as the input data is trusted (it's coming from the feature file itself)
            .registerHelper("noIndent", (str, options) -> options.handlebars.compileInline(str.toString().replaceAll("(?m)(?:^\\s+|\\s+$)", "").replace("\n", "")).apply(options.context)); // NOSONAR

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
                value = load(getOrSelf(property.substring(1)));
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
        return Methods.invokeUnchecked(context, method, args);
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
                    Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
                    yield Env.export(name, String.valueOf(args[1]));
                }
                default -> Methods.invokeUnchecked(new LinkedHashMap<>(), method, args);
            };
        }));
        add("_properties", Proxy.newProxyInstance(Map.class.getClassLoader(), new Class[]{Map.class}, (proxy, method, args) -> {
            String key = String.valueOf(args[0]);
            return switch (method.getName()) {
                case "get" -> System.getProperty(key);
                case "containsKey" -> System.getProperty(key) != null;
                case "put" -> System.setProperty(key, String.valueOf(args[1]));
                default -> Methods.invokeUnchecked(new LinkedHashMap<>(), method, args);
            };
        }));
        add("_examples", getExamples(scenario));
        add("randomUUID", (Supplier<UUID>) UUID::randomUUID);
        context.remove("_method_output");
    }

    @After(order = Integer.MAX_VALUE)
    public void after() {
        Guard.awaitAsyncSteps();
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
                                .map(Examples.class::cast)
                                .flatMap(examples -> examples.getTableHeader().map(TableRow::getCells).stream())
                                .findFirst().orElseThrow();
                        List<TableCell> values = currentStack.stream()
                                .filter(stack -> stack.getClass().getSimpleName().equals("GherkinMessagesExample"))
                                .map(o -> getValue(o, "tableRow"))
                                .map(TableRow.class::cast)
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

    @When(THAT + GUARD + "(?:the )?method " + VARIABLE + " of " + VARIABLE + " is called")
    public void callMethod(Guard guard, String methodName, String classOrInstance) {
        callMethodWithParams(guard, methodName, classOrInstance, null);
    }

    @When(THAT + GUARD + "(?:the )?method " + VARIABLE + " of " + VARIABLE + " is called with parameters?:$")
    public void callMethodWithParams(Guard guard, String methodName, String classOrInstance, Object parametersStr) {
        guard.in(this, () -> {
            Object host = get(classOrInstance);
            if (host == null)
                callStaticMethodWithReturn(Types.rawTypeOf(TypeParser.parse(classOrInstance)), methodName, parametersStr);
            else callInstanceMethodWithReturn(host, methodName, parametersStr);
        });
    }

    private <E> E callMethodWithReturn(Object host, Class<?> hostClass, String methodName, Object parametersStr) {
        Map<String, Object> parameters = parametersStr == null ? Collections.emptyMap() : Mapper.read(toString(parametersStr), Map.class);
        parameters.replaceAll((key, value) -> resolve(value));

        Optional<Method> methodOpt = Methods.findMethodByParameterNames(hostClass, methodName, parameters.keySet());
        Object methodOutput = methodOpt.isPresent() ? invokeMethodByParameterNames(host, methodOpt.get(), parameters)
                : invokeMethodByParameterCountAndType(host, hostClass, methodName, parameters);

        add("_method_output", methodOutput);
        return (E) methodOutput;
    }

    private <E> E callStaticMethodWithReturn(Class<?> hostClass, String methodName, Object parametersStr) {
        return callMethodWithReturn(null, hostClass, methodName, parametersStr);
    }

    private <E> E callInstanceMethodWithReturn(Object host, String methodName, Object parametersStr) {
        return callMethodWithReturn(host, host.getClass(), methodName, parametersStr);
    }

    private Object invokeMethodByParameterCountAndType(Object host, Class<?> targetClass, String methodName, Map<String, Object> parameters) {
        int parameterCount = parameters.size();
        List<Method> eligibleMethodsWithoutParamTypeCheck = Methods.findMethodByNameAndNumberOfArgs(targetClass, methodName, parameterCount);
        List<Object> rawParameters = parameters.values().stream().toList();

        AtomicReference<Object[]> parsedParametersReference = new AtomicReference<>();
        Method methodToInvoke = findEligibleMethodWithParamCheck(parameterCount, eligibleMethodsWithoutParamTypeCheck, rawParameters, parsedParametersReference);

        return Methods.invokeUnchecked(host, methodToInvoke, parsedParametersReference.get());
    }

    private static Method findEligibleMethodWithParamCheck(int parameterCount, List<Method> eligibleMethods, List<Object> rawParametersStr, AtomicReference<Object[]> parsedParametersReference) {
        return eligibleMethods.stream()
                .sorted(Comparator.<Method>comparingLong(method -> Arrays.stream(method.getParameterTypes())
                        .filter(Class.class::equals)
                        .count()).reversed()
                        // Add deterministic secondary sorting to ensure consistent behavior across JVM versions
                        .thenComparing(method -> {
                            // Prefer methods with more specific parameter types (primitives over Object types)
                            long primitiveCount = Arrays.stream(method.getParameterTypes())
                                    .filter(Class::isPrimitive)
                                    .count();
                            return -primitiveCount; // Negative for descending order
                        })
                        .thenComparing(Method::toGenericString)) // Final tiebreaker for deterministic ordering
                .filter(method -> {
            List<Parameter> methodParameters = Arrays.stream(method.getParameters()).toList();
            try {
                parsedParametersReference.set(IntStream.range(0, parameterCount).boxed()
                        .map(idx -> {
                            Object rawParameter = rawParametersStr.get(idx);
                            Class<?> methodParameterType = methodParameters.get(idx).getType();
                            return wrap(rawParameter.getClass()) == wrap(methodParameterType) ? rawParameter : Mapper.read((String) rawParameter, methodParameterType);
                        })
                        .toArray(Object[]::new));
                return true;
            } catch (Exception e) {
                return false;
            }
        }).findFirst().orElseThrow(() -> new AssertionError("Couldn't find method to call by parameter name or count"));
    }

    private static Object invokeMethodByParameterNames(Object host, Method method, Map<String, Object> parameters) {
        Object[] parsedParameters = Arrays.stream(method.getParameters()).map(parameter -> {
            Object paramValue = parameters.get(parameter.getName());
            Class<?> methodParameterType = parameter.getType();
            return wrap(paramValue.getClass()) == wrap(methodParameterType) ? paramValue : Mapper.read((String) paramValue, methodParameterType);
        }).toArray(Object[]::new);

        return Methods.invokeUnchecked(host, method, parsedParameters);
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

    @Then(THAT + GUARD + VARIABLE + IS_COMPARED_TO + "(?: " + A + TYPE_PATTERN + ")?:$")
    public void something_is_compared_(Guard guard, String name, Comparison comparison, Object value) {
        something_is_compared(guard, name, comparison, value);
    }

    @Then(THAT + GUARD + VARIABLE + IS_COMPARED_TO + "(?: " + A + TYPE_PATTERN + ")? " + QUOTED_CONTENT + "$")
    public void something_is_compared(@NotNull Guard guard, String name, Comparison comparison, Object value) {
        guard.in(this, () -> {
            Object actualObject = get(name);
            String expected = resolve(value);
            comparison.compare(
                    actualObject == null ? null : Mapper.toJson(actualObject),
                    actualObject == null && "null".equals(expected) ? null : Mapper.toJson(expected)
            );
        });
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
                Path path = Paths.get(resourcePath.toString(), resolve(sourcePath)).normalize();
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
        content = toString(content);
        return resolve((String) content);
    }

    private String toString(Object content) {
        if (content instanceof DataTable dataTable) {
            content = Mapper.toJson(dataTable
                    .getTableConverter()
                    .<String, String>toMaps((DataTable) content, String.class, String.class)
                    .stream()
                    .map(map -> map.entrySet().stream().collect(HashMap<String, Object>::new,
                            (newMap, entry) -> newMap.put(entry.getKey(), resolve(entry.getValue())), HashMap::putAll))
                    .map(this::dotToMap)
                    .toList());
        } else if (content instanceof DocString docString) {
            content = docString.getContent();
        }

        return String.valueOf(content);
    }

    @SneakyThrows
    public String resolve(String content) {
        if (content == null) return null;

        if (content.contains("{{")) {
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

    public <E> E getHost(Object host, String property, boolean instanciateIfNotFound) {
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

    public <E> E applyToHost(Object host, String hostName, boolean instanciateIfNotFound, BiFunction<Object, String, E> function) {
        int bracket = hostName.lastIndexOf("(");
        int split = bracket > -1 ? hostName.substring(0, bracket).lastIndexOf(".") : hostName.lastIndexOf(".");
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
    public Map<String, ?> dotToMap(Map<String, ?> input) {
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

            Class<?> parameterType = object == null ? Object.class : object.getClass();
            applyToHost(output, key, true, (o, s) -> getSetter(o, s, parameterType)).accept(object);
        });
        return output;
    }

    public Consumer<Object> getSetter(String name, Class<?> parameterType) {
        return applyToHost(name, true, (host, property) -> getSetter(host, property, parameterType));
    }


    @NotNull
    public Consumer<Object> getSetter(Object host, String property, Class<?> parameterType) {
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

    private <E> E getProperty(Object host, String property, boolean instanciateIfNotFound) {
        if (host == null) {
            return null;
        }
        Matcher isList = LIST.matcher(property);
        Matcher isSubString = SUBSTRING.matcher(property);
        if (isList.matches()) {
            host = getProperty(host, isList.group(1), instanciateIfNotFound);
            if (host != null) {
                if (host instanceof String hostStr) {
                    host = Mapper.read(hostStr, List.class);
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
        } else if (property.matches(TYPE_PATTERN) && TypeParser.hasClass(property)) {
            return (E) TypeParser.parse(property);
        } else if (hasField(host, property)) {
            E value = getValue(host, property);
            if (value instanceof byte[] bytes) {
                return (E) new String(bytes, UTF_8);
            }
            return value;
        } else if (property.matches("\\w+\\(((?:[^)],?)*+)\\)")) {
            String[] splitMethodNameAndArgs = property.split("[()]");
            String methodName = splitMethodNameAndArgs[0];
            String[] parameters = splitMethodNameAndArgs.length == 1 ? new String[0] : splitMethodNameAndArgs[1].split("[, ]+");
            String parametersAsJson = Mapper.toJson(IntStream.range(0, parameters.length).boxed().collect(Collectors.toMap(Function.identity(), idx -> parameters[idx])));

            return host instanceof Class<?> hostClass
                    ? callStaticMethodWithReturn(hostClass, methodName, parametersAsJson)
                    : callInstanceMethodWithReturn(host, methodName, parametersAsJson);
        } else if (findMethod(host.getClass(), property).isPresent()) {
            return invoke(host, property);
        } else if (findMethod(host.getClass(), "get" + capitalize(property)).isPresent()) {
            return invoke(host, "get" + capitalize(property));
        } else if (findMethod(host.getClass(), "is" + capitalize(property)).isPresent()) {
            return invoke(host, "is" + capitalize(property));
        } else if (property.matches("\\d+")) {
            if (host instanceof String hostStr) {
                host = Mapper.read(hostStr, List.class);
            }
            if (host instanceof List) {
                return (E) ((List<?>) host).get(Integer.parseInt(property));
            }
        } else if (host instanceof String hostStr) {
            try {
                host = Mapper.read(hostStr, Map.class);
                return (E) ((Map<?, ?>) host).get(property);
            } catch (Exception e) {
                // not a map
            }
        }
        return null;
    }

    public int getCount(String countAsString) {
        if (countAsString.equals("a")) {
            return 1;
        } else if (countAsString.matches("\\d+")) {
            return Integer.parseInt(countAsString);
        } else {
            return Integer.parseInt(get(countAsString));
        }
    }

    @SneakyThrows
    public static String load(String resource) {
        if (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        try (InputStream inputStream = ObjectSteps.class.getClassLoader().getResourceAsStream(resource)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            if (inputStream == null) {
                Assertions.fail("couldn't find resource: " + resource);
            }
            IOUtils.copy(inputStream, outputStream);
            return outputStream.toString(UTF_8).trim();
        }
    }
}
````

## File: tzatziki-core/src/test/resources/com/decathlon/tzatziki/steps/objects.feature
````
Feature: to interact with objects in the context

  Scenario: we can set a variable in the current context and assert it (short java style version)
    Given that something = "test"
    Then something == "test"

  Scenario: we can set a variable in the current context and assert it (short literal style version)
    Given that something is "test"
    Then something is equal to "test"
    And something is equal to "?e te(?:ts|st)"
    Given that something is "tets"
    Then something is equal to "?e te(?:ts|st)"

  Scenario: we can set a variable in the current context and assert it (long version)
    Given that something is:
      """
      test
      """
    Then something is equal to:
      """
      test
      """

  Scenario: we can assert a typed attribute of a typed object
    Given that map is a Map:
      """yml
      name: test
      attribute: value
      object:
        name: my super object
        attribute: 1
      parameters:
        - value1
        - value2
      blob: | #this will keep the breaks
        some super long
        text that had to be put
        on different lines
      """
#    Then map.name is equal to "test"
#    And map.attribute is equal to a String "value"
#    And map.object.attribute is equal to an Integer "1"
    And map.threshold is equal to 12
    And map.isEmpty is equal to false
    And map.parameters[1] is equal to "value2"
    And map.parameters.1 is equal to "value2"
    And if value is "{{map.parameters.1}}"
    Then value is equal to "value2"
    # regex matching by prefixing the expected content with ?e
    And map.object is equal to:
      """yml
      name: ?e my super .*
      attribute: ?e [0-9]
      """
    # ignore whitespaces by prefixing the expected content with ?w
    And map.blob is equal to "?w some super long text that had to be put on different lines"
    And map.blob is equal to:
      """
      ?w
      some
      super
      long
      text
      that
      had
      to
      be
      put
      on
      different
      lines
      """

    And map.object contains at least:
      """yml
      attribute: 1
      """

    And map.parameters contains at least "value1"
    And map.parameters contains only:
      """yml
      - value1
      - value2
      """
    And map.parameters.toString contains exactly "[value1, value2]"
    And map.parameters contains:
      """yml
      - value1
      - value2
      """

  Scenario: using a contains on a typed object will not fail on default fields
    Given that bob is:
      """yml
      id: 1
      name: Bob
      """
    Then bob contains a User:
      """yml
      name: Bob
      """

  Scenario: list of maps, this should fail
    When that list is a List:
      """yml
      - store_stock: "100"
        item: "767657"
        ecommerce_stock: "0"
        security_stock: "0"
        real_stock: "100"
        store: "2"
      - store_stock: "100"
        item: "400000"
        ecommerce_stock: "0"
        security_stock: "20"
        real_stock: "80"
        store: "1"
      - store_stock: "100"
        item: "767657"
        ecommerce_stock: "0"
        security_stock: "0"
        real_stock: "100"
        store: "3"
      - store_stock: "100"
        item: "767657"
        ecommerce_stock: "0"
        security_stock: "20"
        real_stock: "80"
        store: "1"
      """
    Then it is not true that list contains only:
      """yml
      - store: "1"
        item: "767657"
        security_stock: "12"
      - store: "1"
        item: "400000"
        security_stock: "13"
      - store: "2"
        item: "767657"
        security_stock: "14"
      - store: "3"
        item: "767657"
        security_stock: "0"
      """

  Scenario: assert of fields using flags
    Given that user is a Map:
      """yml
      id: 1
      name: Bob
      uuid: c8eb85bc-c7fc-4586-9f91-c14e7c9d473e
      age: 20
      created: {{{[@10 mins ago]}}}
      """
    Then user.age == "?eq 20"
    Then user.age == "?== 20"
    Then user.age == "?gt 19"
    Then user.age == "?> 19"
    And user.age == "?ge 20"
    And user.age == "?>= 20"
    And user.age == "?lt 21"
    And user.age == "?< 21"
    And user.age == "?le 20"
    And user.age == "?<= 20"
    And user.age == "?not 0"
    And user.age == "?!= 0"
    And user.name == "?not null"
    And user.created == "?before {{@now}}"
    And user.created == "?after {{{[@20 mins ago]}}}"
    And user.name == "?base64 Qm9i"
    And user.uuid == "?isUUID"
    And user contains:
      """yml
      uuid: ?isUUID
      name: ?e B.*
      """

  Scenario: we can set the time in our tests
    Given that the current time is the first Sunday of November 2020 at midnight
    Then now is equal to "2020-11-01T00:00:00Z"

  Scenario: all fields of a dot map are evaluated
    Given that users is:
      | id | name | created              |
      | 1  | Bob  | {{{[@10 mins ago]}}} |
    Then users[0].created == "{{{[@10 mins ago]}}}"
    And users[0] is equal to:
      """yml
      id: 1
      name: Bob
      created: {{{[@10 mins ago]}}}
      """

  Scenario: assert of deep nested lists
    Given that deepNested is a List:
      """yml
      - id:
          shipping_id: ABC
          container_id: XYZ
        tracking_status: PICKED
        picked_items:
          picked_items:
          - sku_code: "767657"
            picked_quantity: 1
            rfids:
            - "3039606203C7F24000053621"
          - sku_code: "2357060"
            picked_quantity: 3
            rfids:
            - "3039606203C7F24000053622"
            - "3039606203C7F24000053623"
            - "3039606203C7F24000053624"
      """
    Then deepNested contains only:
      """yml
      - id:
          shipping_id: ABC
          container_id: XYZ
        tracking_status: PICKED
        picked_items:
          picked_items:
          - sku_code: "767657"
            picked_quantity: 1
            rfids:
            - "3039606203C7F24000053621"
          - sku_code: "2357060"
            picked_quantity: 3
            rfids:
            - "3039606203C7F24000053622"
            - "3039606203C7F24000053623"
            - "3039606203C7F24000053624"
      """
  Scenario: handling bidirectional relationships
    Given that order is an com.decathlon.tzatziki.cyclicgraph.Order:
        """yml
        id: 1
        name: order1
        orderLines:
          - id: 1
            sku: abcdef
            quantity: 42
          - id: 2
            sku: ghijkl
            quantity: 21
        """
    And orderLines references order
    Then order is equal to:
      """yml
        id: 1
        name: order1
        orderLines:
          - id: 1
            sku: abcdef
            quantity: 42
          - id: 2
            sku: ghijkl
            quantity: 21
      """
    # The JsonBackReference annotation on the OrderLine class prevents infinite recursion to happen in this situation
  
  @someTag
  Scenario: we can access the tags in a scenario
    * _scenario.sourceTagNames[0] == "@someTag"


  Scenario Template: we can access the tags in a scenario template
    * _scenario.sourceTagNames[0] == "@<firstTag>"
    * _scenario.sourceTagNames[1][5-] == "<arg>"

    @test1 @arg=value1
    Examples:
      | firstTag | arg    |
      | test1    | value1 |

    @test2 @arg=value2
    Examples:
      | firstTag | arg    |
      | test2    | value2 |

  Scenario Template: we can write to and read from files
    Given that dateAppendedFilePath is:
    """
    <path>-{{{[@now as a formatted date YYYY-MM-dd'T'HH_mm_ss]}}}
    """
    Given that we output in "{{{dateAppendedFilePath}}}":
      """yml
      id: 1
      name: bob
      """
    When bob is "{{{[&dateAppendedFilePath]}}}"
    Then bob is equal to:
      """yml
      id: 1
      name: bob
      """

    Examples:
      | path                    |
      | bob.yaml                |
      | test1/bob.yaml          |
      | /test2/test/../bob.yaml |

  Scenario: we can use file as templates
    Given that userTemplatePath is:
    """
    templates/userTemplate.yaml
    """
    When name is "Alice"
    When alice is:
    """
    {{{[&userTemplatePath]}}}
    """
    Then alice is equal to:
      """yml
      id: 1
      name: Alice
      """
    
  Scenario: we cannot write a file outside the resource folder of the build
    * it is not true that we output in "../../bob.yaml":
      """yml
      id: 1
      name: bob
      """

  Scenario: parse negative integer values in tables
    Given that users is a List<User>:
      | id | name | score |
      | 1  | bob  | -42   |
    Then users[0] is equal to a User:
      """yml
      id: 1
      name: bob
      score: -42
      """

  Scenario Template: we can ignore a step based on a predicate
    Given that bob is a Map:
      """yml
      id: 1
      name: Bob
      """
    Then if bob.name != Bob => bob.id is 3
    And if now before {{{[@2 mins ago]}}} => bob.id is 4
    But if bob.id == 1 && <incrementId> == true => bob.id is 2
    And it is not true that a SkipStepException is thrown when if bob.name == Bob => bob.name == "Toto"
    Then bob.id == <expectedId>
    And bob is equal to:
      """yml
      id: <expectedId>
      name: Bob
      """

    Examples:
      | incrementId | expectedId |
      | true        | 2          |
      | false       | 1          |

  Scenario: a step can be green because it failed
    Given that bob is a User:
    """yml
    id: 1
    name: bob
    """
    Then it is not true that bob.id == 2

  Scenario: testing a null field
    Given that bob is a User:
    """yml
    id: 1
    name: bob
    """
    Then bob contains:
    """yml
    score: ?isNull
    """

  Scenario: testing a null field of a table
    Given that users is:
      | id | name |
      | 1  |      |
    Then users contains:
      | id | name    |
      | 1  | ?isNull |
    And users[0].id is equal to 1
    And users[0].name is equal to null

  Scenario: we can compare json objects in table
    * details is a Map:
      """
        {"key":"value"}
      """
    Given that users is:
      | id | detail          |
      | 1  | {"key":"value"} |
    Then users contains:
      | id | detail      |
      | 1  | {{details}} |

  Scenario: we can compare a serialized Java Array String that starts with a [ but is actually not a Json document without breaking the Mapper
    Given that content is a Map:
      """yml
      message: "[ConstraintViolationImpl{interpolatedMessage='size must be between 1 and 2147483647', messageTemplate='{javax.validation.constraints.Size.message}'}]"
      """
    Then content is equal to:
      """yml
      message: "[ConstraintViolationImpl{interpolatedMessage='size must be between 1 and 2147483647', messageTemplate='{javax.validation.constraints.Size.message}'}]"
      """
    And content is equal to:
      """yml
      message: ?e .*constraints\.Size\.message.*
      """

  Scenario: we can access the ENVs from the test
    # see com.decathlon.tzatziki.utils.Env to see how we can set an environment variable at runtime
    Given that _env.TEST = "something"
    Then _env.TEST is equal to "something"

  Scenario: we can access the system properties from the test
    Given that _properties.test = "something"
    Then _properties.test is equal to "something"

  Scenario: we can test that a value is one of a list of values
    Given that object is a Map:
      """yml
      property: value1
      """
    Then object.property == "?in [value1, value2]"
    Then object.property == "?notIn [value3, value4]"
    And object contains:
      """yml
      property: ?in [value1, value2]
      """
    And object contains:
      """yml
      property: ?notIn [value3, value4]
      """

  Scenario: we can test that a value can be parsed as a given type
    Given that object is a Map:
      """yml
      date: 2021-05-29T00:00:00Z
      notAdate: value1
      age: 23
      distance: 2.3
      """
    Then object.date == "?is Instant"
    And object contains:
      """yml
      date: ?is java.time.Instant
      """
    And it is not true that object.notAdate == "?is Instant"
    And it is not true that object.date == "?is Date"
    And object.age == "?is Integer"
    And object.distance == "?is Number"
    And it is not true that object.distance == "?is Boolean"

  Scenario: we can test that value contains another one, or not
    Given that object is a Map:
      """yml
      first: some really long sentence that contains the word bird, you know ...
      second: some really long sentence that doesn't contain the famous word, you know ...
      """
    Then object.first == "?contains bird"
    And object.first == "?e .*bird.*"
    And object.second == "?doesNotContain bird"

  Scenario: we can use a variable as a template
    Given that template is:
      """yml
      property: "{{value}}"
      """
    And that value is "test"
    Then template is:
      """yml
      property: "test"
      """
    But if value is "test2"
    Then template is:
      """yml
      property: "test2"
      """

  Scenario: we can define a variable while templating it
    Given that object is a Map:
  """yml
      property: "{{{[id: randomUUID.get]}}}"
      time: "{{{[created_at: @now]}}}"
      """
    Then id == "?isUUID"
    And created_at == "{{@now}}"

  Scenario Template: we can use the conditional helpers in handlebar
    Given that object is a Map:
      """hbs
      {{#lt <test>}}
      is: true
      {{else}}
      is: false
      {{/lt}}
      """
    Then object.is is equal to "<result>"

    Examples:
      | test | result |
      | 0 5  | true   |
      | 10 5 | false  |

  Scenario: we can use the math helper to do math stuff in the tests
    Given that object is a Map:
      """yml
      propertyA: 1
      """
    Then object.propertyA is "{{math object.propertyA '+' 1}}"
    When object.propertyA == "2"

  Scenario: we can compare more in the guard
    When object is a Map:
      """yml
      propertyA: 1
      """
    Then if 3 == 3 => object.propertyA == 1


  Scenario: comparing list orders with null values
    Given that list is a List:
      """yml
      [null, 2, null]
      """
    Then list contains in order:
      """yml
      [null, 2, null]
      """
    But it is not true that list contains in order:
      """yml
      [2, null, null]
      """

  Scenario: we can assert that something is true within a given time
    Given that after 100ms bob is:
      """yml
      id: 1
      user: bob
      """
    Then within 200ms bob is equal to:
      """yml
      id: 1
      user: bob
      """

  Scenario Template: we can template recursively from scenario examples
    Given that value is "some value"
    And that templated is:
      """
      <placeholder>
      """
    Then templated is equal to:
      """
      some value
      """
    Examples:
      | param     | placeholder             |
      | {{value}} | {{{[_examples.param]}}} |
      | {{value}} | {{value}}               |

  Scenario: we can set an attribute on a map
    Given that bob is a Map:
      """yml
      id: 1
      """
    And that bob.name is "bob"
    And that bob.attributes is a List:
      """yml
      - name: test
      - age: 12
      """
    Then bob.name is equal to "bob"
    And bob.attributes[1].age is equal to 12

    But if bob.attributes[1].age is 15
    Then bob.attributes[1].age is equal to 15

  Scenario: we can set an attribute on an object
    Given that user is a User:
      """yml
      id: 1
      name: bob
      """
    Then user.name is equal to "bob"
    But if user.name is "lisa"
    Then user.name is equal to "lisa"

  Scenario: we can set an attribute on an object in a list
    Given that users is a List<User>:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      """
    Then users[0].name is equal to "bob"
    But when users[0].name is "tom"
    Then users[0].name is equal to "tom"

  Scenario: we can lazily create a nested map
    Given that map.prop1.prop2 is "test"
    Then map is equal to:
      """yml
      prop1:
        prop2: test
      """

  Scenario: we can call a method with parameter
    Given that users is a List<User>:
      """json
      []
      """
    And that users.add is called with a User:
      """yml
      id: 1
      name: bob
      """
    Then users[0].name is equal to "bob"

  Scenario: we can safely template something that doesn't exist
    When someVariable is "{{{[something.that.is.definitely.not.there]}}}"
    Then someVariable is equal to "something.that.is.definitely.not.there"

  Scenario: we can assert that something is true during a given period
    Given that bob is:
      """yml
      id: 1
      user: bob
      """
    But that after 200ms bob is null

    Then during 100ms bob is equal to:
      """yml
      id: 1
      user: bob
      """
    And within 150ms bob is equal to null


  Scenario: we can create a null typed object
    Given that user is a User:
      """
      null
      """
    Then user is equal to null

  Scenario: we can expect an exception using guards
    Then an exception MismatchedInputException is thrown when badlyTypedObject is a User:
      """json
      a terribly incorrect json
      """
    And exception.message is equal to "?contains Cannot construct instance of `com.decathlon.tzatziki.User`"

  Scenario: we can expect an unnammed exception using guards
    Then a MismatchedInputException is thrown when badlyTypedObject is a User:
      """json
      a terribly incorrect json
      """
    # default name for the exception is _exception
    And _exception.message is equal to "?contains Cannot construct instance of `com.decathlon.tzatziki.User`"

  Scenario: we can chain multiple guards
    Given that working is "true"
    Then within 50ms it is not true that working is equal to "false"

  Scenario: some additional chain guards
    Given that test is "true"
    Then it is not true that test is equal to "false"
    And it is not true that it is not true that test is equal to "true"
    But it is not true that it is not true that it is not true that test is equal to "false"
    And it is not true that it is not true that it is not true that it is not true that test is equal to "true"
    And it is not true that within 100ms it is not true that during 100ms it is not true that test is equal to "true"

  Scenario Template: some additional conditional chain guards
    Given that if <shouldDoTask> == true => after 100ms taskDone is "true"
    Then if <shouldDoTask> == true => within 150ms taskDone is equal to "true"

    Examples:
      | shouldDoTask |
      | true         |
      | false        |

  Scenario: we can also chain conditional in any order
    Given that ran is "false"
    And that if 1 != 1 => if 1 != 2 => ran is "true"
    Then if 1 != 2 => if 1 != 1 => ran is equal to "true"
    And ran is equal to "false"

  Scenario: concatenate multiple arrays using handlebars helper
    Given that myFirstArray is:
    """
    array:
      - payload: firstItem
      - payload: secondItem
    """
    And that mySecondArray is:
    """
    - payload: thirdItem
    - payload: fourthItem
    """
    And that myThirdArray is:
    """
    - payload: fifthItem
    - payload: sixthItem
    """

    When resultArray is:
    """
    {
      {{#concat [myFirstArray.array] mySecondArray myThirdArray}}
      "myArray": {{this}}
      {{/concat}}
    }
    """

    Then resultArray is equal to:
    """
    {"myArray": [{"payload":"firstItem"},{"payload":"secondItem"},{"payload":"thirdItem"},{"payload":"fourthItem"},{"payload":"fifthItem"},{"payload":"sixthItem"}]}
    """

  Scenario: array to templated-string array with handlebars helper
    Given that rawItems is:
    """
    items:
      - id: item1
        value: value1
      - id: item2
        value: value2
    """

    When that wrappedItems is a List<ListWrapper<java.lang.Object>>:
    """hbs
    {{#foreach [rawItems.items]}}
    - wrapper:
        - {{this}}
    {{/foreach}}
    """

    Then wrappedItems is equal to:
    """
    - wrapper:
        - id: item1
          value: value1
    - wrapper:
        - id: item2
          value: value2
    """

  Scenario: noIndent helper can be used to help increase readability in scenario while allowing handlebars to properly interpret the String
    Given that helloWorld is "Hello World"
    Given that chainedMethodCalls is:
    """
    {{noIndent '{{[

    helloWorld
      .replaceAll(e, 3)
      .replaceAll(l, 1)
      .replaceAll(
        o,
        0
      )

    ]}}'}}
    """
    Then chainedMethodCalls is equal to:
    """
    H3110 W0r1d
    """

  Scenario Template: else guard allows to run a step only if the latest-evaluated condition was false
    Given that condition is "<ifCondition>"
    When if <ifCondition> == true => ran is "if"
    * else ran is "else"
    Then ran is equal to "<expectedRan>"

    Examples:
      | ifCondition | expectedRan |
      | true        | if          |
      | false       | else        |

  Scenario: DataTable can have null value which can be asserted
    Given that unknownPersons is:
      | name |
      |      |
    Then unknownPersons is equal to:
    """
    - name: null
    """
    And unknownPersons is equal to:
      | name |
      |      |

  Scenario: we can call a method without parameters
    Given that aList is a List:
    """
    - hello
    - mr
    """
    When the method size of aList is called
    Then _method_output == 2

  Scenario Template: we can call a method by name providing parameters and assert its return
    Given that aListWrapper is a ListWrapper<String>:
    """
    wrapper:
    - hello
    - bob
    """
    When the method <methodCalled> of aListWrapper is called with parameters:
    """
    <params>
    """
    Then _method_output is equal to:
    """
    <expectedReturn>
    """
    And aListWrapper is equal to:
    """
    <expectedListState>
    """

    Examples:
      | methodCalled | params                     | expectedReturn | expectedListState                |
      | add          | {"element":"mr","index":1} | null           | {"wrapper":["hello","mr","bob"]} |

  Scenario: we can call a method providing parameters by name and assert its exception through guard
    Given that aList is a List:
    """
    - hello
    - bob
    """
    Then an exception java.lang.IndexOutOfBoundsException is thrown when the method get of aList is called with parameter:
    """
    bobby: 2
    """
    And exception.message is equal to:
    """
    Index 2 out of bounds for length 2
    """

  Scenario Template: we can also call methods by parameter order if there is multiple candidates for the given parameter count
    Given that aListWrapper is a ListWrapper<String>:
    """
    wrapper:
    - hello
    - bob
    """
    When the method getOrDefault of aListWrapper is called with parameters:
    """
    bobby: 3
    tommy: <secondParameter>
    """
    Then _method_output is equal to:
    """
    <expectedReturn>
    """
    Examples:
      | secondParameter | expectedReturn |
      | 0               | hello          |
      | fallbackTommy   | fallbackTommy  |

  Scenario: we can call a static method by specifying a class
    When the method read of com.decathlon.tzatziki.utils.Mapper is called with parameters:
    """
    objectToRead: |
      id: 1
      name: bob
    wantedType: com.decathlon.tzatziki.User
    """
    Then _method_output.class is equal to:
    """
    com.decathlon.tzatziki.User
    """
    And _method_output is equal to:
    """
    id: 1
    name: bob
    score: null
    """

  Scenario: we can call a method inline within a variable assignment
    When users is a List<String>:
    """
    - toto
    - bob
    """
    And bobbyVar is "bobby"
    When previousUserAtPosition is "{{{[users.set(1, {{{bobbyVar}}})]}}}"
    Then previousUserAtPosition is equal to "bob"
    And users is equal to:
    """
    - toto
    - bobby
    """
    When previousUserAtPosition is "{{{[users.set(0, stringUser)]}}}"
    Then previousUserAtPosition is equal to "toto"
    And users is equal to:
    """
    - stringUser
    - bobby
    """

  Scenario: we can call a method for a property assignment either on an instance or statically (Mapper)
    When users is a List<String>:
    """
    - toto
    - bob
    """
    And that usersProxy is a Map:
    """
    users: {{{users}}}
    bobIsInBefore: {{{[users.contains(bob)]}}}
    lastRemovedUser: {{{[users.set(1, stringUser)]}}}
    bobIsInAfter: {{{[users.contains(bob)]}}}
    lastAddedUser: {{{[users.get(1)]}}}
    isList: {{{[Mapper.isList({{{users}}})]}}}
    """
    Then usersProxy is equal to:
    """
    users:
    - toto
    - bob
    bobIsInBefore: true
    lastRemovedUser: bob
    bobIsInAfter: false
    lastAddedUser: stringUser
    isList: true
    """
    But users is equal to:
    """
    - toto
    - stringUser
    """

  Scenario: we can use dot-notation to specify nested fields
    Given that yamlNests is a List<Nest>:
    """
    - subNest.bird.name: Titi
    - subNest.subNest:
        subNest.bird.name: Tutu
        bird.name: Tata
    """
    Then yamlNests contains only:
    """
    - subNest:
        bird:
          name: Titi
    - subNest:
        subNest:
          subNest:
            bird:
              name: Tutu
          bird:
            name: Tata
    """
    Given that jsonNests is a List<Nest>:
    """
    [
      {
        "subNest.bird.name": "Titi"
      },
      {
        "subNest.subNest": {
          "subNest.bird.name": "Tutu",
          "bird.name": "Tata"
        }
      }
    ]
    """
    And jsonNests contains only:
    """
    [
      {
        "subNest": {
          "bird": {
            "name": "Titi"
          }
        }
      },
      {
        "subNest": {
          "subNest": {
            "subNest": {
              "bird": {
                "name": "Tutu"
              }
            },
            "bird": {
              "name": "Tata"
            }
          }
        }
      }
    ]
    """

  Scenario: a nested list with dot notation
    Given that listWithNestedList is a List:
    """
    - element.nestedList:
      - element.message: a message
      message: another message
    """
    Then listWithNestedList is equal to:
    """
    - element:
        nestedList:
        - element:
            message: a message
      message: another message
    """

  Scenario: dot notation should only take dot notation for keys (even if the value contains dots and colons)
    Given that object is:
    """
    current_time.timestamp: '2021-08-01T12:30:00.000+02:00'
    """
    Then object is equal to:
    """
    current_time:
      timestamp: '2021-08-01T10:30:00Z'
    """

  Scenario: contains should work even if an expected with a map is matched against a non-map (empty string for eg.)
    Given that aList is a List<Map>:
    """
    - id: 1
      value: ''
    - id: 2
      value.name: toto
    """
    Then aList contains only:
    """
    - id: 2
      value.name: toto
    - id: 1
      value: ''
    """

  Scenario: custom flags can be created in Cucumber runner and used in assertions (note that the isEvenAndInBounds flag is custom and wont be available for you)
    Given that aList is a List<Map>:
    """
    - id: 1
      value: ''
    - id: 2
      value.name: toto
    """
    Then aList contains only:
    """
    - id: 1
      value: ''
    - id: ?isEvenAndInBounds 1 | 2
      value.name: toto
    """

  @ignore @run-manually
  Scenario: an async steps failing should generate an error in the After step
    Given that after 10ms value is equal to "test"
````

## File: tzatziki-core/src/test/resources/com/decathlon/tzatziki/steps/scenario-with-background.feature
````
Feature: a feature with a background that we template from the examples in the scenario

  Background:
    Given that map is a Map:
      """yml
      property: {{{[_examples.testValue]}}}
      """
    And if map.property == 4 => map.property is equal to 4

  Scenario Template: test 1 and 2
    Then map.property is equal to <testValue>

    Examples:
      | testValue |
      | 1         |
      | 2         |

  Scenario Template: test 3 and 4
    Then map.property is equal to <testValue>
    And if map.property == 3 => map.property is equal to 3

    Examples:
      | testValue |
      | 3         |
      | 4         |

  Scenario: another scenario that doesn't have examples
    Then map.property is equal to "_examples.testValue"
````

## File: README.md
````markdown
Tzatziki Steps Library
======

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.decathlon.tzatziki/tzatziki-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.decathlon.tzatziki/tzatziki-parent)
![Build](https://github.com/Decathlon/tzatziki/workflows/Build/badge.svg)
[![codecov](https://codecov.io/gh/Decathlon/tzatziki/branch/main/graph/badge.svg)](https://codecov.io/gh/Decathlon/tzatziki)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![lifecycle: beta](https://img.shields.io/badge/lifecycle-beta-509bf5.svg)

This project is a collection of ready-to-use Cucumber steps making it easy to TDD Java microservices by focusing on an
outside-in testing strategy.

## Wait, Cucumber?

*You are a Cucumber veteran? ... jump directly to [Content of this project](#content-of-this-project)*

Otherwise, here is what [wikipedia](https://en.wikipedia.org/wiki/Cucumber_(software)) says:

> Cucumber is a software tool used by computer programmers for testing other software.
> It runs automated acceptance tests written in a behavior-driven development (BDD) style.
> Central to the Cucumber BDD approach is its plain language parser called Gherkin.
> It allows expected software behaviors to be specified in a logical language that customers can understand.
> As such, Cucumber allows the execution of feature documentation written in business-facing text.

*What does it mean to us developers?*

Cucumber provides a mapping between humanly readable test files written in a language called Gherkin and their JUnit
implementations. You can think about it as a partition that will execute pieces of JUnit code.

*Why using Cucumber?*

By creating a separation between a test expression and its implementation, the resulting Cucumber test tends to be a bit
more readable than its JUnit counterpart. Additionaly, the reusability of each JUnit implementation is really high, and
over time only the Gherkin needs to be added to test a new feature.

*Okay ... so how does it work?*

### Getting started with Cucumber in 5 mins

The Cucumber tests are written in `.feature` files. Most of the IDEs have support for writting, running and debugging
cucumber tests. Since deep down they are just JUnit tests, once they are running everything should be the same: code
coverage, reporting etc.

The structure of a `.feature` file is the following:

```gherkin
Feature: the name of your feature
  Here you can put some comments describing your feature

  Background: some stuff that needs to be done for every scenario
    * a system is running

  @this-is-a-tag
  Scenario: Change a state in the system
  As a User I expect to go from A to C if B happens

    Given a state A
    When B happens
    # we can also put comments if things need a bit of explanation
    Then C is the new state of the system

  Scenario: some other scenario
  ...
```

The lines starting with `Given, When, Then` are called Steps. Additional Steps keywords are `And` and `But` (`*` is also
accepted). Those keywords don't really have a functional meaning, they are just there for us to write nice tests. We
could start every step with `*` and the output of the test would be exactly the same. However, you should choose the one
fitting the most the intent of the step you are writing. A big part of the idea behind using Gherkin, is that the tests
are the specifications of the code, so it should be enough to read them to understand the product they test.

An optional `Background` section can be added at the beginning. The steps in it will be repeated before any scenario in
the file, like a method annotated with `@org.junit.jupiter.api.BeforeEach`.

Each Step has an implementation in plain Java that is annotated with a regular expression matching the step.

So for example:

```gherkin
Given that we do something
```

will have the following implementation:

```java
@Given("that we do something")
public void do_something(){
  // do something here
}
```

Cucumber can extract parameters directly from a step so that:

```gherkin
Given a user named "bob"
```

can be implemented as:

```java
@Given("a user named \"(.*)\"")
public void a_user_named(String name){
  // create a user with that name
}
```

But it also supports multiline arguments:

```gherkin
Given the following configuration file:
  """
  property: value
  """
```

```java
@Given("the following configuration file:")
public void the_following_configuration_file(String content){
  // do something with the file content
}
```

as well as tables:

```gherkin
Given the following users:
  | id  | name    |
  | 1   | bob     |
  | 2   | alice   |
```

```java
@Given("the following users")
public void the_following_users(List<Map<String, String>> users){
  // do something with those users
}
```

Those Java methods need to be added to a Steps class, typically something like `LocalSteps`. Keep in mind that for
technical reasons Cucumber will not allow you to extend those steps. Instead, the framework will enforce composition,
and if any class extending a Steps class is detected, an exception will be thrown.

Cucumber also comes with support for injection frameworks, so all your dependencies will be properly instantiated and
injected at runtime, per scenario.

Note that your `@org.junit.jupiter.api.BeforeEach` and `@org.junit.jupiter.api.AfterEach` annotations won't work in your steps. You need to use the
Cucumber equivalent: `@cucumber.api.java.Before` and `@cucumber.api.java.After`

Example:

```java
public class BaseSteps {

    @Before
    public void before() {
        // something to run before each scenario
    }

    @Given("that we do something")
    public void do_something() {
        // do something here
    }
}

public class LocalSteps {

    private final BaseSteps baseSteps;

    public LocalSteps(BaseSteps baseSteps) {
        this.baseSteps = baseSteps;
    }

    @Given("a user named \"(.*)\"")
    public void a_user_named(String name) {
        baseSteps.do_something();
        // create a user with that name
    }

    @Given("the following users")
    public void the_following_users(List<Map<String, String>> users) {
        // do something with those users
    }

    @After
    public void after() {
        // something to run after each scenario
    }
}
```

Finally, in order to have JUnit execute our Cucumber tests we need a runner:

```java
package com.yourcompany.yourproject;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("com/yourcompany/yourproject")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "pretty")
public class CucumberTest {
}
```

By default, cucumber will look for `.feature` files in the same directory structure than the java runner. However, this
can be configured using the `features` property on the `@io.cucumber.junit.CucumberOptions` annotation. In addition, it
will also look for Java classes containing steps next to the runner and this can also be configured by using the `glues`
property on the same annotation, as illustrated below:

```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.yourcompany.yourproject.features,com.decathlon.tzatziki.steps")
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "not @ignore")
public class CucumberTest {
}
```

> Tip:
> Sometimes it can be hard to come up with the implementation steps...
> but if you start by typing your new step in your feature file and then execute the scenario, Cucumber will output an implementation for you:

```
  Undefined step: Given something else that is not yet implemented

  Skipped step

  Skipped step

  Skipped step

  1 Scenarios (1 undefined)
  5 Steps (3 skipped, 1 undefined, 1 passed)
  0m0.250s


  You can implement missing steps with the snippets below:

  @Given("^something else that is not yet implemented")
  public void something_else_that_is_not_yet_implemented() throws Throwable {
      // Write code here that turns the phrase above into concrete actions
      throw new PendingException();
  }
```

## Content of this project

This repository contains several libraries, each one having its own tutorial and documentation when applicable:

- [tzatziki-mapper](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-mapper) : module containing only the Mapper
  interface.
- [tzatziki-jackson](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-jackson) : Jackson implementation of the
  Mapper.
- [tzatziki-common](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-common) : dependency module containing the
  base classes for the core library, but without cucumber.
- [tzatziki-core](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-core) : the core library, provides support of
  our test instances as well as input/output and time management.
- [tzatziki-logback](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-logback) : the logging library, provides
  support for dynamically configuring the log levels in your tests.
- [tzatziki-http](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-http) : http library encapsulating both
  rest-assured and WireMock.
- [tzatziki-spring](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring) : base library to start a spring
  service
- [tzatziki-spring-jpa](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring-jpa) : support for spring jpa to
  insert and assert data in the database.
- [tzatziki-spring-kafka](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring-kafka) : support for spring
  kafka listener and consumers.

## Support

We welcome [contributions](https://github.com/Decathlon/tzatziki/tree/main/CONTRIBUTING.md), opinions, bug reports and
feature requests!
````
