package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.Visibility;
import org.springframework.data.repository.CrudRepository;

public interface VisibilityDataSpringRepository extends CrudRepository<Visibility, Integer> {
}
