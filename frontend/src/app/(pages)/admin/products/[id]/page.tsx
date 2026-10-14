import { ProductEditor } from "./ProductEditor";

export default async function AdminProductPage({ params }: PageProps<"/admin/products/[id]">) {
  const { id } = await params;
  return <ProductEditor id={id} />;
}
