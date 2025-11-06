package com.decathlon.tzatziki.app.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@NoArgsConstructor
@Table(name = "books", schema = "library")
public class Book {

    @Id
    Integer id;

    String title;
}
