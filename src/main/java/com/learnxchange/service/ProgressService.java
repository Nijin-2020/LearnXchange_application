package com.learnxchange.service;

import com.learnxchange.dao.SessionDAO;
import com.learnxchange.dao.UserDAO;
import com.learnxchange.dao.UserSkillDAO;
import com.learnxchange.model.LearningSession;
import com.learnxchange.model.User;
import com.learnxchange.model.UserSkill;

import java.util.ArrayList;
import java.util.List;

/**
 * "Evolving" profile: XP, level, badges and profile completeness, all derived from existing data
 * (completed sessions, reviews received, skills listed, profile fields). Nothing extra is stored.
 */
public class ProgressService {
    private static final int[] THRESHOLDS = {0, 100, 250, 500, 900, 1500};
    private static final String[] NAMES = {"Newcomer", "Explorer", "Swapper", "Mentor", "Guide", "Master"};

    public record Badge(String id, String name, String description, boolean earned) { }

    public record Progress(int xp, int level, String levelName, int levelStartXp, int nextLevelXp,
                           String nextLevelName, int completeness, int completedSessions,
                           List<Badge> badges, List<String> nextSteps) { }

    private final UserDAO userDAO = new UserDAO();
    private final UserSkillDAO userSkillDAO = new UserSkillDAO();
    private final SessionDAO sessionDAO = new SessionDAO();

    public Progress forUser(User me) {
        User u = userDAO.findById(me.getId()).orElse(me);
        List<UserSkill> skills = userSkillDAO.findByUser(u.getId());
        long teach = skills.stream().filter(s -> s.type() == UserSkill.Type.TEACH).count();
        long learn = skills.stream().filter(s -> s.type() == UserSkill.Type.LEARN).count();
        int completed = (int) sessionDAO.findByUser(u.getId()).stream()
                .filter(s -> s.status() == LearningSession.Status.COMPLETED).count();

        boolean hasDept = !u.getDepartment().isBlank();
        boolean hasBio = !u.getBio().isBlank();
        boolean hasAvail = !u.getAvailability().isEmpty();
        int done = 1 + (hasDept ? 1 : 0) + (hasBio ? 1 : 0) + (hasAvail ? 1 : 0) + (teach > 0 ? 1 : 0) + (learn > 0 ? 1 : 0);
        int completeness = (int) Math.round(done * 100.0 / 6);

        int xp = completed * 50 + u.getRatingCount() * 10
                + (int) Math.min(teach, 8) * 5 + (int) Math.min(learn, 8) * 5 + completeness;

        int idx = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) if (xp >= THRESHOLDS[i]) idx = i;
        boolean max = idx == THRESHOLDS.length - 1;
        int next = max ? THRESHOLDS[idx] : THRESHOLDS[idx + 1];
        String nextName = max ? NAMES[idx] : NAMES[idx + 1];

        List<Badge> badges = List.of(
                new Badge("first-swap", "First swap", "Complete your first session", completed >= 1),
                new Badge("regular", "Regular", "Complete 5 sessions", completed >= 5),
                new Badge("well-rounded", "Well rounded", "List 3 skills to teach and 3 to learn", teach >= 3 && learn >= 3),
                new Badge("favorite", "Crowd favorite", "Average 4.5+ from 3 or more reviews",
                        u.getRatingCount() >= 3 && u.getAvgRating() >= 4.5),
                new Badge("open-book", "Open book", "Reach 100% profile completeness", completeness == 100),
                new Badge("trusted", "Trusted", "Receive 10 reviews", u.getRatingCount() >= 10));

        List<String> steps = new ArrayList<>();
        if (teach == 0) steps.add("Add a skill you can teach");
        if (learn == 0) steps.add("Add a skill you want to learn");
        if (!hasAvail) steps.add("Pick when you're free");
        if (!hasBio) steps.add("Write a short bio");
        if (!hasDept) steps.add("Add your department");
        if (completed == 0 && steps.size() < 3) steps.add("Complete your first swap session");
        if (steps.size() > 3) steps = new ArrayList<>(steps.subList(0, 3));

        return new Progress(xp, idx + 1, NAMES[idx], THRESHOLDS[idx], next, nextName,
                completeness, completed, badges, steps);
    }
}
