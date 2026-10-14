import { Suspense } from "react";
import { LoadingBlock } from "@/app/components/card/States";
import { OrderDetailView } from "./OrderDetailView";

export default async function OrderPage({ params }: PageProps<"/orders/[id]">) {
  const { id } = await params;
  // OrderDetailView đọc ?pay= bằng useSearchParams -> cần Suspense
  return (
    <Suspense fallback={<LoadingBlock />}>
      <OrderDetailView id={id} />
    </Suspense>
  );
}
