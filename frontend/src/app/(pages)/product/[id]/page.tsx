import { ProductDetailView } from "./ProductDetailView";

export default async function ProductPage({ params }: PageProps<"/product/[id]">) {
  const { id } = await params;
  return <ProductDetailView id={id} />;
}
