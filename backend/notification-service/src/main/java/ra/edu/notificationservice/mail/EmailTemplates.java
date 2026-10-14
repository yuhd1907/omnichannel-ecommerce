package ra.edu.notificationservice.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ra.edu.notificationservice.entity.NotificationType;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Nội dung email theo loại event: tiêu đề + bản văn bản thuần + bản HTML (ứng dụng mail không hiện
 * HTML thì dùng bản thuần). Link "Xem đơn hàng" trỏ về web (app.public-url).
 */
@Component
public class EmailTemplates {

    public record Email(String subject, String text, String html) {}

    private final String publicUrl;

    public EmailTemplates(@Value("${app.public-url:http://localhost:3001}") String publicUrl) {
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    public Email build(NotificationType type, UUID orderId, String paymentMethod, BigDecimal totalAmount) {
        boolean cod = "COD".equalsIgnoreCase(paymentMethod);
        String code = orderId.toString().substring(0, 8).toUpperCase();
        String headline = switch (type) {
            case ORDER_CREATED   -> "Đã nhận đơn hàng #" + code;
            case ORDER_CANCELLED -> "Đơn hàng #" + code + " đã bị hủy";
            case ORDER_CONFIRMED -> cod ? "Đặt hàng thành công đơn #" + code + " (thanh toán khi nhận hàng)"
                                        : "Thanh toán thành công đơn #" + code;
            case ORDER_SHIPPED   -> "Đơn hàng #" + code + " đang được giao";
            case ORDER_COMPLETED -> "Đơn hàng #" + code + " đã giao thành công";
        };
        String detail = switch (type) {
            case ORDER_CREATED   -> "Chúng tôi đã nhận đơn hàng của bạn và đang giữ hàng trong 15 phút. Vui lòng hoàn tất thanh toán hoặc chọn thanh toán khi nhận hàng.";
            case ORDER_CANCELLED -> "Đơn hàng đã bị hủy (bạn hủy, thanh toán không thành công hoặc quá hạn thanh toán). Hàng đã được trả lại kho; nếu đã bị trừ tiền, chúng tôi sẽ hoàn lại.";
            case ORDER_CONFIRMED -> cod ? "Đơn hàng đã được xác nhận. Vui lòng chuẩn bị tiền mặt để thanh toán cho nhân viên giao hàng."
                                        : "Chúng tôi đã nhận được thanh toán. Đơn hàng sẽ sớm được giao cho đơn vị vận chuyển.";
            case ORDER_SHIPPED   -> "Đơn hàng đã được giao cho đơn vị vận chuyển và đang trên đường tới bạn.";
            case ORDER_COMPLETED -> cod ? "Đơn hàng đã được giao thành công và đã thu tiền. Cảm ơn bạn đã mua sắm!"
                                        : "Đơn hàng đã được giao thành công. Cảm ơn bạn đã mua sắm!";
        };
        String orderUrl = publicUrl + "/orders/" + orderId;
        String total = totalAmount == null ? null : formatVnd(totalAmount);
        String method = paymentMethod == null ? null : (cod ? "Thanh toán khi nhận hàng (COD)" : paymentMethod.toUpperCase());

        String subject = "[gom.] " + headline;

        StringBuilder text = new StringBuilder("Xin chào,\n\n").append(detail).append("\n\n")
                .append("Mã đơn hàng: ").append(orderId).append('\n');
        if (total != null) text.append("Tổng tiền: ").append(total).append('\n');
        if (method != null) text.append("Thanh toán: ").append(method).append('\n');
        text.append("\nXem đơn hàng: ").append(orderUrl).append("\n\ngom. — Gom mọi kênh, một giỏ hàng");

        StringBuilder rows = new StringBuilder(row("Mã đơn hàng", "#" + code));
        if (total != null) rows.append(row("Tổng tiền", total));
        if (method != null) rows.append(row("Thanh toán", method));

        String html = """
                <!doctype html><html lang="vi"><body style="margin:0;background:#f5f5f5;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#171717">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:24px 12px"><tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border:1px solid #e5e5e5;border-radius:16px">
                <tr><td style="padding:24px 28px;border-bottom:1px solid #e5e5e5">
                  <span style="display:inline-block;width:28px;height:28px;border-radius:8px;background:#0a0a0a;color:#fff;text-align:center;line-height:28px;font-weight:800">g</span>
                  <span style="font-size:20px;font-weight:800;letter-spacing:-0.5px;vertical-align:middle;margin-left:6px">gom<span style="color:#a3a3a3">.</span></span>
                </td></tr>
                <tr><td style="padding:28px">
                  <h1 style="margin:0 0 12px;font-size:20px;line-height:1.35">%s</h1>
                  <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:#404040">%s</p>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e5e5;border-radius:12px;font-size:14px">%s</table>
                  <p style="margin:24px 0 0"><a href="%s" style="display:inline-block;background:#171717;color:#ffffff;text-decoration:none;font-weight:600;padding:12px 22px;border-radius:12px">Xem đơn hàng</a></p>
                </td></tr>
                <tr><td style="padding:16px 28px;border-top:1px solid #e5e5e5;font-size:12px;color:#737373">gom. — Gom mọi kênh, một giỏ hàng. Email tự động, vui lòng không trả lời.</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(escape(headline), escape(detail), rows, escape(orderUrl));

        return new Email(subject, text.toString(), html);
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:10px 14px;color:#737373\">" + escape(label)
                + "</td><td style=\"padding:10px 14px;text-align:right;font-weight:600\">" + escape(value) + "</td></tr>";
    }

    private static String formatVnd(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return nf.format(amount) + " ₫";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
