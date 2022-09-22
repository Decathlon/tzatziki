package com.decathlon.tzatziki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    Integer id;

    String name;

    Integer score;

    List<Integer> friendsId;

    boolean friendly;
}
