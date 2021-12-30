package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.User;
import org.springframework.data.repository.CrudRepository;

public interface UserDataSpringRepository extends CrudRepository<User, Integer> {}
