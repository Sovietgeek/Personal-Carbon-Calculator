package com.infosys.milestone3_goals;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "Carbon Tracker Backend Running 🚀";
    }
}