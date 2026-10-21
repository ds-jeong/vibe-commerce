import React from 'react';
import { Link } from 'react-router-dom';
import Pagination from '../common/Pagination';
import ProductImage from '../common/ProductImage';
import { isOutOfStock, isSaleStopped, purchaseBlockMessage } from '../../utils/saleStatus';

export default function LikedProducts({
  products,
  loading,
  formatPrice,
  formatDate,
  pendingIds,
  onUnlike,
  onAddToCart,
  currentPage,
  totalPages,
  onPreviousPage,
  onNextPage,
  setCurrentPage,
}) {
  const rows = Array.isArray(products) ? products : [];

  if (loading && rows.length === 0) {
    return <p className="py-16 text-center text-sm text-gray-400">좋아요한 상품을 불러오는 중입니다.</p>;
  }

  if (rows.length === 0) {
    return (
      <div className="py-16 text-center">
        <p className="text-sm text-gray-400">좋아요한 상품이 없습니다.</p>
        <Link
          to="/"
          className="mt-4 inline-flex rounded-md bg-[#0A192F] px-4 py-2 text-xs font-semibold text-white"
        >
          상품 보러가기
        </Link>
      </div>
    );
  }

  return (
    <div>
      <p className="mb-4 text-sm text-gray-500">최근 좋아요한 상품부터 보여 줍니다. 하트를 누르면 이 목록에서 빠집니다.</p>
      <ul className="space-y-3">
        {rows.map((product) => {
          const stopped = isSaleStopped(product);
          const outOfStock = isOutOfStock(product);
          const pending = pendingIds.includes(product.id);
          return (
            <li key={product.id} className="flex flex-wrap items-center gap-4 rounded-xl border border-gray-100 p-3">
              <Link to={`/products/${product.id}`} className="h-16 w-16 shrink-0 overflow-hidden rounded-lg bg-slate-50">
                <ProductImage src={product.imageUrl} alt={product.name} className="h-full w-full object-cover" />
              </Link>
              <div className="min-w-0 flex-1">
                <Link to={`/products/${product.id}`} className="line-clamp-1 text-sm font-semibold text-gray-900">
                  {product.name}
                </Link>
                <p className="mt-1 text-sm font-semibold text-black">{formatPrice(product.price)}원</p>
                <p className="mt-1 text-xs text-gray-400">좋아요 {formatDate(product.likedAt)}</p>
                {stopped || outOfStock ? (
                  <p className="mt-1 text-xs font-semibold text-slate-500">{stopped ? '판매중지' : '품절'}</p>
                ) : null}
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  aria-label={`${product.name} 좋아요 취소`}
                  aria-pressed="true"
                  disabled={pending}
                  onClick={() => onUnlike(product)}
                  className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2.5 py-1 text-xs font-semibold text-rose-500 disabled:opacity-60"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true" className="h-4 w-4 fill-rose-500">
                    <path d="M12 20s-7-4.4-7-9.1C5 8 6.8 6.2 9.2 6.2c1.3 0 2.4.6 2.8 1.6.4-1 1.5-1.6 2.8-1.6 2.4 0 4.2 1.8 4.2 4.7C19 15.6 12 20 12 20z" />
                  </svg>
                  {Number(product.likeCount || 0).toLocaleString('ko-KR')}
                </button>
                <button
                  type="button"
                  disabled={Boolean(purchaseBlockMessage(product))}
                  onClick={() => onAddToCart(product)}
                  className="rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  장바구니
                </button>
              </div>
            </li>
          );
        })}
      </ul>
      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        handlePreviousPage={onPreviousPage}
        handleNextPage={onNextPage}
        setCurrentPage={setCurrentPage}
      />
    </div>
  );
}
