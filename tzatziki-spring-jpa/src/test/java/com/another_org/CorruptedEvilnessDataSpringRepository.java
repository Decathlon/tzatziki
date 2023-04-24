package com.another_org;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CorruptedEvilnessDataSpringRepository extends CrudRepository<CorruptedEvilness, Integer> {}
