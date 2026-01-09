package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

@SuppressWarnings("unchecked")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistenceUtil {
    private static final Map<String, Class<?>> persistenceClassByName = Collections.synchronizedMap(new HashMap<>());

    public static Module getMapperModule() {
        SimpleModule module = new SimpleModule();
        // Register a custom serializer for Hibernate proxies
        module.addSerializer(HibernateProxy.class, new HibernateProxySerializer());
        // Register a custom serializer for Hibernate persistent collections
        module.addSerializer(PersistentCollection.class, new PersistentCollectionSerializer());
        // Add a modifier to skip uninitialized lazy properties
        module.setSerializerModifier(new HibernateBeanSerializerModifier());
        return module;
    }

    /**
     * Custom serializer that skips uninitialized Hibernate proxies
     */
    private static class HibernateProxySerializer extends StdSerializer<HibernateProxy> {
        protected HibernateProxySerializer() {
            super(HibernateProxy.class);
        }

        @Override
        public void serialize(HibernateProxy value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            // Only serialize if the proxy is already initialized
            if (Hibernate.isInitialized(value)) {
                // Get the actual implementation and serialize it
                Object impl = Hibernate.unproxy(value);
                provider.defaultSerializeValue(impl, gen);
            } else {
                // Skip uninitialized proxies by writing null
                gen.writeNull();
            }
        }
    }

    /**
     * Custom serializer that skips uninitialized Hibernate collections
     */
    private static class PersistentCollectionSerializer extends StdSerializer<PersistentCollection> {
        protected PersistentCollectionSerializer() {
            super(PersistentCollection.class);
        }

        @Override
        public void serialize(PersistentCollection value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            // Only serialize if the collection is already initialized
            if (Hibernate.isInitialized(value)) {
                // Serialize the initialized collection
                provider.defaultSerializeValue(value, gen);
            } else {
                // Skip uninitialized collections by writing null
                gen.writeNull();
            }
        }
    }

    /**
     * Bean serializer modifier that skips uninitialized lazy properties
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
     * Custom property writer that checks if a property is initialized before serializing
     */
    private static class HibernateBeanPropertyWriter extends BeanPropertyWriter {
        protected HibernateBeanPropertyWriter(BeanPropertyWriter base) {
            super(base);
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            Object value = get(bean);
            // Skip uninitialized Hibernate proxies and collections
            if (value instanceof HibernateProxy || value instanceof PersistentCollection) {
                if (!Hibernate.isInitialized(value)) {
                    // Skip this property entirely
                    return;
                }
            }
            super.serializeAsField(bean, gen, prov);
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
