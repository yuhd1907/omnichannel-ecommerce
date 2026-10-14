export interface CartItem {
  productId: string;
  productName: string;
  skuCode: string;
  skuLabel: string;
  price: number;
  imageUrl: string | null;
  quantity: number;
}
