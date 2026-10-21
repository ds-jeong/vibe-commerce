import React, { memo } from 'react';
import { Link } from 'react-router-dom';
import ProductImage from '../common/ProductImage';
import { isOutOfStock, isSaleStopped } from '../../utils/saleStatus';

function ProductCard({
  product,
  formatPrice,
  onAddToCart,
  onBuyNow,
  showLikes = false,
  likePending = false,
  onToggleLike,
}) {
  const stopped = isSaleStopped(product);
  const outOfStock = isOutOfStock(product);
  const blocked = stopped || outOfStock;
  const liked = Boolean(product.likedByCurrentUser);
  return (
    <div className="relative overflow-hidden rounded-md border border-slate-100 bg-white shadow-sm transition duration-300 hover:scale-[1.01] hover:shadow-md">
      {showLikes ? (
        <button
          type="button"
          aria-pressed={liked}
          aria-label={liked ? '좋아요 취소' : '좋아요'}
          disabled={likePending}
          onClick={() => onToggleLike(product)}
          className="absolute right-3 top-3 z-10 inline-flex items-center gap-1 rounded-full bg-white/95 px-2.5 py-1 text-xs font-semibold text-slate-700 shadow-sm disabled:opacity-60"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true" className={`h-4 w-4 ${liked ? 'fill-rose-500 text-rose-500' : 'fill-none text-slate-500'}`}>
            <path
              d="M12 20s-7-4.4-7-9.1C5 8 6.8 6.2 9.2 6.2c1.3 0 2.4.6 2.8 1.6.4-1 1.5-1.6 2.8-1.6 2.4 0 4.2 1.8 4.2 4.7C19 15.6 12 20 12 20z"
              stroke="currentColor"
              strokeWidth="1.8"
            />
          </svg>
          <span>{Number(product.likeCount || 0).toLocaleString('ko-KR')}</span>
        </button>
      ) : null}
      <Link to={`/products/${product.id}`} className="block text-inherit no-underline">
        <div className="flex h-52 items-center justify-center bg-slate-50">
          <ProductImage
            src={product.imageUrl || product.image}
            alt={product.name}
            className="h-full w-full object-cover"
          />
        </div>
        <div className="px-5 pt-5">
          <h3 className="line-clamp-2 min-h-[48px] text-base font-semibold text-black">{product.name}</h3>
          <div className="mt-4">
            <span className="text-xl font-semibold tracking-tight text-black">{formatPrice(product.price)}</span>
            <span className="ml-1 text-sm text-slate-500">원</span>
          </div>
          {blocked ? (
            <p className="mt-2 text-xs font-semibold text-slate-500">
              {stopped ? '판매중지 · 구매 불가' : '품절 · 구매 불가'}
            </p>
          ) : null}
        </div>
      </Link>
      <div className="grid grid-cols-2 gap-2 p-5">
        <button
          type="button"
          disabled={blocked}
          onClick={() => onAddToCart(product)}
          className="rounded-md border border-slate-200 py-3 text-xs font-semibold text-slate-700 transition hover:scale-[1.01] hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          장바구니
        </button>
        <button
          type="button"
          disabled={blocked}
          onClick={() => onBuyNow(product)}
          className="rounded-md bg-[#0A192F] py-3 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:cursor-not-allowed disabled:opacity-50"
        >
          구매하기
        </button>
      </div>
    </div>
  );
}

export default memo(ProductCard);
