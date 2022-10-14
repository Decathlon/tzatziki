package com.decathlon.tzatziki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Nest {
    Nest subNest;
    Bird bird;

    @Data
    public static class Bird {
        String name;
    }
}
