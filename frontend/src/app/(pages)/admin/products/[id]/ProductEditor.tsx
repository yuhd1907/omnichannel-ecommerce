"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { FiArrowLeft, FiLock, FiPlus, FiSave, FiTrash2, FiX } from "react-icons/fi";
import { toast } from "sonner";
import { z } from "zod";
import { SkuImageUploader } from "@/app/components/admin/SkuImageUploader";
import { Button } from "@/app/components/button/Button";
import { EmptyBlock, ErrorBlock, LoadingBlock, PageTitle } from "@/app/components/card/States";
import { confirmAction } from "@/helper/alert";
import { errorMessage } from "@/helper/api";
import { useAsync } from "@/hooks/useAsync";
import { useAuth } from "@/hooks/useAuth";
import type { BrandSummary, CategorySummary, ProductDetail, ProductSku } from "@/interface/product.interface";
import { productSchema, skuSchema, type ProductFormInput, type SkuFormInput } from "@/schemas/product.schema";
import { formatAttributes, parseAttributes, productAdminService } from "@/services/product-admin.service";

export function ProductEditor({ id }: { id: string }) {
  const { user, isAdmin, hydrated, openLogin } = useAuth();
  if (!hydrated) return <LoadingBlock />;
  if (!user || !isAdmin) {
    return (
      <EmptyBlock icon={<FiLock />} title="Chỉ dành cho tài khoản quản trị">
        {!user && (
          <Button className="mt-2" onClick={openLogin}>
            Đăng nhập
          </Button>
        )}
      </EmptyBlock>
    );
  }
  return id === "new" ? <CreateProduct /> : <EditProduct id={id} />;
}

function useOptions() {
  const categories = useAsync(() => productAdminService.categories(), []);
  const brands = useAsync(() => productAdminService.brands(), []);
  return { categories: categories.data, brands: brands.data, error: categories.error ?? brands.error };
}

function BackLink() {
  return (
    <Link href="/admin?tab=products" className="mb-6 inline-flex items-center gap-2 text-sm text-neutral-900 hover:text-neutral-500">
      <FiArrowLeft /> Danh sách sản phẩm
    </Link>
  );
}

// ───────────────────────── Tạo mới ─────────────────────────

const createSchema = productSchema.extend({ skus: z.array(skuSchema).min(1, "Cần ít nhất 1 phiên bản") });
type CreateInput = z.infer<typeof createSchema>;

function CreateProduct() {
  const router = useRouter();
  const { categories, brands, error } = useOptions();
  const form = useForm<CreateInput>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      name: "",
      slug: "",
      description: "",
      categoryId: "",
      brandId: "",
      status: "ACTIVE",
      skus: [{ skuCode: "", attributes: "", price: undefined as unknown as number }],
    },
  });
  const skus = useFieldArray({ control: form.control, name: "skus" });
  const { register, handleSubmit, formState } = form;

  const onSubmit = async (v: CreateInput) => {
    try {
      const created = await productAdminService.create(
        { name: v.name, slug: v.slug, description: v.description, categoryId: v.categoryId, brandId: v.brandId, basePrice: v.basePrice, status: v.status },
        v.skus.map((s) => ({ skuCode: s.skuCode, price: s.price, attributes: parseAttributes(s.attributes) })),
      );
      toast.success("Đã tạo sản phẩm. Tải ảnh và nhập kho cho từng phiên bản.");
      router.replace(`/admin/products/${created.id}`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  if (error) return <ErrorBlock error={error} />;
  if (!categories || !brands) return <LoadingBlock />;

  return (
    <div>
      <BackLink />
      <PageTitle title="Thêm sản phẩm" subtitle="Ảnh tải lên sau khi tạo; tồn kho của phiên bản mới bắt đầu từ 0" />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-6">
        <section className="card p-6">
          <ProductFields form={form} categories={categories} brands={brands} withSlug />
        </section>

        <section className="card p-6">
          <h2 className="mb-1 font-semibold text-neutral-900">Phiên bản (SKU)</h2>
          <p className="mb-4 text-xs text-neutral-500">Thuộc tính ghi dạng “Màu: Đen; Dung lượng: 256GB”.</p>
          <div className="space-y-3">
            {skus.fields.map((f, i) => (
              <div key={f.id} className="grid gap-3 rounded-xl border border-neutral-200 p-4 sm:grid-cols-[1fr_1.5fr_160px_auto]">
                <Field label="Mã SKU" error={formState.errors.skus?.[i]?.skuCode?.message}>
                  <input className="input font-mono" placeholder="IP16P-BLK-256" {...register(`skus.${i}.skuCode`)} />
                </Field>
                <Field label="Thuộc tính" error={formState.errors.skus?.[i]?.attributes?.message}>
                  <input className="input" placeholder="Màu: Đen; Dung lượng: 256GB" {...register(`skus.${i}.attributes`)} />
                </Field>
                <Field label="Giá (₫)" error={formState.errors.skus?.[i]?.price?.message}>
                  <input type="number" min={1} className="input" {...register(`skus.${i}.price`, { valueAsNumber: true })} />
                </Field>
                <div className="flex items-end">
                  <Button type="button" variant="ghost" size="sm" disabled={skus.fields.length === 1} onClick={() => skus.remove(i)} aria-label="Bỏ phiên bản">
                    <FiX />
                  </Button>
                </div>
              </div>
            ))}
          </div>
          {formState.errors.skus?.message && <p className="field-error">{formState.errors.skus.message}</p>}
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className="mt-3"
            icon={<FiPlus />}
            onClick={() => skus.append({ skuCode: "", attributes: "", price: undefined as unknown as number })}
          >
            Thêm phiên bản
          </Button>
        </section>

        <Button type="submit" size="lg" loading={formState.isSubmitting} icon={<FiSave />}>
          Tạo sản phẩm
        </Button>
      </form>
    </div>
  );
}

// ───────────────────────── Sửa ─────────────────────────

function EditProduct({ id }: { id: string }) {
  const product = useAsync(() => productAdminService.get(id), [id]);
  const { categories, brands, error } = useOptions();

  if (product.error && !product.data) return <ErrorBlock error={product.error} onRetry={product.reload} />;
  if (error) return <ErrorBlock error={error} />;
  if (!product.data || !categories || !brands) return <LoadingBlock />;

  const p = product.data;
  return (
    <div>
      <BackLink />
      <PageTitle title={p.name} subtitle={p.status === "ACTIVE" ? "Đang bán" : "Ngừng bán — không hiện trên cửa hàng"} />
      <div className="space-y-6">
        <ProductInfoForm key={p.updatedAt} product={p} categories={categories} brands={brands} onSaved={product.setData} />
        <section className="card p-6">
          <h2 className="mb-1 font-semibold text-neutral-900">Phiên bản (SKU)</h2>
          <p className="mb-4 text-xs text-neutral-500">
            Mã SKU không đổi được (là khóa liên kết với tồn kho và đơn hàng). Nhập kho ở tab Tồn kho.
          </p>
          <div className="space-y-3">
            {p.skus.map((s) => (
              <SkuRow key={s.skuCode} productName={p.name} sku={s} canDelete={p.skus.length > 1} onChanged={product.reload} />
            ))}
          </div>
          <AddSkuForm productId={p.id} onAdded={product.reload} />
        </section>
      </div>
    </div>
  );
}

function ProductInfoForm({
  product,
  categories,
  brands,
  onSaved,
}: {
  product: ProductDetail;
  categories: CategorySummary[];
  brands: BrandSummary[];
  onSaved: (p: ProductDetail) => void;
}) {
  const form = useForm<ProductFormInput>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      name: product.name,
      slug: product.slug,
      description: product.description ?? "",
      categoryId: product.category?.id ?? "",
      brandId: product.brand?.id ?? "",
      basePrice: Number(product.basePrice),
      status: product.status === "ACTIVE" ? "ACTIVE" : "INACTIVE",
    },
  });

  const onSubmit = async (v: ProductFormInput) => {
    try {
      const saved = await productAdminService.update(product.id, {
        name: v.name,
        description: v.description,
        categoryId: v.categoryId,
        brandId: v.brandId,
        basePrice: v.basePrice,
        status: v.status,
      });
      onSaved(saved);
      toast.success("Đã lưu thông tin sản phẩm");
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="card p-6">
      <ProductFields form={form} categories={categories} brands={brands} />
      <Button type="submit" className="mt-5" loading={form.formState.isSubmitting} icon={<FiSave />}>
        Lưu thông tin
      </Button>
    </form>
  );
}

function SkuRow({ productName, sku, canDelete, onChanged }: { productName: string; sku: ProductSku; canDelete: boolean; onChanged: () => void }) {
  const [price, setPrice] = useState(String(Number(sku.price)));
  const [attrs, setAttrs] = useState(formatAttributes(sku.attributes));
  const [busy, setBusy] = useState<"save" | "delete" | null>(null);

  const save = async () => {
    const value = Number(price);
    if (!Number.isFinite(value) || value <= 0) {
      toast.error("Giá phải lớn hơn 0");
      return;
    }
    setBusy("save");
    try {
      await productAdminService.updateSku(sku.skuCode, value, parseAttributes(attrs));
      toast.success(`Đã lưu ${sku.skuCode}`);
      onChanged();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(null);
    }
  };

  const remove = async () => {
    const ok = await confirmAction(
      `Xóa phiên bản ${sku.skuCode}?`,
      "Khách không đặt được phiên bản này nữa. Đơn hàng cũ vẫn giữ tên và giá tại thời điểm mua.",
      "Xóa",
      true,
    );
    if (!ok) return;
    setBusy("delete");
    try {
      await productAdminService.deleteSku(sku.skuCode);
      toast.success(`Đã xóa ${sku.skuCode}`);
      onChanged();
    } catch (err) {
      toast.error(errorMessage(err));
      setBusy(null);
    }
  };

  return (
    <div className="grid items-end gap-3 rounded-xl border border-neutral-200 p-4 lg:grid-cols-[auto_1fr_1.5fr_150px_auto]">
      <SkuImageUploader skuCode={sku.skuCode} name={productName} imageUrl={sku.imageUrl} onUploaded={onChanged} />
      <Field label="Mã SKU">
        <div className="input bg-neutral-50 font-mono text-neutral-500">{sku.skuCode}</div>
      </Field>
      <Field label="Thuộc tính">
        <input className="input" value={attrs} onChange={(e) => setAttrs(e.target.value)} placeholder="Màu: Đen; Dung lượng: 256GB" />
      </Field>
      <Field label="Giá (₫)">
        <input type="number" min={1} className="input" value={price} onChange={(e) => setPrice(e.target.value)} />
      </Field>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" className="h-[42px]" loading={busy === "save"} disabled={!!busy} onClick={save} icon={<FiSave />}>
          Lưu
        </Button>
        <Button
          variant="danger"
          size="sm"
          className="h-[42px]"
          loading={busy === "delete"}
          disabled={!!busy || !canDelete}
          title={canDelete ? undefined : "Sản phẩm phải còn ít nhất 1 phiên bản; muốn ngừng bán thì dùng nút Ngừng bán"}
          onClick={remove}
          icon={<FiTrash2 />}
        >
          Xóa
        </Button>
      </div>
    </div>
  );
}

function AddSkuForm({ productId, onAdded }: { productId: string; onAdded: () => void }) {
  const [open, setOpen] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<SkuFormInput>({ resolver: zodResolver(skuSchema), defaultValues: { skuCode: "", attributes: "" } });

  const onSubmit = async (v: SkuFormInput) => {
    try {
      await productAdminService.addSku(productId, { skuCode: v.skuCode, price: v.price, attributes: parseAttributes(v.attributes) });
      toast.success(`Đã thêm ${v.skuCode} (tồn kho 0, nhập kho ở tab Tồn kho)`);
      reset({ skuCode: "", attributes: "" });
      setOpen(false);
      onAdded();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  if (!open) {
    return (
      <Button variant="secondary" size="sm" className="mt-3" icon={<FiPlus />} onClick={() => setOpen(true)}>
        Thêm phiên bản
      </Button>
    );
  }
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="mt-3 grid items-start gap-3 rounded-xl border border-dashed border-neutral-300 p-4 sm:grid-cols-[1fr_1.5fr_150px_auto]">
      <Field label="Mã SKU" error={errors.skuCode?.message}>
        <input className="input font-mono" {...register("skuCode")} />
      </Field>
      <Field label="Thuộc tính" error={errors.attributes?.message}>
        <input className="input" placeholder="Màu: Đen; Dung lượng: 256GB" {...register("attributes")} />
      </Field>
      <Field label="Giá (₫)" error={errors.price?.message}>
        <input type="number" min={1} className="input" {...register("price", { valueAsNumber: true })} />
      </Field>
      <div className="flex gap-2 pt-6">
        <Button type="submit" size="sm" className="h-[42px]" loading={isSubmitting}>
          Thêm
        </Button>
        <Button type="button" variant="ghost" size="sm" className="h-[42px]" onClick={() => setOpen(false)}>
          Hủy
        </Button>
      </div>
    </form>
  );
}

// ───────────────────────── Dùng chung ─────────────────────────

function ProductFields({
  form,
  categories,
  brands,
  withSlug = false,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- dùng cho cả form tạo (có skus) và form sửa
  form: any;
  categories: CategorySummary[];
  brands: BrandSummary[];
  withSlug?: boolean;
}) {
  const { register, formState } = form;
  const e = formState.errors;
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="Tên sản phẩm" error={e.name?.message} wide>
        <input className="input" {...register("name")} />
      </Field>
      {withSlug && (
        <Field label="Slug (để trống = tự sinh từ tên)" error={e.slug?.message} wide>
          <input className="input font-mono" placeholder="iphone-16-pro" {...register("slug")} />
        </Field>
      )}
      <Field label="Mô tả" error={e.description?.message} wide>
        <textarea rows={3} className="input" {...register("description")} />
      </Field>
      <Field label="Danh mục" error={e.categoryId?.message}>
        <select className="input" {...register("categoryId")}>
          <option value="">— Chọn danh mục —</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Thương hiệu" error={e.brandId?.message}>
        <select className="input" {...register("brandId")}>
          <option value="">— Chọn thương hiệu —</option>
          {brands.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Giá hiển thị “Giá từ” (₫)" error={e.basePrice?.message}>
        <input type="number" min={1} className="input" {...register("basePrice", { valueAsNumber: true })} />
      </Field>
      <Field label="Trạng thái" error={e.status?.message}>
        <select className="input" {...register("status")}>
          <option value="ACTIVE">Đang bán</option>
          <option value="INACTIVE">Ngừng bán</option>
        </select>
      </Field>
    </div>
  );
}

function Field({ label, error, wide, children }: { label: string; error?: string; wide?: boolean; children: React.ReactNode }) {
  return (
    <div className={wide ? "sm:col-span-2" : ""}>
      <div className="label">{label}</div>
      {children}
      {error && <p className="field-error">{error}</p>}
    </div>
  );
}
