// Envelope chung của backend (common/response): ApiResponse khi thành công, ApiError khi lỗi.

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  traceId: string | null;
  timestamp: string;
}

export interface FieldIssue {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  data: unknown;
  errors: FieldIssue[] | null;
  traceId: string | null;
  timestamp: string;
}

export interface PageData<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
