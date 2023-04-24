package com.decathlon.tzatziki.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import com.decathlon.tzatziki.app.entity_listeners.SuperUserEntityListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
