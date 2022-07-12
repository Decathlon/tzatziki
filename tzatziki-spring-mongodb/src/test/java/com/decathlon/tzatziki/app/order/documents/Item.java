package com.decathlon.tzatziki.app.order.documents;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Item {

    private String name;
    private String type;
    private Integer quantity;
    private Double price;
}
