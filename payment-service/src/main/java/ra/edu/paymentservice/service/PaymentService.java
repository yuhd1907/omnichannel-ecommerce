package ra.edu.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.common.response.ErrorCode;
import ra.edu.paymentservice.dto.CheckoutRequest;
import ra.edu.paymentservice.dto.PaymentView;
import ra.edu.paymentservice.entity.Payment;
import ra.edu.paymentservice.exception.BusinessException;
import ra.edu.paymentservice.gateway.PaymentGatewayAdapter;
import ra.edu.paymentservice.repository.PaymentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final List<PaymentGatewayAdapter> adapters;

    /** Map tra cứu nhanh adapter theo method (MOCK, VNPAY, ...). */
    private Map<String, PaymentGatewayAdapter> adapterMap() {
        return adapters.stream()
                .collect(Collectors.toMap(a -> a.method().toUpperCase(), Function.identity()));
    }

    /**
     * POST /api/v1/orders/{orderId}/payments
     * Idempotent: nếu payment còn PENDING thì trả lại đúng payment đó kèm checkoutUrl.
     */
    @Transactional
    public PaymentView checkout(UUID orderId, UUID userId, CheckoutRequest request) {
        return checkout(orderId, userId, request, null);
    }

    /** @param clientIp IP người mua (VNPAY bắt buộc gửi vnp_IpAddr); null nếu không rõ. */
    @Transactional
    public PaymentView checkout(UUID orderId, UUID userId, CheckoutRequest request, String clientIp) {
        // 1. Tìm payment theo orderId — nếu chưa có thì order.created event chưa relay tới
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_READY,
                        "Payment record not found. Order may still be processing, please retry shortly.",
                        HttpStatus.CONFLICT));

        // 2. Chủ đơn hoặc admin — admin không bị từ chối ở đây (security layer lo)
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ACCESS_DENIED,
                    "You are not the owner of this order.",
                    HttpStatus.FORBIDDEN);
        }

        // 3. Trạng thái SUCCESS → không cho thanh toán lại
        if ("SUCCESS".equals(payment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ALREADY_COMPLETED,
                    "This order has already been paid.",
                    HttpStatus.CONFLICT);
        }

        // 4. Trạng thái FAILED → đơn đã hủy hoặc hết hạn
        if ("FAILED".equals(payment.getStatus())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CLOSED,
                    "Order is cancelled or expired. Payment is no longer accepted.",
                    HttpStatus.CONFLICT);
        }

        // 5. Lấy adapter cho method — không có → 400
        String methodKey = request.method().toUpperCase();
        PaymentGatewayAdapter adapter = adapterMap().get(methodKey);
        if (adapter == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Payment method '" + request.method() + "' is not supported.",
                    HttpStatus.BAD_REQUEST);
        }

        // 6. Lưu method (có thể đổi method khi còn PENDING), tạo checkoutUrl
        payment.setMethod(methodKey);
        paymentRepository.save(payment);

        String checkoutUrl = adapter.buildCheckoutUrl(payment, clientIp);
        log.info("Checkout initiated: orderId={}, userId={}, method={}", orderId, userId, methodKey);

        return toView(payment, checkoutUrl);
    }

    /**
     * GET /api/v1/orders/{orderId}/payments
     * Chủ đơn hoặc ADMIN.
     */
    @Transactional(readOnly = true)
    public PaymentView getPayment(UUID orderId, UUID userId, boolean isAdmin) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found for this order.",
                        HttpStatus.NOT_FOUND));

        if (!isAdmin && !payment.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ACCESS_DENIED,
                    "You are not the owner of this order.",
                    HttpStatus.FORBIDDEN);
        }

        // checkoutUrl chỉ hiển thị khi còn PENDING và đã chọn method
        String checkoutUrl = null;
        if ("PENDING".equals(payment.getStatus()) && payment.getMethod() != null) {
            PaymentGatewayAdapter adapter = adapterMap().get(payment.getMethod().toUpperCase());
            if (adapter != null) {
                checkoutUrl = adapter.buildCheckoutUrl(payment);
            }
        }

        return toView(payment, checkoutUrl);
    }

    private PaymentView toView(Payment payment, String checkoutUrl) {
        return new PaymentView(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                checkoutUrl);
    }
}
