package com.snuc.cicd;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Integration-style tests: start the Spring context and hit real HTTP endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void infoEndpointReturnsMessage() throws Exception {
        mvc.perform(get("/api/info"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value(HomeController.MESSAGE));
    }

    @Test
    void greetEndpointUsesName() throws Exception {
        mvc.perform(get("/api/greet").param("name", "SNUC"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.greeting").value("Hello, SNUC!"));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }
}
