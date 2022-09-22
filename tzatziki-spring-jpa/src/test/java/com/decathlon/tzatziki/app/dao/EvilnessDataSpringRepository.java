package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.Evilness;
import org.springframework.data.repository.CrudRepository;

public interface EvilnessDataSpringRepository extends CrudRepository<Evilness, Integer> {}
