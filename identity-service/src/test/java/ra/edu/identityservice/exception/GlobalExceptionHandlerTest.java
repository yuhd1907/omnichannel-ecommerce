package ra.edu.identityservice.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ra.edu.common.response.ErrorCode;

import java.sql.SQLException;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    record SampleRequest(@NotBlank(message = "Field is required") String title) {}

    @RestController
    static class DummyController {

        @PostMapping("/test/validation")
        void testValidation(@Valid @RequestBody SampleRequest request) {}

        @GetMapping("/test/business-409")
        void testConflict() {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS, "Email đã tồn tại", HttpStatus.CONFLICT);
        }

        @GetMapping("/test/business-401")
        void testUnauthorized() {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sai thông tin đăng nhập", HttpStatus.UNAUTHORIZED);
        }

        @GetMapping("/test/sql-error")
        void testSqlError() throws Exception {
            throw new SQLException("SELECT * FROM users WHERE secret_column = 'leak'; syntax error");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Validation error: Tra 400 VALIDATION_FAILED kem danh sach field issues")
    void handleValidationException() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("Field is required"));
    }

    @Test
    @DisplayName("Business exception 409: Tra dung ma loi EMAIL_EXISTS va status 409")
    void handleBusinessConflict() throws Exception {
        mockMvc.perform(get("/test/business-409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_EXISTS))
                .andExpect(jsonPath("$.message").value("Email đã tồn tại"));
    }

    @Test
    @DisplayName("Business exception 401: Tra dung ma loi INVALID_CREDENTIALS va status 401")
    void handleBusinessUnauthorized() throws Exception {
        mockMvc.perform(get("/test/business-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS))
                .andExpect(jsonPath("$.message").value("Sai thông tin đăng nhập"));
    }

    @Test
    @DisplayName("Fallback 500: Bat SQLException nhung khong de lo SQL hay stack trace ra ngoai response")
    void handleSqlErrorFallback() throws Exception {
        mockMvc.perform(get("/test/sql-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR))
                .andExpect(jsonPath("$.message").value(not(containsString("SELECT"))))
                .andExpect(jsonPath("$.message").value(not(containsString("syntax error"))))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
