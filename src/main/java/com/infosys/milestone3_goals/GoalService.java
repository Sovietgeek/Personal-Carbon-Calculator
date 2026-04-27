package com.infosys.milestone3_goals;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GoalService {

    private final GoalRepository repository;
    private final UserRepository userRepo;

    public GoalService(GoalRepository repository, UserRepository userRepo) {
        this.repository = repository;
        this.userRepo = userRepo;
    }

    public Goal createGoal(Goal goal) {
        goal.setProgress(0);
        return repository.save(goal);
    }

    public List<Goal> getAllGoals() {
        return repository.findAll();
    }

    // 🔥 FINAL METHOD
    public Goal updateProgress(Long id, int value, String username) {

        Goal goal = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        if (value < 0) {
            throw new RuntimeException("Progress cannot be negative");
        }

        int oldProgress = goal.getProgress();

        // 🔥 INCREMENTAL UPDATE (important)
        int newProgress = oldProgress + value;

        if (newProgress > goal.getTarget()) {
            newProgress = goal.getTarget();
        }

        goal.setProgress(newProgress);

        // 🧪 DEBUG
        System.out.println("USERNAME: " + username);
        System.out.println("OLD: " + oldProgress);
        System.out.println("NEW: " + newProgress);
        System.out.println("TARGET: " + goal.getTarget());

        // 🎯 POINTS LOGIC (only once)
        if (oldProgress < goal.getTarget() && newProgress >= goal.getTarget()) {

            System.out.println("GOAL COMPLETED 🔥");

            User user = userRepo.findByUsername(username);

            if (user != null) {
                System.out.println("USER FOUND: " + user.getUsername());

                user.setPoints(user.getPoints() + 50);
                userRepo.save(user);

            } else {
                System.out.println("USER NOT FOUND ❌");
            }
        }

        return repository.save(goal);
    }

    public void deleteGoal(Long id) {
        repository.deleteById(id);
    }
}