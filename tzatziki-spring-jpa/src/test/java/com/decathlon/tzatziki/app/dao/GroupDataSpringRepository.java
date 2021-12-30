package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.Group;
import org.springframework.data.repository.CrudRepository;

public interface GroupDataSpringRepository extends CrudRepository<Group, Integer> {}
