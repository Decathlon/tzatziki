package com.decathlon.tzatziki.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Jackson 3 ({@code tools.jackson}) equivalent of {@link PersistenceUtil}'s mapper module.
 * Only referenced/loaded when {@code Jackson3Mapper} is the active delegate.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Jackson3PersistenceUtil {

    public static void register() {
        Jackson3Mapper.with(objectMapper -> objectMapper.rebuild().addModule(getMapperModule()).build());
    }

    public static JacksonModule getMapperModule() {
        SimpleModule module = new SimpleModule();
        // Add a modifier to skip uninitialized lazy properties
        module.setSerializerModifier(new HibernateValueSerializerModifier());
        return module;
    }

    /**
     * Serializer modifier that skips uninitialized lazy properties and @Transient annotated properties
     */
    private static class HibernateValueSerializerModifier extends ValueSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier beanDescRef, List<BeanPropertyWriter> beanProperties) {
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
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext ctxt) throws Exception {
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
            super.serializeAsProperty(bean, gen, ctxt);
        }

        private boolean isTransient() {
            return getMember().hasAnnotation(jakarta.persistence.Transient.class) ||
                    getMember().hasAnnotation(org.springframework.data.annotation.Transient.class);
        }
    }
}
