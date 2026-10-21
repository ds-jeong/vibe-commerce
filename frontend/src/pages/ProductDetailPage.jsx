import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import useProductListPage from '../hooks/useProductListPage';
import ResponsiveHeader from '../components/common/ResponsiveHeader';
import CartModal from '../components/cart/CartModal';
import GuestOrderModal from '../components/cart/GuestOrderModal';
import OrderConfirmModal from '../components/order/OrderConfirmModal';
import ProductImage from '../components/common/ProductImage';

export default function ProductDetailPage() {
  const { id } = useParams();
  const shop = useProductListPage();
  const [product, setProduct] = useState(null);
  const [status, setStatus] = useState('loading');
  const [errorMessage, setErrorMessage] = useState('');
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    let ignore = false;

    async function load() {
      setStatus('loading');
      setProduct(null);
      setErrorMessage('');
      setQuantity(1);

      if (!/^\d+$/.test(String(id || ''))) {
        setStatus('missing');
        setErrorMessage('상품을 찾을 수 없습니다.');
        return;
      }

      try {
        const response = await fetch(`/api/products/${id}`);
        const data = await response.json().catch(() => ({}));
        if (ignore) {
          return;
        }
        if (!response.ok || data.status === 'FAIL' || data.id == null) {
          setStatus(response.status === 400 || response.status === 404 ? 'missing' : 'error');
          setErrorMessage(data.message || '상품을 불러오지 못했습니다.');
          return;
        }
        setProduct(data);
        setStatus('ready');
      } catch (error) {
        if (!ignore) {
          setStatus('error');
          setErrorMessage(error.message || '상품을 불러오지 못했습니다.');
        }
      }
    }

    load();
    return () => {
      ignore = true;
    };
  }, [id]);

  const stock = product?.stockQuantity;
  const stopped = product?.saleStatus === 'STOPPED';
  const hasStock = !stopped && (stock == null || Number(stock) > 0);
  const maxQuantity = stock == null ? 99 : Math.max(Number(stock) || 0, 0);
  const detailImages = Array.isArray(product?.detailImages)
    ? product.detailImages.filter((image) => image?.imageUrl)
    : [];
  const description = product?.description?.trim() ? product.description : '';

  const changeQuantity = (next) => {
    const value = Math.floor(Number(next));
    if (!Number.isFinite(value)) {
      return;
    }
    setQuantity(Math.min(Math.max(1, value), Math.max(maxQuantity, 1)));
  };

  const headerProps = {
    ...shop,
    handleSearchProducts: (event) => {
      if (event && event.preventDefault) {
        event.preventDefault();
      }
      const keyword = String(shop.searchKeyword || '').trim();
      window.location.assign(keyword ? `/?keyword=${encodeURIComponent(keyword)}` : '/');
    },
  };

  return (
    <div className="min-h-screen bg-white font-sans text-[#0A192F]">
      <ResponsiveHeader {...headerProps} />
      <main className="mx-auto max-w-7xl px-4 py-10">
        <Link to="/" className="text-xs font-semibold text-slate-500 no-underline hover:text-black">
          상품 목록
        </Link>

        {status === 'loading' ? (
          <div className="flex min-h-[300px] items-center justify-center text-sm text-slate-500">
            상품을 불러오는 중입니다.
          </div>
        ) : null}

        {status === 'missing' || status === 'error' ? (
          <div className="mt-8 rounded-md border border-slate-100 bg-slate-50 px-6 py-16 text-center">
            <p className="text-base font-semibold text-black">
              {status === 'missing' ? '상품을 찾을 수 없습니다.' : '상품을 불러오지 못했습니다.'}
            </p>
            <p className="mt-2 text-sm text-slate-500">{errorMessage}</p>
          </div>
        ) : null}

        {status === 'ready' && product ? (
          <div className="mt-6">
            <div className="grid gap-8 lg:grid-cols-2">
            <div>
              <p className="mb-2 text-xs font-semibold text-slate-500">대표 이미지</p>
              <div className="overflow-hidden rounded-md border border-slate-100 bg-slate-50">
                <ProductImage
                  src={product.imageUrl || product.image}
                  alt={product.name}
                  className="h-[420px] w-full object-cover"
                />
              </div>
            </div>
            <div>
              {product.categoryId && product.categoryName ? (
                <Link
                  to={`/?categoryId=${encodeURIComponent(product.categoryId)}`}
                  className="text-xs font-semibold text-slate-500 no-underline hover:text-black"
                >
                  {product.categoryName}
                </Link>
              ) : null}
              <h2 className="mt-2 text-2xl font-semibold tracking-tight text-black sm:text-3xl">{product.name}</h2>
              <p className="mt-4 text-2xl font-semibold text-black">
                {shop.formatPrice(product.price)}
                <span className="ml-1 text-sm font-normal text-slate-500">원</span>
              </p>
              <p className="mt-4 text-sm font-semibold text-slate-700">
                {stopped
                  ? '판매중지 · 구매 불가'
                  : stock == null
                  ? '재고 정보 없음'
                  : hasStock
                    ? `재고 ${Number(stock).toLocaleString('ko-KR')}개 · 구매 가능`
                    : '품절 · 구매 불가'}
              </p>

              <div className="mt-6 flex items-center gap-3">
                <label className="text-xs font-semibold text-slate-500" htmlFor="product-quantity">
                  수량
                </label>
                <input
                  id="product-quantity"
                  type="number"
                  min="1"
                  max={maxQuantity || 1}
                  value={quantity}
                  disabled={!hasStock}
                  onChange={(event) => changeQuantity(event.target.value)}
                  className="w-24 rounded-md border border-slate-200 px-3 py-2 text-sm"
                />
              </div>

              <div className="mt-6 grid grid-cols-2 gap-2">
                <button
                  type="button"
                  disabled={!hasStock}
                  onClick={() => shop.handleAddToCart(product, quantity)}
                  className="rounded-md border border-slate-200 py-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  장바구니
                </button>
                <button
                  type="button"
                  disabled={!hasStock}
                  onClick={() => shop.handleImmediateBuy(product, quantity)}
                  className="rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white transition hover:bg-[#1E293B] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  구매하기
                </button>
              </div>
            </div>
            </div>

            {detailImages.length > 0 ? (
              <section className="mx-auto mt-16 flex w-full max-w-3xl flex-col items-center gap-8">
                <h3 className="text-sm font-semibold text-slate-500">상세 이미지</h3>
                {detailImages.map((image, index) => (
                  <ProductImage
                    key={image.id ?? `${image.imageUrl}-${index}`}
                    src={image.imageUrl}
                    alt={`${product.name} 상세 이미지 ${index + 1}`}
                    className="block w-full object-contain"
                  />
                ))}
              </section>
            ) : null}

            <section className="mx-auto mt-16 w-full max-w-3xl border-t border-slate-100 pt-10">
              <h3 className="text-sm font-semibold text-slate-500">상세 내용</h3>
              <p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-slate-700">
                {description || '등록된 상품 설명이 없습니다.'}
              </p>
            </section>
          </div>
        ) : null}
      </main>

      {shop.isChoiceModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
          <div className="w-full max-w-md rounded-md bg-white p-7 shadow-sm">
            <h3 className="text-center text-xl font-semibold text-black">구매 방법 선택</h3>
            <p className="mt-2 text-center text-sm text-slate-500">
              로그인 후 구매하거나, 비회원으로 주문을 진행할 수 있습니다.
            </p>
            <div className="mt-7 space-y-3">
              <button
                type="button"
                onClick={shop.handleSelectMemberOrder}
                className="w-full rounded-md bg-[#0A192F] py-4 text-sm font-semibold text-white"
              >
                회원으로 구매하기
              </button>
              {!shop.userToken && (
                <button
                  type="button"
                  onClick={shop.handleSelectGuestOrder}
                  className="w-full rounded-md border border-slate-200 py-4 text-sm font-semibold text-slate-700"
                >
                  비회원으로 구매하기
                </button>
              )}
              <button
                type="button"
                onClick={() => shop.setIsChoiceModalOpen(false)}
                className="w-full py-3 text-xs text-slate-400"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
      <GuestOrderModal {...shop} />
      <OrderConfirmModal
        open={shop.isOrderConfirmOpen}
        items={shop.checkoutItems}
        formatPrice={shop.formatPrice}
        loading={shop.guestOrderLoading || shop.memberOrderLoading}
        error={shop.guestOrderError}
        onBack={shop.handleBackFromOrderConfirm}
        onConfirmPay={shop.handleConfirmFinalPay}
      />
      <CartModal {...shop} />
    </div>
  );
}
