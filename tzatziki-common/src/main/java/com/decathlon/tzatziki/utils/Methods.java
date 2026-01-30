package com.decathlon.tzatziki.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Stream;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.util.Collections.synchronizedMap;
import static java.util.Optional.ofNullable;
import static org.burningwave.core.assembler.StaticComponentContainer.Modules;

@Slf4j
@SuppressWarnings("java:S3011") // Allow reflective access to fields & methods
public final class Methods {

    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = synchronizedMap(new HashMap<>());

    private Methods() {
    }

    public static Map<String, Method> byName(Class<?> clazz) {
        return METHOD_CACHE.computeIfAbsent(clazz, Methods::recursivelyFindAllMethodsOf);
    }

    private static Map<String, Method> recursivelyFindAllMethodsOf(Class<?> clazz) {
        Map<String, Method> methods = synchronizedMap(new LinkedHashMap<>());
        recursivelyFindAllMethodsOf(clazz, methods);
        return methods;
    }

    private static synchronized void recursivelyFindAllMethodsOf(Class<?> clazz, Map<String, Method> methods) {
        Stream.of(clazz.getDeclaredMethods())
                .filter(method -> clazz.isAnnotation() || !Modifier.isAbstract(method.getModifiers()))
                .filter(method -> !methods.containsValue(method))
                .forEach(method -> {
                    try {
                        method.setAccessible(true);
                    } catch (Exception e) {
                        log.debug("auto exporting {}!", method.getDeclaringClass().getPackageName());
                        Modules.exportPackageToAllUnnamed("java.base", method.getDeclaringClass().getPackageName());
                        method.setAccessible(true);
                    }
                    methods.put(getMethodSignature(clazz, method.getName(), method.getParameterTypes()), method);
                });
        ofNullable(clazz.getSuperclass()).ifPresent(superClass -> recursivelyFindAllMethodsOf(superClass, methods));
        Stream.of(clazz.getInterfaces()).forEach(interfaceClass -> recursivelyFindAllMethodsOf(interfaceClass, methods));
    }

    @SuppressWarnings("unchecked")
    public static <R> R invoke(Class<?> clazz, String name, Object... parameters) {
        return (R) invoke(clazz, null, name, parameters);
    }

    @SuppressWarnings("unchecked")
    public static <R> R invoke(Object instance, String name, Object... parameters) {
        return (R) invoke(instance.getClass(), instance, name, parameters);
    }

    @SuppressWarnings("unchecked")
    public static <E, R> R invokeUnchecked(E instance, Method method, Object... parameters) {
        return unchecked(() -> (R) method.invoke(instance, parameters), Throwable::getCause);
    }

    @SuppressWarnings("unchecked")
    public static <E, R> R invoke(E instance, Method method, Object... parameters) throws InvocationTargetException, IllegalAccessException {
        return (R) method.invoke(instance, parameters);
    }

    @SuppressWarnings("unchecked")
    private static <E, R> R invoke(Class<?> clazz, E instance, String name, Object... parameters) {
        Class<?>[] parameterTypes = Stream.of(parameters)
                .map(value -> value != null ? value.getClass() : Object.class)
                .toArray(Class<?>[]::new);
        return (R) unchecked(() -> getMethod(clazz, name, parameterTypes).invoke(instance, parameters));
    }

    public static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        return unchecked(() -> findMethod(clazz, name, parameterTypes)
                .orElseThrow(() -> new NoSuchMethodException("method '" + name + "' doesn't exist on " + clazz))
        );
    }

    public static Optional<Method> findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        return ofNullable(byName(clazz).computeIfAbsent(
                getMethodSignature(clazz, name, parameterTypes),
                methodSignature -> byName(clazz).values().stream()
                        .filter(method -> isApplicableMethod(method, name, parameterTypes))
                        .findFirst().orElse(null))
        );
    }

    public static List<Method> findMethodByNameAndNumberOfArgs(Class<?> clazz, String name, int argsCount) {
        return byName(clazz).values().stream()
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == argsCount).toList();
    }

    public static Optional<Method> findMethodByParameterNames(Class<?> clazz, String name, Collection<String> parameterNames) {
        return byName(clazz).values().stream()
                .filter(method -> method.getName().equals(name)
                        && method.getParameterCount() == parameterNames.size()
                        && Arrays.stream(method.getParameters()).map(Parameter::getName).allMatch(parameterNames::contains)).findFirst();
    }

    private static boolean isApplicableMethod(Method method, String name, Class<?>[] parameterTypes) {
        if (name.equals(method.getName()) && method.getParameterTypes().length == parameterTypes.length) {
            boolean isApplicableMethod = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!ClassUtils.isAssignable(parameterTypes[i], method.getParameterTypes()[i])) {
                    isApplicableMethod = false;
                    break;
                }
            }
            return isApplicableMethod;
        }
        return false;
    }

    private static String getMethodSignature(Class<?> clazz, String name, Class<?>[] parameterTypes) {
        return clazz.getCanonicalName() + "." + name + Arrays.toString(parameterTypes);
    }
}
