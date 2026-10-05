package com.techindna.template.entity.email;

import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmailDetails {

    private String recipient;
    private String subject;
    private String body;
    private Map<String, Object> variables;

    public EmailDetails(String recipient, String subject, String body,
                        Map<String, Object> variables) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.variables = variables;
    }
}