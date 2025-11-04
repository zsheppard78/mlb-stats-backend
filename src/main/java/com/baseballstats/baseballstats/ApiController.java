package com.baseballstats.baseballstats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ApiController {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/api/teams")
    public Object getTeams() {
        String url = "https://statsapi.mlb.com/api/v1/teams?sportId=1";
        return restTemplate.getForObject(url, Object.class);
    }
}
