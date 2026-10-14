import { z } from "zod";

// Khớp @Valid của product-service (CreateProductRequest, UpdateProductRequest, CreateSkuRequest).

export const productSchema = z.object({
  name: z.string().trim().min(1, "Vui lòng nhập tên sản phẩm").max(255, "Tối đa 255 ký tự"),
  slug: z
    .string()
    .trim()
    .max(255)
    .refine((v) => v === "" || /^[a-z0-9]+(-[a-z0-9]+)*$/.test(v), "Chỉ chữ thường, số và dấu gạch nối (vd iphone-16-pro)"),
  description: z.string().trim().max(5000, "Tối đa 5000 ký tự"),
  categoryId: z.string().min(1, "Chọn danh mục"),
  brandId: z.string().min(1, "Chọn thương hiệu"),
  basePrice: z.number({ error: "Nhập giá" }).positive("Giá phải lớn hơn 0"),
  status: z.enum(["ACTIVE", "INACTIVE"]),
});

export const skuSchema = z.object({
  skuCode: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9][A-Za-z0-9._-]{1,99}$/, "2-100 ký tự: chữ, số, dấu chấm, gạch dưới, gạch nối"),
  price: z.number({ error: "Nhập giá" }).positive("Giá phải lớn hơn 0"),
  attributes: z.string().trim().max(500, "Tối đa 500 ký tự"),
});

export type ProductFormInput = z.infer<typeof productSchema>;
export type SkuFormInput = z.infer<typeof skuSchema>;
