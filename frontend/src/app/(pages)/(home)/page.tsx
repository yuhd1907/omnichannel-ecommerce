import { Suspense } from "react";
import { LoadingBlock } from "@/app/components/card/States";
import { Storefront } from "./Storefront";

export default function HomePage() {
  // useSearchParams (q, categoryId, page) cần Suspense để phần còn lại của trang vẫn prerender được
  return (
    <Suspense fallback={<LoadingBlock />}>
      <Storefront />
    </Suspense>
  );
}
