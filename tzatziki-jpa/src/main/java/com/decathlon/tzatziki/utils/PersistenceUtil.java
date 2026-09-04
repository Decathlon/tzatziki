package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

@SuppressWarnings("unchecked")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistenceUtil {
    private static final Map<String, Class<?>> persistenceClassByName = Collections.synchronizedMap(new HashMap<>());
    private static final List<Class<? extends Annotation>> transientAnnotations = new CopyOnWriteArrayList<>(
            List.of(jakarta.persistence.Transient.class)
    );

    /**
     * Register an additional annotation class that should be treated as @Transient during serialization.
     * This allows the Spring layer to add org.springframework.data.annotation.Transient without
     * introducing a Spring dependency in the pure JPA module.
     */
    public static void registerTransientAnnotation(Class<? extends Annotation> annotationClass) {
        if (!transientAnnotations.contains(annotationClass)) {
            transientAnnotations.add(annotationClass);
        }
    }

    public static Module getMapperModule() {
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new HibernateBeanSerializerModifier());
        return module;
    }

    /**
     * Bean serializer modifier that skips uninitialized lazy properties and @Transient annotated properties
     */
    private static class HibernateBeanSerializerModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
            List<BeanPropertyWriter> modifiedWriters = new ArrayList<>();
            for (BeanPropertyWriter writer : beanProperties) {
                modifiedWriters.add(new HibernateBeanPropertyWriter(writer));
            }
            return modifiedWriters;
        }
    }

    /**
     * Custom property writer that checks if a property is initialized before serializing and skips @Transient properties
     */
    private static class HibernateBeanPropertyWriter extends BeanPropertyWriter {
        protected HibernateBeanPropertyWriter(BeanPropertyWriter base) {
            super(base);
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (isTransient()) {
                return;
            }

            Object value = get(bean);
            if ((value instanceof HibernateProxy || value instanceof PersistentCollection) && !Hibernate.isInitialized(value)) {
                return;
            }
            super.serializeAsField(bean, gen, prov);
        }

        private boolean isTransient() {
            return transientAnnotations.stream().anyMatch(a -> getMember().hasAnnotation(a));
        }
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
