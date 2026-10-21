export function isSaleStopped(product) {
  return product?.saleStatus === 'STOPPED';
}

export function isOutOfStock(product, quantity = 1) {
  if (product?.stockQuantity == null) {
    return false;
  }
  const amount = Number(quantity) > 0 ? Number(quantity) : 1;
  return Number(product.stockQuantity) < amount;
}

export function purchaseBlockMessage(product, quantity = 1) {
  if (isSaleStopped(product)) {
    return '판매중지된 상품은 주문할 수 없습니다.';
  }
  if (isOutOfStock(product, quantity)) {
    return '재고가 부족합니다.';
  }
  return '';
}
