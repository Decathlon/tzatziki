package com.decathlon.tzatziki.app.entity_listeners;

import com.decathlon.tzatziki.app.model.SuperUser;

import javax.persistence.PrePersist;

public class SuperUserEntityListener {
    @PrePersist
    public void addPrefixToRoles(SuperUser superUser) {
        superUser.setRole("superUser_" + superUser.getRole());
    }
}
