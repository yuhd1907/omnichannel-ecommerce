package ra.edu.identityservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ra.edu.identityservice.dto.request.AddressRequest;
import ra.edu.identityservice.dto.response.AddressResponse;
import ra.edu.identityservice.dto.response.UserResponse;
import ra.edu.identityservice.entity.Address;
import ra.edu.identityservice.entity.Role;
import ra.edu.identityservice.entity.User;
import ra.edu.identityservice.exception.ResourceNotFoundException;
import ra.edu.identityservice.repository.AddressRepository;
import ra.edu.identityservice.repository.UserRepository;
import ra.edu.identityservice.service.UserService;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(UUID userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(this::mapToAddressResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse createAddress(UUID userId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        boolean isDefault = Boolean.TRUE.equals(request.isDefault());

        if (isDefault) {
            // Unset previous default address within the same transaction
            // Flush immediately to avoid partial unique index violation in PostgreSQL
            addressRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(oldDefault -> {
                oldDefault.setIsDefault(false);
                addressRepository.saveAndFlush(oldDefault);
                log.info("Unmarked previous default address ID: {}", oldDefault.getId());
            });
        }

        Address address = Address.builder()
                .user(user)
                .recipientName(request.recipientName().trim())
                .phone(request.phone().trim())
                .addressLine(request.addressLine().trim())
                .ward(request.ward().trim())
                .district(request.district().trim())
                .city(request.city().trim())
                .isDefault(isDefault)
                .build();

        Address savedAddress = addressRepository.save(address);
        log.info("Address created with ID: {} for user ID: {}", savedAddress.getId(), userId);

        return mapToAddressResponse(savedAddress);
    }

    private UserResponse mapToUserResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                roleNames,
                user.getCreatedAt()
        );
    }

    private AddressResponse mapToAddressResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getAddressLine(),
                address.getWard(),
                address.getDistrict(),
                address.getCity(),
                address.getIsDefault()
        );
    }
}
