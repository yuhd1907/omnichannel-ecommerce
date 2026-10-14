import { z } from "zod";

// AdjustInventoryRequest: delta (Long, âm = xuất/giảm), reason bắt buộc tối đa 500 ký tự.
export const adjustInventorySchema = z.object({
  delta: z
    .number({ error: "Nhập số lượng" })
    .int("Phải là số nguyên")
    .refine((v) => v !== 0, "Số lượng điều chỉnh phải khác 0"),
  reason: z.string().trim().min(1, "Vui lòng nhập lý do").max(500, "Tối đa 500 ký tự"),
});

export type AdjustInventoryInput = z.infer<typeof adjustInventorySchema>;
