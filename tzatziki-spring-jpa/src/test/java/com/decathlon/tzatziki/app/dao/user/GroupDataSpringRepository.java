package com.decathlon.tzatziki.app.dao.user;

import com.decathlon.tzatziki.app.model.user.Group;
import org.springframework.data.repository.CrudRepository;

public interface GroupDataSpringRepository extends CrudRepository<Group, Integer> {}
