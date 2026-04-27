package com.infosys.milestone3_goals;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/leaderboard")
@CrossOrigin("*")
public class LeaderboardController {

    private final UserRepository repo;

    public LeaderboardController(UserRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<User> getTopUsers() {
        return repo.findAll()
                .stream()
                .sorted((a, b) -> b.getPoints() - a.getPoints())
                .limit(5)
                .toList();
    }
}