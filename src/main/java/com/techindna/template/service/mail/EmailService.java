package com.techindna.template.service.mail;

import com.techindna.template.entity.email.EmailDetails;

public interface EmailService {

    void sendMail(EmailDetails details);
}