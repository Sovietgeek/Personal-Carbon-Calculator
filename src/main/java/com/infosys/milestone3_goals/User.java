package com.infosys.milestone3_goals;

import jakarta.persistence.*;

@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;

    // ✅ ADD THIS
    private int points = 0;

    public User() {}

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // GETTERS
    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getPoints() {
        return points;
    }

    // SETTERS
    public void setPoints(int points) {
        this.points = points;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}