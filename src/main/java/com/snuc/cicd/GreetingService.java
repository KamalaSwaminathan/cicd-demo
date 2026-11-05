package com.snuc.cicd;

import org.springframework.stereotype.Service;

/** Business logic kept separate from the controller so it can be unit-tested on its own. */
@Service
public class GreetingService {

    public String greet(String name) {
        if (name == null || name.isBlank()) {
            return "Hello, World!";
        }
        return "Hello, " + name.trim() + "!";
    }

    public int add(int a, int b) {
        return a + b;
    }
}
