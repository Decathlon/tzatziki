package com.decathlon.tzatziki.app.model;

import com.decathlon.tzatziki.app.entity_listeners.SuperUserEntityListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Table;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "super_users")
@EntityListeners(SuperUserEntityListener.class)
public class SuperUser extends User {
    @Setter
    @Column(name = "role")
    String role;
}
