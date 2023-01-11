package com.decathlon.tzatziki.app.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "super_users")
public class SuperUser extends User {
    @Setter
    @Column(name = "role")
    String role;
}
