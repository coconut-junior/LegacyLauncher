package org.spoutcraft.launcher.gui;

import javax.swing.*;

public class JAccountButton extends JButton {
    private String accountEmail;

    public JAccountButton(String text) {
        super(text);
    }

    public void setAccountName(String accountEmail) {
        this.accountEmail = accountEmail;
    }

    public String getAccountEmail() {
        return this.accountEmail;
    }
}
