package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookDataSpringRepository<T extends Book> extends JpaRepository<T, Integer> {
}
