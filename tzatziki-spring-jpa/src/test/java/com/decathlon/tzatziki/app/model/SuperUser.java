package com.decathlon.tzatziki.app.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "super_users")
public class SuperUser extends User {
    @Setter
    @Column(name = "role")
    String role;
}
