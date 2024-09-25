package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductDataSpringRepository<T extends Product> extends JpaRepository<T, Integer> {
}
