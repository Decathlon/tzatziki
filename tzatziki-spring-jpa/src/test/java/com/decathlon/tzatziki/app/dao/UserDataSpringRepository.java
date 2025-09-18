package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDataSpringRepository<T extends User> extends JpaRepository<T, Integer> {
}
