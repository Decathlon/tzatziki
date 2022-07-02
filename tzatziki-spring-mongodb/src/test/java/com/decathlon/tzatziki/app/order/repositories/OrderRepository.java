package com.decathlon.tzatziki.app.order.repositories;

import com.decathlon.tzatziki.app.order.documents.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {

}
