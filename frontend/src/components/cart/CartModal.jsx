import React from 'react';
import { resolveImageUrl } from '../../utils/media';

export default function CartModal(props) {
  const {
    isCartModalOpen,
    setIsCartModalOpen,
    userToken,
    guestCart,
    selectedCartIds,
    setSelectedCartIds,
    decreaseCartQuantity,
    increaseCartQuantity,
    removeFromCart,
    formatPrice,
    checkedCartItems,
    checkedCartTotalPrice,
    clearCart,
    handleCartOrder,
    memberOrderLoading,
  } = props;

  if (!isCartModalOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/40 px-4 py-8">
      <div className="w-full max-w-2xl rounded-md bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-100 p-6">
          <div>
            <h3 className="text-xl font-semibold text-black">장바구니</h3>
            <p className="mt-1 text-xs text-slate-400">
              {userToken
                ? '회원 장바구니입니다.'
                : '담고 있는 상품을 확인해 주세요.'}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setIsCartModalOpen(false)}
            className="rounded-md px-3 py-2 text-slate-400 hover:bg-slate-50 hover:text-black"
          >
            ✕
          </button>
        </div>

        <div className="max-h-[60vh] overflow-y-auto p-6">
          {guestCart.length === 0 ? (
            <div className="py-16 text-center">
              <h4 className="text-base font-semibold text-black">
                장바구니가 비어 있습니다.
              </h4>
              <p className="mt-2 text-sm text-slate-400">원하는 상품을 담아 보세요.</p>
            </div>
          ) : (
            <div className="space-y-3">
              <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                <input
                  type="checkbox"
                  checked={
                    guestCart.length > 0 &&
                    selectedCartIds.length === guestCart.length
                  }
                  onChange={(e) => {
                    if (e.target.checked) {
                      setSelectedCartIds(
                        guestCart.map((item) => Number(item.id))
                      );
                    } else {
                      setSelectedCartIds([]);
                    }
                  }}
                />
                전체 선택
              </label>
              {guestCart.map((item) => (
                <div
                  key={item.id}
                  className="rounded-md border border-slate-100 p-4"
                >
                  <div className="flex items-center justify-between gap-4">
                    <input
                      type="checkbox"
                      checked={selectedCartIds.includes(Number(item.id))}
                      onChange={(e) => {
                        const itemId = Number(item.id);
                        setSelectedCartIds((prev) =>
                          e.target.checked
                            ? [...prev, itemId]
                            : prev.filter((id) => Number(id) !== itemId)
                        );
                      }}
                      className="h-4 w-4 shrink-0"
                    />
                    <img
                      src={resolveImageUrl(item.imageUrl || item.image)}
                      alt={item.name}
                      className="h-14 w-14 rounded-md object-cover"
                      onError={(e) => {
                        e.currentTarget.src = '/images/default-product.svg';
                      }}
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-black">
                        {item.name}
                      </p>
                      <p className="mt-1 text-xs text-slate-400">
                        {formatPrice(item.price)}원
                      </p>
                    </div>
                    <div className="flex items-center rounded-md border border-slate-200">
                      <button
                        type="button"
                        onClick={() => decreaseCartQuantity(item.id)}
                        className="h-9 w-9 text-slate-500"
                      >
                        −
                      </button>
                      <span className="flex h-9 min-w-10 items-center justify-center border-x text-sm font-semibold">
                        {item.quantity}
                      </span>
                      <button
                        type="button"
                        onClick={() => increaseCartQuantity(item.id)}
                        className="h-9 w-9 text-slate-500"
                      >
                        +
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeFromCart(item.id)}
                      className="rounded-md p-2 text-xs font-semibold text-slate-400 hover:text-black"
                    >
                      삭제
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {guestCart.length > 0 && (
          <div className="border-t border-slate-100 p-6">
            <div className="mb-4 flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-700">
                {checkedCartItems.reduce(
                  (total, item) => total + Number(item.quantity || 0),
                  0
                )}
                개 선택
              </p>
              <p className="text-xl font-semibold text-black">
                {formatPrice(checkedCartTotalPrice)}원
              </p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={clearCart}
                className="rounded-md border border-slate-200 bg-white px-4 py-3 text-xs font-semibold text-slate-600"
              >
                전체 삭제
              </button>
              <button
                type="button"
                onClick={handleCartOrder}
                disabled={memberOrderLoading}
                className="flex-1 rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:opacity-50"
              >
                주문하기
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
