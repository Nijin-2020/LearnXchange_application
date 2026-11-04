package com.learnxchange.util;

import com.learnxchange.model.User;

/** Holds the currently logged-in user for the desktop session. */
public final class AppContext {
    private static User currentUser;

    private AppContext() { }

    public static User getCurrentUser() { return currentUser; }
    public static void setCurrentUser(User user) { currentUser = user; }
    public static void clear() { currentUser = null; }
}
