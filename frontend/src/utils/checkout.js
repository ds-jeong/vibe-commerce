export const FREE_SHIPPING_THRESHOLD = 50000;
export const DEFAULT_DELIVERY_FEE = 3000;

export function computeCheckoutSummary(items) {
  const rows = Array.isArray(items) ? items : [];
  const goodsAmount = rows.reduce(
    (sum, item) =>
      sum + Number(item?.price || 0) * Number(item?.quantity || 1),
    0
  );
  const deliveryFee =
    goodsAmount > 0 && goodsAmount < FREE_SHIPPING_THRESHOLD
      ? DEFAULT_DELIVERY_FEE
      : 0;
  return {
    goodsAmount,
    deliveryFee,
    payableAmount: goodsAmount + deliveryFee,
  };
}

export function resolveItemImage(item) {
  return (
    item?.imageUrl ||
    item?.image ||
    item?.productImageUrl ||
    item?.product?.imageUrl ||
    ''
  );
}
