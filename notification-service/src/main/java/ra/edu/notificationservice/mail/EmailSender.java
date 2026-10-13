package ra.edu.notificationservice.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gửi email qua SMTP (Mailpit khi demo: UI xem thư ở cổng 8025).
 *
 * Best-effort: lỗi SMTP chỉ ghi log, KHÔNG ném ra ngoài. Notification đã được lưu DB và message
 * đã đánh dấu xử lý; ném lỗi ở đây sẽ làm RabbitMQ retry và tạo bản ghi trùng.
 * Tắt hẳn bằng MAIL_ENABLED=false (chỉ ghi log như trước).
 */
@Slf4j
@Component
public class EmailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${app.mail.enabled:false}") boolean enabled,
                       @Value("${app.mail.from:no-reply@omnichannel.local}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.info("[EMAIL BỎ QUA] không có địa chỉ nhận, subject={}", subject);
            return;
        }
        JavaMailSender sender = enabled ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.info("[EMAIL GIẢ LẬP] to={} subject={}", to, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("[EMAIL ĐÃ GỬI] to={} subject={}", to, subject);
        } catch (MailException e) {
            log.warn("[EMAIL LỖI] to={} subject={}: {}", to, subject, e.getMessage());
        }
    }
}
