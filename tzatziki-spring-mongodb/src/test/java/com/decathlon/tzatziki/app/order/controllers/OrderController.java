package com.decathlon.tzatziki.app.order.controllers;

import com.decathlon.tzatziki.app.order.documents.Order;
import com.decathlon.tzatziki.app.order.repositories.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class OrderController {

    private final OrderRepository repository;

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return repository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
