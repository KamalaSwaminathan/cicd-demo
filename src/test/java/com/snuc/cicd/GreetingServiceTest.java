package com.snuc.cicd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring context, run in milliseconds. */
class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    @Test
    void greetsByName() {
        assertEquals("Hello, Kamala!", service.greet("Kamala"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals("Hello, DevOps!", service.greet("  DevOps  "));
    }

    @Test
    void defaultsWhenNameMissing() {
        assertEquals("Hello, World!", service.greet(null));
        assertEquals("Hello, World!", service.greet("   "));
    }

    @Test
    void addsNumbers() {
        assertEquals(5, service.add(2, 3));
        assertEquals(-1, service.add(2, -3));
    }
}
