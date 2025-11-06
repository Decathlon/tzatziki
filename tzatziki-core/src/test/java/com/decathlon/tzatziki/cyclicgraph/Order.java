package com.decathlon.tzatziki.cyclicgraph;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    Integer id;
    String name;
    List<OrderLine> orderLines;
}
