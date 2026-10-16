package com.decathlon.tzatziki.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entity without @Table: its table name (jedi_master) comes from Spring Boot's physical naming strategy.
 */
@NoArgsConstructor
@Getter
@Entity
public class JediMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String fullName;
}
