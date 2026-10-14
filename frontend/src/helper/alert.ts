import Swal from "sweetalert2";
import withReactContent from "sweetalert2-react-content";

const MySwal = withReactContent(Swal);

const theme = {
  background: "#ffffff",
  color: "#171717",
  confirmButtonColor: "#171717",
  cancelButtonColor: "#ffffff",
};

/** Hộp thoại xác nhận cho thao tác không hoàn tác được (hủy đơn, chuyển trạng thái, chỉnh kho). */
export async function confirmAction(title: string, text: string, confirmText = "Đồng ý", danger = false): Promise<boolean> {
  const result = await MySwal.fire({
    ...theme,
    title,
    text,
    icon: danger ? "warning" : "question",
    showCancelButton: true,
    confirmButtonText: confirmText,
    cancelButtonText: "Quay lại",
    confirmButtonColor: danger ? "#e11d48" : theme.confirmButtonColor,
    iconColor: danger ? "#e11d48" : "#171717",
    reverseButtons: true,
    focusCancel: danger,
  });
  return result.isConfirmed;
}

export function showResult(title: string, text: string, icon: "success" | "error" | "info") {
  return MySwal.fire({ ...theme, title, text, icon, confirmButtonText: "Đóng" });
}
