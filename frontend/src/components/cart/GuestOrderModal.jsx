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
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/40 px-4 py-8">
      <div className="w-full max-w-lg rounded-md bg-white p-7 shadow-sm">
        <div className="mb-6 flex items-start justify-between">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-400">
              {checkoutMode === 'member-address' ? 'Shipping' : 'Guest Checkout'}
            </p>
            <h3 className="mt-1 text-xl font-semibold text-black">
              {checkoutMode === 'member-address' ? '배송지 입력' : '주문자 정보'}
            </h3>
          </div>
          <button
            type="button"
            onClick={() => setIsGuestOrderFormOpen(false)}
            className="rounded-md px-3 py-2 text-slate-400"
          >
            ✕
          </button>
        </div>

        {(checkoutItems[0] || selectedProduct) && (
          <div className="mb-5 rounded-md bg-slate-50 p-4">
            <p className="text-sm font-semibold text-black">
              {buildOrderName(
                checkoutItems.length > 0
                  ? checkoutItems
                  : [toCheckoutItem(selectedProduct)]
              )}
            </p>
            <p className="mt-1 text-base font-semibold text-black">
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
            className="w-full rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm"
          />
          {guestNameMessage && (
            <p
              className={`text-xs font-semibold ${
                guestNameRegex.test(guestName) ? 'text-emerald-600' : 'text-red-500'
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
            className="w-full rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm"
          />
          {guestPhoneMessage && (
            <p
              className={`text-xs font-semibold ${
                guestPhoneRegex.test(guestPhone)
                  ? 'text-emerald-600'
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
              className="flex-1 rounded-md border bg-slate-100 px-4 py-3 text-sm"
            />
            <button
              type="button"
              onClick={handleAddressSearch}
              className="rounded-md bg-[#1E293B] px-4 py-3 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01]"
            >
              주소 검색
            </button>
          </div>
          <input
            value={guestRoadAddress}
            readOnly
            placeholder="도로명 주소"
            className="w-full rounded-md border bg-slate-100 px-4 py-3 text-sm"
          />
          <input
            id="guest-detail-address"
            value={guestDetailAddress}
            onChange={(e) => setGuestDetailAddress(e.target.value)}
            placeholder="상세주소"
            className="w-full rounded-md border bg-slate-50 px-4 py-3 text-sm"
          />
          {guestOrderError && (
            <div className="rounded-md bg-red-50 p-4 text-xs font-semibold text-red-500">
              {guestOrderError}
            </div>
          )}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setIsGuestOrderFormOpen(false)}
              className="flex-1 rounded-md border border-slate-200 py-3.5 text-sm font-semibold"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={guestOrderLoading}
              className="flex-1 rounded-md bg-[#0A192F] py-3.5 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:opacity-50"
            >
              {guestOrderLoading
                ? '처리 중...'
                : checkoutMode === 'member-address'
                  ? '배송지 저장'
                  : '주문서 확인'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
