-- V4: luu noi dung + trang thai gui email cua tung notification (outbox cho email).
-- Truoc day loi SMTP chi ghi log -> thu mat han. Nay: PENDING -> SENT, loi thi gui lai theo backoff,
-- qua so lan toi da -> FAILED (doi soat bang tay). SKIPPED: khong co dia chi nhan hoac tat gui mail.
ALTER TABLE notifications
    ADD COLUMN email_to              VARCHAR(255),
    ADD COLUMN email_subject         VARCHAR(255),
    ADD COLUMN email_text            TEXT,
    ADD COLUMN email_html            TEXT,
    ADD COLUMN email_status          VARCHAR(20) NOT NULL DEFAULT 'SKIPPED',
    ADD COLUMN email_attempts        INT         NOT NULL DEFAULT 0,
    ADD COLUMN email_next_attempt_at TIMESTAMPTZ,
    ADD COLUMN email_last_error      TEXT,
    ADD COLUMN email_sent_at         TIMESTAMPTZ;

ALTER TABLE notifications ADD CONSTRAINT chk_notifications_email_status
    CHECK (email_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'));

-- Job gui lai chi quet thu dang cho -> partial index nho
CREATE INDEX idx_notifications_email_due ON notifications (email_next_attempt_at)
    WHERE email_status = 'PENDING';
