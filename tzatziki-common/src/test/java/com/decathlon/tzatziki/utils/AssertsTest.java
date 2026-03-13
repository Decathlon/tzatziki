package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import com.google.common.base.Splitter;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class AssertsTest {
    @Test
    public void nonDefaultContains() {
        User actualUser = User.builder()
                .id(1)
                .name("toto")
                .friendly(true)
                .friendsId(List.of(1))
                .build();
        User expectedUser = User.builder()
                .id(1)
                .name(null)
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        Assertions.assertThrows(AssertionError.class, () -> Asserts.contains(actualUser, expectedUser));
    }

    @Test
    public void containsInAnyOrderFailError() {
        User actualUser1 = User.builder()
                .id(1)
                .name("toto1")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        User actualUser2 = User.builder()
                .id(2)
                .name("toto2")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        List<User> actualUsers = List.of(actualUser1, actualUser2);

        User expectedUser1 = User.builder()
                .id(1)
                .name("toto1")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();
        User expectedUser2 = User.builder()
                .id(2)
                .name("toto3")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        List<User> expectedUsers = List.of(expectedUser2, expectedUser1);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> Asserts.contains(actualUsers, expectedUsers))
                .withMessageContaining("[0]!=[1].name' -> expected:<toto[3]> but was:<toto[2]>")
                //We want to make sure that the contains return the most relevant error comparison
                .withMessageNotContaining("[1]!=[0]");

    }

    @Test
    public void containsInOrderFailError() {
        User actualUser1 = User.builder()
                .id(1)
                .name("toto1")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        User actualUser2 = User.builder()
                .id(2)
                .name("toto2")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        List<User> actualUsers = List.of(actualUser1, actualUser2);

        User expectedUser1 = User.builder()
                .id(1)
                .name("toto1")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();
        User expectedUser2 = User.builder()
                .id(2)
                .name("toto2")
                .friendly(false)
                .friendsId(Collections.emptyList())
                .build();

        List<User> expectedUsers = List.of(expectedUser2, expectedUser1);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> Asserts.containsInOrder(actualUsers, expectedUsers))
                .withMessageContaining("The actual list is not in the order expected");

    }

    @Test
    public void containsSkipsScalarsWhenExpectedElementIsStructured() {
        User user1 = User.builder()
                .id(1)
                .name("toto")
                .build();
        User user2 = User.builder()
                .id(2)
                .name("titi")
                .build();

        Asserts.contains(
                List.of(user1.getName(), user2),
                List.of(Map.of("id", 2, "name", "titi"))
        );
    }

    @Test
    public void equalsFailsWithClearErrorWhenActualIsScalarButExpectedIsMap() {
        User actualUser = User.builder()
                .id(1)
                .name("toto1")
                .build();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> Asserts.equals(actualUser.getName(), Map.of("id", 1, "name", "toto1")))
                .withMessageContaining("expected an object but was: toto1");
    }

    @Test
    public void containsFailsWithClearErrorWhenActualIsScalarButExpectedIsMap() {
        User actualUser = User.builder()
                .id(1)
                .name("toto1")
                .build();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> Asserts.contains(actualUser.getName(), Map.of("id", 1, "name", "toto1")))
                .withMessageContaining("expected an object but was: toto1");
    }

    @Test
    public void equalsEmptyStringActsAsEmptyMap() {
        Asserts.equals("", Collections.emptyMap());
    }

    @Test
    public void containsEmptyStringActsAsEmptyMap() {
        Asserts.contains("", Collections.emptyMap());
    }

    @Test
    public void equalsActualObjectWithExpectedMap() {
        User actualUser = User.builder()
                .id(1)
                .name("toto1")
                .build();
        Map<String, Object> expectedMap = Mapper.read(Mapper.toJson(actualUser), Map.class);

        Asserts.equals(actualUser, expectedMap);
    }

    @Test
    public void equalsActualJsonStringWithExpectedMap() {
        User user = User.builder()
                .id(1)
                .name("toto1")
                .build();
        String actualJson = Mapper.toJson(user);
        Map<String, Object> expectedMap = Mapper.read(actualJson, Map.class);

        Asserts.equals(actualJson, expectedMap);
    }

    @Test
    public void specialFieldTypeComparison(){
        User actualUser = User.builder()
                .id(1)
                .creationDate(Instant.parse("2022-08-12T10:00:00Z"))
                .build();

        Asserts.contains(actualUser, Map.of("id", 1, "creationDate", "2022-08-12T10:00:00.000Z"));
    }

    @Test
    public void customFlagsCanBeAdded(){
        Asserts.addFlag("isEvenAndInBounds", (input, expected) -> {
            String[] bounds = Splitter.on('|').trimResults().omitEmptyStrings().splitToList(expected).toArray(String[]::new);
            int inputInt = Integer.parseInt(input);
            int min = Integer.parseInt(bounds[0]);
            int max = Integer.parseInt(bounds[1]);
            org.junit.jupiter.api.Assertions.assertTrue(() -> inputInt >= min && inputInt <= max && inputInt % 2 == 0);
        });

        User actualUser = User.builder()
                .id(2)
                .creationDate(Instant.parse("2022-08-12T10:00:00Z"))
                .build();

        Asserts.contains(actualUser, Map.of("id", "?isEvenAndInBounds 2 || 4", "creationDate", "2022-08-12T10:00:00.000Z"));
        Asserts.equals("2", "?isEvenAndInBounds 2 || 4");

        Asserts.threwException(() -> Asserts.contains(actualUser, Map.of("id", "?isEvenAndInBounds 3 || 9999", "creationDate", "2022-08-12T10:00:00.000Z")), AssertionError.class);
        actualUser.setId(1);
        Asserts.threwException(() -> Asserts.contains(actualUser, Map.of("id", "?isEvenAndInBounds 1 || 9999", "creationDate", "2022-08-12T10:00:00.000Z")), AssertionError.class);

    }

}
