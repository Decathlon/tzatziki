package com.decathlon.tzatziki.app.order.documents;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(value = "orders")
@Getter
@Setter
@ToString
public class Order {

    @Id
    public String id;
    private Customer customer;
    private List<Item> items;
    private Double price;
}
