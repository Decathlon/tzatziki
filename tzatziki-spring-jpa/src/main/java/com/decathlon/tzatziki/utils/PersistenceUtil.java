package com.decathlon.tzatziki.utils;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;

import java.util.*;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

@SuppressWarnings("unchecked")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistenceUtil {
    private static final Map<String, Class<?>> persistenceClassByName = Collections.synchronizedMap(new HashMap<>());

    public static JacksonModule getMapperModule() {
        SimpleModule module = new SimpleModule();
        // Add a modifier to skip uninitialized lazy properties
        module.setSerializerModifier(new HibernateBeanSerializerModifier());
        return module;
    }

    /**
     * Bean serializer modifier that skips uninitialized lazy properties and @Transient annotated properties
     */
    private static class HibernateBeanSerializerModifier extends ValueSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier beanDesc, List<BeanPropertyWriter> beanProperties) {
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
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
            // Skip properties annotated with @Transient
            if (isTransient()) {
                return;
            }

            Object value = get(bean);
            // Skip uninitialized Hibernate proxies and collections
            if ((value instanceof HibernateProxy || value instanceof PersistentCollection) && !Hibernate.isInitialized(value)) {
                // Skip this property entirely
                return;
            }
            super.serializeAsProperty(bean, gen, prov);
        }

        private boolean isTransient() {
            return getMember().hasAnnotation(jakarta.persistence.Transient.class) ||
                    getMember().hasAnnotation(org.springframework.data.annotation.Transient.class);
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
