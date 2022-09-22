package com.decathlon.tzatziki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListWrapper<T> {
    private List<T> wrapper;

    public void add(int index, T element) {
        wrapper.add(index, element);
    }

    public T get(int index) {
        return wrapper.get(index);
    }

    public T getOrDefault(int index, T defaultValue){
        return wrapper.size() > index ? wrapper.get(index) : defaultValue;
    }

    public T getOrDefault(int index, int defaultIndex){
        return wrapper.size() > index ? wrapper.get(index) : wrapper.get(defaultIndex);
    }
}
