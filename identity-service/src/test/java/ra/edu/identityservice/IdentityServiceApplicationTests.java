package ra.edu.identityservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ra.edu.identityservice.entity.Role;
import ra.edu.identityservice.repository.AddressRepository;
import ra.edu.identityservice.repository.RefreshTokenRepository;
import ra.edu.identityservice.repository.RoleRepository;
import ra.edu.identityservice.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IdentityServiceApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void contextLoads() {
        assertThat(userRepository).isNotNull();
        assertThat(roleRepository).isNotNull();
        assertThat(addressRepository).isNotNull();
        assertThat(refreshTokenRepository).isNotNull();

        Optional<Role> userRole = roleRepository.findByName("USER");
        assertThat(userRole).isPresent();

        Optional<Role> adminRole = roleRepository.findByName("ADMIN");
        assertThat(adminRole).isPresent();
    }

}
