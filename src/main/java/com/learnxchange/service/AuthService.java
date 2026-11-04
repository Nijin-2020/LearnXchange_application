package com.learnxchange.service;

import com.learnxchange.dao.UserDAO;
import com.learnxchange.model.User;
import com.learnxchange.util.AppContext;
import com.learnxchange.util.PasswordUtil;
import com.learnxchange.util.ServiceException;
import com.learnxchange.util.Validator;

public class AuthService {
    public static final String DEFAULT_ADMIN_EMAIL = "admin@learnxchange.com";
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";

    private final UserDAO userDAO = new UserDAO();

    public User register(String name, String email, String password, String department) {
        String cleanName = Validator.require(name, "Name", 100);
        String cleanEmail = Validator.email(email);
        Validator.password(password);
        String cleanDept = Validator.optional(department, "Department", 100);

        if (userDAO.findByEmail(cleanEmail).isPresent())
            throw new ServiceException("An account with this email already exists.");

        User u = new User();
        u.setName(cleanName);
        u.setEmail(cleanEmail);
        u.setDepartment(cleanDept);
        u.setPasswordHash(PasswordUtil.hash(password));
        u.setRole(User.Role.USER);
        u.setId(userDAO.insert(u));
        return u;
    }

    public User login(String email, String password) {
        String e = email == null ? "" : email.trim().toLowerCase();
        User u = userDAO.findByEmail(e).orElse(null);
        if (u == null || !PasswordUtil.verify(password == null ? "" : password, u.getPasswordHash()))
            throw new ServiceException("Invalid email or password.");
        if (u.isBlocked())
            throw new ServiceException("This account has been blocked. Please contact an administrator.");
        return u;
    }

    public void logout() {
        AppContext.clear();
    }

    /** Creates the first admin account if none exists, so the admin panel is reachable on a fresh database. */
    public void ensureDefaultAdmin() {
        if (userDAO.countByRole(User.Role.ADMIN) > 0) return;
        User admin = new User();
        admin.setName("Administrator");
        admin.setEmail(DEFAULT_ADMIN_EMAIL);
        admin.setPasswordHash(PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD));
        admin.setRole(User.Role.ADMIN);
        userDAO.insert(admin);
    }
}
