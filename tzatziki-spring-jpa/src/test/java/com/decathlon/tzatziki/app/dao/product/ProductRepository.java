package com.decathlon.tzatziki.app.dao.product;

import com.decathlon.tzatziki.app.model.product.Product;
import org.springframework.data.repository.CrudRepository;

public interface ProductRepository extends CrudRepository<Product, Integer> {

}
