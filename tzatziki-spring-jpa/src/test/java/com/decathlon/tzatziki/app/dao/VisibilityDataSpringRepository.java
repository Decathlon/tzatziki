package com.decathlon.tzatziki.app.dao;

import com.decathlon.tzatziki.app.model.Visibility;
import org.springframework.data.repository.CrudRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface VisibilityDataSpringRepository extends CrudRepository<Visibility, Integer> {

    AtomicInteger SAVE_ALL_CALLS = new AtomicInteger();

    @Override
    default <S extends Visibility> Iterable<S> saveAll(Iterable<S> entities) {
        SAVE_ALL_CALLS.incrementAndGet();
        List<S> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }
}
