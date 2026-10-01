import React, { useCallback, useRef } from 'react';
import useProductListPage from '../hooks/useProductListPage';
import ResponsiveHeader from '../components/common/ResponsiveHeader';
import Pagination from '../components/common/Pagination';
import CartModal from '../components/cart/CartModal';
import GuestOrderModal from '../components/cart/GuestOrderModal';
import OrderConfirmModal from '../components/order/OrderConfirmModal';
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
    <div className="min-h-screen bg-white font-sans text-[#0A192F]">
      <ResponsiveHeader {...p} />

      <main className="mx-auto max-w-7xl px-4 py-10">
        <div className="mb-10">
          <p className="mb-1 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-400">
            Collection
          </p>
          <h2 className="text-2xl font-semibold tracking-tight text-black sm:text-3xl">
            상품 목록
          </h2>
        </div>

        {p.loading ? (
          <div className="flex min-h-[300px] items-center justify-center text-sm text-slate-500">
            상품을 불러오는 중입니다.
          </div>
        ) : p.products.length === 0 ? (
          <div className="rounded-md border border-slate-100 bg-slate-50 py-20 text-center text-slate-500">
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
          <div className="w-full max-w-md rounded-md bg-white p-7 shadow-sm transition duration-300">
            <h3 className="text-center text-xl font-semibold text-black">구매 방법 선택</h3>
            <p className="mt-2 text-center text-sm text-slate-500">
              로그인 후 구매하거나, 비회원으로 주문을 진행할 수 있습니다.
            </p>
            <div className="mt-7 space-y-3">
              <button
                type="button"
                onClick={p.handleSelectMemberOrder}
                className="w-full rounded-md bg-[#0A192F] py-4 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
              >
                회원으로 구매하기
              </button>
              {!p.userToken && (
                <button
                  type="button"
                  onClick={p.handleSelectGuestOrder}
                  className="w-full rounded-md border border-slate-200 py-4 text-sm font-semibold text-slate-700 transition hover:scale-[1.01] hover:bg-slate-50"
                >
                  비회원으로 구매하기
                </button>
              )}
              <button
                type="button"
                onClick={() => p.setIsChoiceModalOpen(false)}
                className="w-full py-3 text-xs text-slate-400"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}

      <GuestOrderModal {...p} />
      <OrderConfirmModal
        open={p.isOrderConfirmOpen}
        items={p.checkoutItems}
        formatPrice={formatPrice}
        loading={p.guestOrderLoading || p.memberOrderLoading}
        error={p.guestOrderError}
        onBack={p.handleBackFromOrderConfirm}
        onConfirmPay={p.handleConfirmFinalPay}
      />
      <CartModal {...p} />
    </div>
  );
}
