package com.decathlon.tzatziki.app.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "evilness")
public class Evilness {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;
    @Column(
            name = "evil"
    )
    boolean evil;
}
