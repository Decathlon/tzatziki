package com.decathlon.tzatziki.app.dao.user;

import com.decathlon.tzatziki.app.model.user.User;
import org.springframework.data.repository.CrudRepository;

public interface UserDataSpringRepository extends CrudRepository<User, Integer> {}
