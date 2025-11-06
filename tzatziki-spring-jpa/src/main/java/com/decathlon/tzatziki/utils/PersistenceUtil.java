package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.databind.Module;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

@SuppressWarnings("unchecked")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistenceUtil {
    private static final Map<String, Class<?>> persistenceClassByName = Collections.synchronizedMap(new HashMap<>());

    public static Module getMapperModule() {
        Class<?> tableClass = getPersistenceClass("Table");
        Class<Module> mapperModuleClass;

        if (tableClass.getPackageName().equals("javax.persistence"))
            mapperModuleClass = unchecked(() -> (Class<Module>) Class.forName("com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module"));
        else
            mapperModuleClass = unchecked(() -> (Class<Module>) Class.forName("com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule"));

        return unchecked(() -> mapperModuleClass.getConstructor().newInstance());
    }

    public static <T> Class<T> getPersistenceClass(String className) {
        return (Class<T>) persistenceClassByName.computeIfAbsent(
                className,
                clazz -> {
                    Class<Object> foundClass;
                    try {
                        foundClass = (Class<Object>) Class.forName("javax.persistence." + className);
                    } catch (ClassNotFoundException e) {
                        foundClass = unchecked(() -> (Class<Object>) Class.forName("jakarta.persistence." + className));
                    }
                    return foundClass;
                }
        );
    }

}
