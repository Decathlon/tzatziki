package com.decathlon.tzatziki.utils;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.ArrayUtils.addFirst;


@SuppressWarnings("unchecked")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class Types {

    private static final Map<Class<?>, Class<?>> WRAPPER_BY_PRIMITIVE = ImmutableMap.<Class<?>, Class<?>>builder()
        .put(byte.class, Byte.class)
        .put(short.class, Short.class)
        .put(int.class, Integer.class)
        .put(long.class, Long.class)
        .put(float.class, Float.class)
        .put(double.class, Double.class)
        .put(boolean.class, Boolean.class)
        .put(char.class, Character.class)
        .build();

    public static <E> E safeNull(Type type, E value) {
        return safeNull(rawTypeOf(type), value);
    }

    public static <E> E safeNull(Class<?> clazz, E value) {
        if (clazz.isPrimitive() && value == null) {
            value = (E) Array.get(Array.newInstance(clazz, 1), 0);
        }
        return value;
    }

    public static <E> Optional<Class<E>> optionalRawTypeArgumentOf(Type type) {
        return optionalTypeArgumentOf(type, 0).map(Types::rawTypeOf);
    }

    public static Optional<Type> optionalTypeArgumentOf(Type type) {
        return optionalTypeArgumentOf(type, 0);
    }

    public static <E> Optional<Class<E>> optionalRawTypeArgumentOf(Type type, int argumentIndex) {
        return optionalTypeArgumentOf(type, argumentIndex).map(Types::rawTypeOf);
    }

    public static Optional<Type> optionalTypeArgumentOf(Type type, int argumentIndex) {
        return Optional.ofNullable(typeArgumentOf(type, argumentIndex));
    }

    public static <E> Class<E> rawTypeArgumentOf(Type type) {
        return rawTypeOf(typeArgumentOf(type));
    }

    public static Type typeArgumentOf(Type type) {
        return typeArgumentOf(type, 0);
    }

    public static <E> Class<E> rawTypeArgumentOf(Type type, int argumentIndex) {
        return rawTypeOf(typeArgumentOf(type, argumentIndex));
    }

    public static Type typeArgumentOf(Type type, int argumentIndex) {
        if (type instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getActualTypeArguments().length > argumentIndex) {
                return parameterizedType.getActualTypeArguments()[argumentIndex];
            }
            throw new IllegalArgumentException(parameterizedType.getTypeName() + " doesn't have a TypeArgument " + argumentIndex);
        } else {
            return null;
        }
    }

    public static <E> Class<E> rawTypeOf(Type type) {
        return type instanceof ParameterizedType ? (Class<E>) ((ParameterizedType) type).getRawType() : (Class<E>) type;
    }

    public static void assertIsAssignableTo(Object value, Class<?> toClass) {
        if (!Types.isAssignableTo(value.getClass(), toClass)) {
            throw new IllegalArgumentException(toClass + " cannot be assigned value '" + value + "' of type " + value.getClass());
        }
    }

    public static boolean isAssignableToOneOf(Type type, Class<?>... toClasses) {
        return isAssignableToOneOf(rawTypeOf(type), toClasses);
    }

    public static boolean isAssignableToOneOf(Class<?> clazz, Class<?>... toClasses) {
        return Stream.of(toClasses).anyMatch(toClass -> isAssignableTo(clazz, toClass));
    }

    public static boolean isEqualToOneOf(Class<?> clazz, Class<?>... toClasses) {
        return Arrays.asList(toClasses).contains(clazz);
    }

    public static boolean isAssignableTo(Type type, Class<?> toClass) {
        return isAssignableTo(rawTypeOf(type), toClass);
    }

    public static boolean isAssignableTo(Class<?> clazz, Class<?> toClass) {
        return toClass != null && clazz != null && wrap(toClass).isAssignableFrom(wrap(clazz));
    }

    public static Class<?> wrap(Class<?> clazz) {
        return clazz.isPrimitive() ? WRAPPER_BY_PRIMITIVE.get(clazz) : clazz;
    }

    public static <T> boolean hasSuperClass(Class<T> clazz) {
        return clazz.getSuperclass() != null && !clazz.getSuperclass().equals(Object.class);
    }

    public static ParameterizedTypeBuilder parameterized(Class<?> rawType) {
        return new ParameterizedTypeBuilder(rawType);
    }

    public static boolean isAnnotatedWith(Class<?> clazz, Class<? extends Annotation> annotation) {
        return clazz.isAnnotationPresent(annotation) || (Types.hasSuperClass(clazz) && isAnnotatedWith(clazz.getSuperclass(), annotation));
    }

    public static ParameterizedType parameterized(Class<?> rawType, Type parameter, Type... parameters) {
        return new ParameterizedTypeImpl(rawType, addFirst(parameters, parameter));
    }

    public record ParameterizedTypeBuilder(Class<?> rawType) {

        public ParameterizedType of(Type parameter, Type... parameters) {
            return new ParameterizedTypeImpl(rawType, addFirst(parameters, parameter));
        }
    }

    private record ParameterizedTypeImpl(Class<?> rawType, Type[] actualTypeArguments) implements ParameterizedType {

        private ParameterizedTypeImpl(Class<?> rawType, Type[] actualTypeArguments) {
            this.rawType = rawType;
            if (rawType.getTypeParameters().length != actualTypeArguments.length) {
                throw new IllegalArgumentException(
                    "type %s expects %d argument%s but got %d".formatted(
                        rawType, rawType.getTypeParameters().length,
                        rawType.getTypeParameters().length > 1 ? "s" : "",
                        actualTypeArguments.length));
            }
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return rawType.getDeclaringClass();
        }

        @Override
        public String toString() {
            return "%s<%s>".formatted(rawType.getTypeName(),
                Stream.of(actualTypeArguments).map(Type::getTypeName).collect(Collectors.joining(", ")));
        }
    }
}
