import React, { useCallback, useRef } from 'react';
import useProductListPage from '../hooks/useProductListPage';
import ResponsiveHeader from '../components/common/ResponsiveHeader';
import Pagination from '../components/common/Pagination';
import CartModal from '../components/cart/CartModal';
import GuestOrderModal from '../components/cart/GuestOrderModal';
import ProductCard from '../components/product/ProductCard';

export default function ProductListPage() {
  const p = useProductListPage();
  const pRef = useRef(p);
  pRef.current = p;

  const onAddToCart = useCallback((product) => {
    pRef.current.handleAddToCart(product);
  }, []);
  const onBuyNow = useCallback((product) => {
    pRef.current.handleImmediateBuy(product);
  }, []);
  const formatPrice = useCallback((price) => pRef.current.formatPrice(price), []);

  return (
    <div className="min-h-screen bg-gray-50 font-sans">
      <ResponsiveHeader {...p} />

      <main className="mx-auto max-w-7xl px-4 py-8">
        <div className="mb-8">
          <p className="mb-1 text-xs font-bold uppercase tracking-widest text-blue-600">
            VibeCommerce
          </p>
          <h2 className="text-2xl font-extrabold tracking-tight text-gray-900 sm:text-3xl">
            상품 목록
          </h2>
        </div>

        {p.loading ? (
          <div className="flex min-h-[300px] items-center justify-center text-sm text-gray-500">
            상품을 불러오는 중입니다...
          </div>
        ) : p.products.length === 0 ? (
          <div className="rounded-2xl border bg-white py-20 text-center">
            등록된 상품이 없습니다.
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {p.products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  formatPrice={formatPrice}
                  onAddToCart={onAddToCart}
                  onBuyNow={onBuyNow}
                />
              ))}
            </div>
            <Pagination {...p} />
          </>
        )}
      </main>

      {p.isChoiceModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-7 shadow-2xl transition duration-300">
            <h3 className="text-center text-xl font-extrabold">바로구매</h3>
            <div className="mt-7 space-y-3">
              <button
                type="button"
                onClick={p.handleSelectMemberOrder}
                className="w-full rounded-xl bg-blue-600 py-4 text-sm font-bold text-white"
              >
                👤 회원으로 구매하기
              </button>
              {!p.userToken && (
                <button
                  type="button"
                  onClick={p.handleSelectGuestOrder}
                  className="w-full rounded-xl border py-4 text-sm font-bold"
                >
                  🛍️ 비회원으로 구매하기
                </button>
              )}
              <button
                type="button"
                onClick={() => p.setIsChoiceModalOpen(false)}
                className="w-full py-3 text-xs text-gray-400"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      <GuestOrderModal {...p} />
      <CartModal {...p} />
    </div>
  );
}
