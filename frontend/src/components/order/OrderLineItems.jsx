import React from 'react';
import { productImageSrc } from '../../utils/validation';

export default function OrderLineItems({ items, formatPrice }) {
  const rows = Array.isArray(items) ? items : [];
  if (rows.length === 0) {
    return <p className="text-xs text-gray-400">상품 정보 없음</p>;
  }

  return (
    <div className="space-y-2">
      {rows.map((item, index) => {
        const product = item?.product || {};
        const unitPrice = item?.orderPrice ?? product?.price ?? 0;
        return (
          <div key={item?.id || index} className="flex items-center gap-3">
            <img
              src={productImageSrc(product)}
              alt={product?.name || '상품'}
              className="h-14 w-14 rounded-lg bg-gray-100 object-cover"
              onError={(e) => {
                e.currentTarget.src = '/images/default-product.svg';
              }}
            />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold text-gray-800">
                {product?.name || '상품'}
              </p>
              <p className="text-xs text-gray-500">
                {typeof formatPrice === 'function'
                  ? `${formatPrice(unitPrice)}원`
                  : `₩${Number(unitPrice || 0).toLocaleString()}`}
                {' · '}
                {item?.count || 0}개
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}
