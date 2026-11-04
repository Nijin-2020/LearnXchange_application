package com.learnxchange.controller;

import javafx.scene.Parent;

/** A screen that can be embedded in a tab and reloads its data when shown. */
public interface Refreshable {
    Parent getView();
    void refresh();
}
