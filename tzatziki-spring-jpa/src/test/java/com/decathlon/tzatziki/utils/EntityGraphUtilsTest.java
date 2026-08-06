package com.decathlon.tzatziki.utils;

import org.hibernate.graph.Graph;
import org.hibernate.graph.SubGraph;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.junit.jupiter.api.Test;

import jakarta.persistence.metamodel.Attribute;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityGraphUtilsTest {

    @Test
    void convertsConfiguredNamesToJavaAttributeNamesAtEveryGraphLevel() {
        Graph<Object> rootGraph = mock(Graph.class);
        SubGraph<Object> childGraph = mock(SubGraph.class);
        ManagedDomainType<Object> rootType = mock(ManagedDomainType.class);
        ManagedDomainType<Object> childType = mock(ManagedDomainType.class);
        Attribute<Object, Object> shippingId = mock(Attribute.class);
        Attribute<Object, Object> updatedAt = mock(Attribute.class);
        Attribute<Object, Object> carrierCode = mock(Attribute.class);

        when(rootGraph.getGraphedType()).thenReturn(rootType);
        when(childGraph.getGraphedType()).thenReturn(childType);
        when(rootType.getAttributes()).thenReturn(Set.of(shippingId, updatedAt));
        when(childType.getAttributes()).thenReturn(Set.of(carrierCode));
        when(shippingId.getName()).thenReturn("shippingId");
        when(updatedAt.getName()).thenReturn("updatedAt");
        when(carrierCode.getName()).thenReturn("carrierCode");
        when(rootGraph.addSubGraph("shippingId")).thenReturn(childGraph);

        EntityGraphUtils.addAttributesToGraph(rootGraph, Map.of(
                "shipping_id", Map.of("carrier_code", "value"),
                "updatedAt", "value"
        ));

        verify(rootGraph).addSubGraph("shippingId");
        verify(rootGraph).addAttributeNode("updatedAt");
        verify(childGraph).addAttributeNode("carrierCode");
    }
}
