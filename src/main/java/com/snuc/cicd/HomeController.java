package com.snuc.cicd;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    // CHANGE THIS LINE for the redeployment demo (e.g. v1 -> v2), commit, push, watch it go live.
    static final String MESSAGE = "Welcome to the CI/CD Demo - v1";

    private final GreetingService greetingService;

    // Injected by Kubernetes from the deployment (commit SHA / pod name) so you can prove which build is live.
    @Value("${APP_VERSION:local}")
    private String appVersion;

    @Value("${HOSTNAME:localhost}")
    private String podName;

    public HomeController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", MESSAGE);
        body.put("version", appVersion);
        body.put("pod", podName);
        return body;
    }

    @GetMapping("/api/greet")
    public Map<String, String> greet(@RequestParam(required = false) String name) {
        return Map.of("greeting", greetingService.greet(name));
    }
}
