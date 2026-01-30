package com.decathlon.tzatziki.app.jdbc;

import com.decathlon.tzatziki.app.model.Book;
import org.springframework.data.repository.CrudRepository;

public interface BookDataSpringRepository extends CrudRepository<Book, Integer> {
}
