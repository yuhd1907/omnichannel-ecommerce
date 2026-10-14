import { Suspense } from "react";
import { LoadingBlock } from "@/app/components/card/States";
import { AdminView } from "./AdminView";

export default function AdminPage() {
  // AdminView đọc ?tab= bằng useSearchParams -> cần Suspense
  return (
    <Suspense fallback={<LoadingBlock />}>
      <AdminView />
    </Suspense>
  );
}
