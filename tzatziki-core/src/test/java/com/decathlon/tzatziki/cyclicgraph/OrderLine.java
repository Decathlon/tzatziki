package com.decathlon.tzatziki.cyclicgraph;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderLine {
    Integer id;
    String sku;
    Integer quantity;
    
    @JsonBackReference // Prevents infinite recursion when handling bidirectional relationship between Order and OrderLine
    Order order;
}
