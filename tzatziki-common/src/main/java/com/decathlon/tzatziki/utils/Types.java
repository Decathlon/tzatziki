package com.decathlon.tzatziki.utils;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;
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

    public static <E> Class<E> rawTypeArgumentOf(Type type) {
        return rawTypeOf(typeArgumentOf(type));
    }

    public static Type typeArgumentOf(Type type) {
        return typeArgumentOf(type, 0);
    }

    public static Type typeArgumentOf(Type type, int argumentIndex) {
        if (type instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getActualTypeArguments().length > argumentIndex) {
                Type targetType = parameterizedType.getActualTypeArguments()[argumentIndex];
                if (targetType instanceof TypeVariable<?> variableTargetType) {
                    Type[] bounds = variableTargetType.getBounds();
                    targetType = bounds.length > 0 ? bounds[0] : targetType;
                }
                return targetType;
            }
            throw new IllegalArgumentException(parameterizedType.getTypeName() + " doesn't have a TypeArgument " + argumentIndex);
        } else {
            return null;
        }
    }

    public static <E> Class<E> rawTypeOf(Type type) {
        return type instanceof ParameterizedType parameterizedType ? (Class<E>) parameterizedType.getRawType() : (Class<E>) type;
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
