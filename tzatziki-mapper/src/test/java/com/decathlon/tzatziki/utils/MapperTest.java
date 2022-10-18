package com.decathlon.tzatziki.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MapperTest {
    @Test
    void checkFirstNonWhitespaceCharacter() {
        Assertions.assertTrue(Mapper.firstNonWhitespaceCharacterIs("""
                                
                   - hello
                """, '-'));
    }

    @Test
    void yamlDotPropertyToObject(){
        Assertions.assertEquals(Mapper.toYaml("""
                user.name: bob
                """),
                """
                        user:
                          name: bob
                        """);

        Mapper.shouldConvertDotPropertiesToObject(false);
        Assertions.assertEquals(Mapper.toYaml("""
                user.name: bob
                """),
                """
                        user.name: bob
                        """);

        Mapper.shouldConvertDotPropertiesToObject(true);
    }
}
