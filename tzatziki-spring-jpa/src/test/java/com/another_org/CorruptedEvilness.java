package com.another_org;

import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "evilness")
public class CorruptedEvilness {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;
    @Column(
            name = "bad_attribute"
    )
    boolean badAttribute;
}
