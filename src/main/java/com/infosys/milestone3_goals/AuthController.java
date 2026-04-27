package com.infosys.milestone3_goals;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository repo;

    public AuthController(UserRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/register")
    public String register(@RequestBody User user) {

        // check if user already exists
        User existing = repo.findByUsername(user.getUsername());

        if (existing != null) {
            return "USER_ALREADY_EXISTS";
        }

        repo.save(user);
        return "REGISTERED";
    }

    @PostMapping("/login")
    public String login(@RequestBody User user) {

        User existing = repo.findByUsername(user.getUsername());

        if (existing != null && existing.getPassword().equals(user.getPassword())) {
            return JwtUtil.generateToken(user.getUsername()); // 🔥 TOKEN RETURN
        } else {
            return "FAIL";
        }
    }
}