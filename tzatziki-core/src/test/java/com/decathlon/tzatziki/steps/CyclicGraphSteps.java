package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.cyclicgraph.Order;
import io.cucumber.java.en.And;

public class CyclicGraphSteps {
    private final ObjectSteps objects;

    public CyclicGraphSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    @And("orderLines references order")
    public void orderlinesReferenceOrder() {
        Order order = objects.get("order");
        order.getOrderLines().forEach( orderLine -> orderLine.setOrder(order));
    }
}
