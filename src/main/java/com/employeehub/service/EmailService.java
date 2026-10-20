package com.employeehub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.List;

// The one place in the app that actually talks to an SMTP server — every
// feature that needs to email someone (new-employee alerts, document
// expiration reminders, and whatever comes after those) goes through this,
// not its own JavaMailSender. Keeping it this thin and generic (to/subject/
// body in, nothing domain-specific) is what makes it reusable instead of
// each feature growing its own mail-sending copy.
//
// Deliberately NOT exposed as an HTTP endpoint ("POST /send-email" taking an
// arbitrary recipient) — that would turn the app into an open mail relay
// for anyone who can reach the API. Only server-side code calls this.
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:}")
    private String fromAddress;

    public void send(List<String> to, String subject, String htmlBody) {
        if (to == null || to.isEmpty()) {
            logger.warn("Email '{}' skipped: no recipients", subject);
            return;
        }
        if (!enabled) {
            logger.info("Email notifications disabled (app.mail.enabled=false) — would have sent '{}' to {}",
                    subject, to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            logger.info("Email '{}' sent to {}", subject, to);
        } catch (MessagingException | RuntimeException e) {
            // A failed email should never break the feature that triggered
            // it (e.g. an SMTP hiccup must not stop an employee from being
            // saved) — log loudly and move on rather than propagate.
            logger.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage(), e);
        }
    }
}
