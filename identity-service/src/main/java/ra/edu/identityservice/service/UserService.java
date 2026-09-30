package ra.edu.identityservice.service;

import ra.edu.identityservice.dto.request.AddressRequest;
import ra.edu.identityservice.dto.response.AddressResponse;
import ra.edu.identityservice.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserResponse getCurrentUser(UUID userId);

    List<AddressResponse> getUserAddresses(UUID userId);

    AddressResponse createAddress(UUID userId, AddressRequest request);
}
