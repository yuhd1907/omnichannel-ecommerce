package ra.edu.notificationservice.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Gửi email qua SMTP: Mailpit khi dev/demo (xem thư ở cổng 8025), SMTP thật (Gmail, Brevo, SES...)
 * khi cấu hình SMTP_* trong .env. Ném lỗi khi gửi thất bại để EmailDispatcher hẹn gửi lại.
 * Tắt hẳn bằng MAIL_ENABLED=false (vd khi load test): thư được đánh dấu SKIPPED.
 */
@Slf4j
@Component
public class EmailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;
    private final String fromName;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${app.mail.enabled:false}") boolean enabled,
                       @Value("${app.mail.from:no-reply@omnichannel.local}") String from,
                       @Value("${app.mail.from-name:gom.}") String fromName) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.fromName = fromName;
    }

    public boolean isEnabled() {
        return enabled && mailSender.getIfAvailable() != null;
    }

    /** Gửi thư 2 phần (text + HTML). @throws MailDeliveryException khi SMTP lỗi hoặc thư không dựng được. */
    public void send(String to, String subject, String text, String html) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!enabled || sender == null) {
            throw new MailDeliveryException("Mail sending is disabled");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html == null ? text : html);
            sender.send(message);
            log.info("[EMAIL ĐÃ GỬI] to={} subject={}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException | org.springframework.mail.MailException e) {
            throw new MailDeliveryException(e.getMessage(), e);
        }
    }

    public static class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message) {
            super(message);
        }

        public MailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
