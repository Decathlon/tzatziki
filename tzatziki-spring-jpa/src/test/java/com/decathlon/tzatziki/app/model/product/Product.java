package com.decathlon.tzatziki.app.model.product;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "products")
public class Product {

    @Id
    private int id;

    private String name;

    private int price;
}
