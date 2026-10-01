import React from 'react';

export default function GuestOrderModal(props) {
  const {
    isGuestOrderFormOpen,
    setIsGuestOrderFormOpen,
    checkoutMode,
    checkoutItems,
    selectedProduct,
    buildOrderName,
    toCheckoutItem,
    formatPrice,
    handleGuestOrderSubmit,
    guestName,
    handleGuestNameChange,
    guestNameMessage,
    guestNameRegex,
    guestPhone,
    handleGuestPhoneChange,
    guestPhoneMessage,
    guestPhoneRegex,
    guestZipcode,
    guestRoadAddress,
    guestDetailAddress,
    setGuestDetailAddress,
    handleAddressSearch,
    guestOrderError,
    guestOrderLoading,
  } = props;

  if (!isGuestOrderFormOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 px-4 py-8">
      <div className="w-full max-w-lg rounded-2xl bg-white p-7 shadow-2xl transition duration-300">
        <div className="mb-6 flex items-start justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-blue-600">
              {checkoutMode === 'member-address' ? 'Member Address' : 'Guest Order'}
            </p>
            <h3 className="mt-1 text-xl font-extrabold text-gray-800">
              {checkoutMode === 'member-address' ? '배송지 입력' : '비회원 주문'}
            </h3>
          </div>
          <button
            type="button"
            onClick={() => setIsGuestOrderFormOpen(false)}
            className="rounded-lg px-3 py-2 text-gray-400"
          >
            ✕
          </button>
        </div>

        {(checkoutItems[0] || selectedProduct) && (
          <div className="mb-5 rounded-xl bg-gray-50 p-4">
            <p className="text-sm font-bold text-gray-800">
              {buildOrderName(
                checkoutItems.length > 0
                  ? checkoutItems
                  : [toCheckoutItem(selectedProduct)]
              )}
            </p>
            <p className="mt-1 text-base font-extrabold text-blue-600">
              {formatPrice(
                checkoutItems.length > 0
                  ? checkoutItems.reduce(
                      (sum, item) =>
                        sum +
                        Number(item.price || 0) * Number(item.quantity || 1),
                      0
                    )
                  : selectedProduct.price
              )}
              원
            </p>
          </div>
        )}

        <form onSubmit={handleGuestOrderSubmit} className="space-y-5">
          <input
            type="text"
            value={guestName}
            onChange={handleGuestNameChange}
            placeholder="주문자 이름"
            className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
          />
          {guestNameMessage && (
            <p
              className={`text-xs font-semibold ${
                guestNameRegex.test(guestName) ? 'text-green-600' : 'text-red-500'
              }`}
            >
              {guestNameMessage}
            </p>
          )}
          <input
            type="tel"
            value={guestPhone}
            onChange={handleGuestPhoneChange}
            placeholder="01012345678"
            className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
          />
          {guestPhoneMessage && (
            <p
              className={`text-xs font-semibold ${
                guestPhoneRegex.test(guestPhone)
                  ? 'text-green-600'
                  : 'text-red-500'
              }`}
            >
              {guestPhoneMessage}
            </p>
          )}
          <div className="flex gap-2">
            <input
              value={guestZipcode}
              readOnly
              placeholder="우편번호"
              className="flex-1 rounded-xl border bg-gray-100 px-4 py-3 text-sm"
            />
            <button
              type="button"
              onClick={handleAddressSearch}
              className="rounded-xl bg-gray-800 px-4 py-3 text-xs font-bold text-white"
            >
              주소 검색
            </button>
          </div>
          <input
            value={guestRoadAddress}
            readOnly
            placeholder="도로명 주소"
            className="w-full rounded-xl border bg-gray-100 px-4 py-3 text-sm"
          />
          <input
            id="guest-detail-address"
            value={guestDetailAddress}
            onChange={(e) => setGuestDetailAddress(e.target.value)}
            placeholder="상세주소 입력"
            className="w-full rounded-xl border bg-gray-50 px-4 py-3 text-sm"
          />
          {guestOrderError && (
            <div className="rounded-xl bg-red-50 p-4 text-xs font-bold text-red-500">
              ⚠️ {guestOrderError}
            </div>
          )}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setIsGuestOrderFormOpen(false)}
              className="flex-1 rounded-xl border py-3.5 text-sm font-bold"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={guestOrderLoading}
              className="flex-1 rounded-xl bg-blue-600 py-3.5 text-sm font-bold text-white disabled:opacity-50"
            >
              {guestOrderLoading
                ? '주문 처리 중...'
                : checkoutMode === 'member-address'
                  ? '배송지 저장 후 결제'
                  : '비회원 주문하기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
