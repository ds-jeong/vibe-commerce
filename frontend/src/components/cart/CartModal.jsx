import React from 'react';

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
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 px-4 py-8 animate-in fade-in">
      <div className="w-full max-w-2xl rounded-2xl bg-white shadow-2xl transition duration-300 ease-out">
        <div className="flex items-center justify-between border-b border-gray-100 p-6">
          <div>
            <h3 className="text-xl font-extrabold text-gray-800">🛒 장바구니</h3>
            <p className="mt-1 text-xs text-gray-400">
              {userToken
                ? '회원님의 DB 장바구니입니다.'
                : '현재 담겨있는 상품을 확인하세요.'}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setIsCartModalOpen(false)}
            className="rounded-lg px-3 py-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          >
            ✕
          </button>
        </div>

        <div className="max-h-[60vh] overflow-y-auto p-6">
          {guestCart.length === 0 ? (
            <div className="py-16 text-center">
              <div className="text-5xl">🛒</div>
              <h4 className="mt-4 text-base font-bold text-gray-700">
                장바구니가 비어있습니다.
              </h4>
            </div>
          ) : (
            <div className="space-y-3">
              <label className="flex items-center gap-2 text-sm font-bold text-gray-700">
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
                  className="rounded-xl border border-gray-200 p-4"
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
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold text-gray-800">
                        {item.name}
                      </p>
                      <p className="mt-1 text-xs text-gray-400">
                        {formatPrice(item.price)}원
                      </p>
                    </div>
                    <div className="flex items-center rounded-lg border border-gray-200">
                      <button
                        type="button"
                        onClick={() => decreaseCartQuantity(item.id)}
                        className="h-9 w-9 text-gray-500"
                      >
                        −
                      </button>
                      <span className="flex h-9 min-w-10 items-center justify-center border-x text-sm font-bold">
                        {item.quantity}
                      </span>
                      <button
                        type="button"
                        onClick={() => increaseCartQuantity(item.id)}
                        className="h-9 w-9 text-gray-500"
                      >
                        +
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeFromCart(item.id)}
                      className="rounded-lg p-2 text-gray-300 hover:text-red-500"
                    >
                      🗑️
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {guestCart.length > 0 && (
          <div className="border-t border-gray-100 p-6">
            <div className="mb-4 flex items-center justify-between">
              <p className="text-sm font-bold text-gray-700">
                {checkedCartItems.reduce(
                  (total, item) => total + Number(item.quantity || 0),
                  0
                )}
                개
              </p>
              <p className="text-xl font-extrabold text-blue-600">
                {formatPrice(checkedCartTotalPrice)}원
              </p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={clearCart}
                className="rounded-xl border border-red-100 bg-white px-4 py-3 text-xs font-bold text-red-500"
              >
                전체 삭제
              </button>
              <button
                type="button"
                onClick={handleCartOrder}
                disabled={memberOrderLoading}
                className="flex-1 rounded-xl bg-blue-600 py-3 text-sm font-bold text-white disabled:opacity-50"
              >
                장바구니 주문하기
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
