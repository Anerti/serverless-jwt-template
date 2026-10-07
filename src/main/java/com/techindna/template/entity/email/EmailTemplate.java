package com.techindna.template.entity.email;

public enum EmailTemplate {
    REGISTRATION_VERIFICATION("mail/verification"),
    LOGIN_VERIFICATION("mail/login-verification");

    private final String viewName;

    EmailTemplate(String viewName) {
        this.viewName = viewName;
    }

    public String viewName() {
        return viewName;
    }
}
