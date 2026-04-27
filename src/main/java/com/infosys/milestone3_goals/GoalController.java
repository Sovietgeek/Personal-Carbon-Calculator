package com.infosys.milestone3_goals;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/goals")
public class GoalController {

    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @PostMapping
    public Goal create(@RequestBody Goal goal) {
        return service.createGoal(goal);
    }

    @GetMapping
    public List<Goal> list() {
        return service.getAllGoals();
    }

    @PutMapping("/{id}/progress/{value}")
    public Goal updateProgress(
            @PathVariable Long id,
            @PathVariable int value,
            @RequestHeader("Authorization") String header
    ) {

        String token = header.substring(7); // remove "Bearer "
        String username = JwtUtil.validateToken(token);

        return service.updateProgress(id, value, username);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteGoal(id);
    }

}