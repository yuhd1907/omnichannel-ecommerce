package ra.edu.identityservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ra.edu.identityservice.dto.request.LoginRequest;
import ra.edu.identityservice.dto.request.RegisterRequest;
import ra.edu.identityservice.dto.response.AddressResponse;
import ra.edu.identityservice.dto.response.AuthResponse;
import ra.edu.identityservice.repository.AddressRepository;
import ra.edu.identityservice.service.AuthService;
import ra.edu.identityservice.service.UserService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private AddressRepository addressRepository;

    private String testEmail;
    private String accessToken;
    private UUID userId;

    @BeforeEach
    void setUp() {
        testEmail = "user_" + UUID.randomUUID() + "@example.com";
        var userRes = authService.register(new RegisterRequest(testEmail, "Password123!", "Nguyen Van B", "0912345678"));
        userId = userRes.id();
        AuthResponse loginRes = authService.login(new LoginRequest(testEmail, "Password123!"), "Test-Agent", "Test-Device");
        accessToken = loginRes.accessToken();
    }

    @Test
    @DisplayName("GET /api/v1/users/me - Lay thong tin user tu JWT token, tra 200")
    void getCurrentUser_Authenticated_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value(testEmail))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van B"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me - Khong co token tra 401 UNAUTHORIZED")
    void getCurrentUser_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST & GET /api/v1/users/me/addresses - Tao dia chi va kiem tra partial unique index khi doi default")
    void addresses_Workflow_Success() throws Exception {
        // 1. Tao dia chi mac dinh thu nhat (isDefault = true)
        String addr1 = """
                {
                    "recipientName": "Nguyen Van B",
                    "phone": "0912345678",
                    "addressLine": "123 Le Loi",
                    "ward": "Ben Nghe",
                    "district": "Quan 1",
                    "city": "TP Ho Chi Minh",
                    "isDefault": true
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addr1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.addressLine").value("123 Le Loi"));

        // 2. Tao dia chi mac dinh thu hai (isDefault = true)
        // Partial unique index se bi vi pham neu service khong bo isDefault cua dia chi cu trong cung transaction!
        String addr2 = """
                {
                    "recipientName": "Nguyen Van B",
                    "phone": "0912345678",
                    "addressLine": "456 Nguyen Hue",
                    "ward": "Ben Nghe",
                    "district": "Quan 1",
                    "city": "TP Ho Chi Minh",
                    "isDefault": true
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addr2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.addressLine").value("456 Nguyen Hue"));

        // 3. Lay danh sach dia chi cua user: phai co 2 dia chi, chi dia chi 2 co isDefault = true
        mockMvc.perform(get("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(2));

        List<AddressResponse> addresses = userService.getUserAddresses(userId);
        assertThat(addresses).hasSize(2);
        long defaultCount = addresses.stream().filter(AddressResponse::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);

        AddressResponse currentDefault = addresses.stream().filter(AddressResponse::isDefault).findFirst().orElseThrow();
        assertThat(currentDefault.addressLine()).isEqualTo("456 Nguyen Hue");
    }
}
