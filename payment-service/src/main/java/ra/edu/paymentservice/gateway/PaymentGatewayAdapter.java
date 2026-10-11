package ra.edu.paymentservice.gateway;

import ra.edu.paymentservice.entity.Payment;

public interface PaymentGatewayAdapter {

    /**
     * Tên phương thức thanh toán mà adapter này hỗ trợ (ví dụ: "MOCK", "VNPAY").
     */
    String method();

    /**
     * Tạo URL redirect để người dùng thực hiện thanh toán.
     */
    String buildCheckoutUrl(Payment payment);

    /**
     * Xác thực chữ ký webhook từ cổng thanh toán.
     *
     * @param body      raw body dạng bytes
     * @param signature giá trị header chữ ký
     * @return true nếu hợp lệ
     */
    boolean verifySignature(byte[] body, String signature);
}
