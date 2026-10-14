// identity-service: AuthResponse, UserResponse, AddressResponse

export interface AuthTokens {
  accessToken: string;
  refreshToken: string | null;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  status: string;
  roles: string[];
  createdAt: string;
}

export interface Address {
  id: string;
  recipientName: string;
  phone: string;
  addressLine: string;
  ward: string;
  district: string;
  city: string;
  isDefault: boolean;
}
