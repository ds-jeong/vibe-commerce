import React, { memo } from 'react';
import { resolveImageUrl } from '../../utils/media';

function ProductCard({ product, formatPrice, onAddToCart, onBuyNow }) {
  return (
    <div className="overflow-hidden rounded-md border border-slate-100 bg-white shadow-sm transition duration-300 hover:scale-[1.01] hover:shadow-md">
      <div className="flex h-52 items-center justify-center bg-slate-50">
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
        <h3 className="line-clamp-2 min-h-[48px] text-base font-semibold text-black">{product.name}</h3>
        <div className="mt-4">
          <span className="text-xl font-semibold tracking-tight text-black">{formatPrice(product.price)}</span>
          <span className="ml-1 text-sm text-slate-500">원</span>
        </div>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => onAddToCart(product)}
            className="rounded-md border border-slate-200 py-3 text-xs font-semibold text-slate-700 transition hover:scale-[1.01] hover:bg-slate-50"
          >
            장바구니
          </button>
          <button
            type="button"
            onClick={() => onBuyNow(product)}
            className="rounded-md bg-[#0A192F] py-3 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
          >
            구매하기
          </button>
        </div>
      </div>
    </div>
  );
}

export default memo(ProductCard);
