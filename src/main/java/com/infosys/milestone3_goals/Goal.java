package com.infosys.milestone3_goals;

import jakarta.persistence.*;

@Entity
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private int target;
    private int progress;

    public Goal() {}

    public Goal(String title, int target) {
        this.title = title;
        this.target = target;
        this.progress = 0;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public int getTarget() { return target; }
    public int getProgress() { return progress; }

    public void setTitle(String title) { this.title = title; }
    public void setTarget(int target) { this.target = target; }
    public void setProgress(int progress) { this.progress = progress; }
}
