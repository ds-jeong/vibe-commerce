import React, { memo } from 'react';
import { resolveImageUrl } from '../../utils/media';

function ProductCard({ product, formatPrice, onAddToCart, onBuyNow }) {
  return (
    <div className="overflow-hidden rounded-2xl border bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:shadow-lg">
      <div className="flex h-52 items-center justify-center bg-gray-100">
        <img
          src={resolveImageUrl(product.imageUrl || product.image)}
          alt={product.name}
          className="h-full w-full object-cover"
          onError={(e) => {
            e.currentTarget.src = '/images/default-product.svg';
          }}
        />
      </div>
      <div className="p-5">
        <h3 className="line-clamp-2 min-h-[48px] text-base font-bold">{product.name}</h3>
        <div className="mt-4">
          <span className="text-xl font-extrabold">{formatPrice(product.price)}</span>
          <span className="ml-1 text-sm text-gray-500">원</span>
        </div>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => onAddToCart(product)}
            className="rounded-xl border py-3 text-xs font-bold transition hover:bg-blue-50"
          >
            🛒 담기
          </button>
          <button
            type="button"
            onClick={() => onBuyNow(product)}
            className="rounded-xl bg-blue-600 py-3 text-xs font-bold text-white transition hover:bg-blue-700"
          >
            ⚡ 바로구매
          </button>
        </div>
      </div>
    </div>
  );
}

export default memo(ProductCard);
