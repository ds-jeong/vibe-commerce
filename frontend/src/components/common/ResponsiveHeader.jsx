import React from 'react';

export default function ResponsiveHeader({
  userToken,
  handleLogout,
  cartItemCount,
  setIsCartModalOpen,
  searchKeyword,
  setSearchKeyword,
  handleSearchProducts,
}) {
  return (
    <header className="sticky top-0 z-40 border-b border-gray-200 bg-white/95 backdrop-blur transition-shadow duration-300">
      <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div
          className="cursor-pointer"
          onClick={() => {
            window.location.href = '/';
          }}
        >
          <h1 className="text-lg font-extrabold tracking-tight text-gray-800 sm:text-xl">
            🛍️ VibeCommerce
          </h1>
          <p className="mt-0.5 text-[10px] text-gray-400">
            VibeCommerce Shopping
          </p>
        </div>

        <form
          onSubmit={handleSearchProducts}
          className="flex w-full max-w-md items-center gap-2"
        >
          <input
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="상품명 검색 (니트, 셔츠...)"
            className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:bg-white"
          />
          <button
            type="submit"
            className="whitespace-nowrap rounded-xl bg-blue-600 px-4 py-2.5 text-xs font-bold text-white transition hover:bg-blue-700"
          >
            검색
          </button>
        </form>

        <div className="flex flex-wrap items-center gap-2">
          {userToken ? (
            <>
              <button
                type="button"
                onClick={handleLogout}
                className="rounded-xl px-3 py-2 text-xs font-bold text-gray-500 transition hover:bg-gray-100 hover:text-red-500"
              >
                로그아웃
              </button>
              <button
                type="button"
                onClick={() => {
                  window.location.href = '/mypage';
                }}
                className="rounded-xl px-3 py-2 text-xs font-bold text-gray-600 transition hover:bg-blue-50 hover:text-blue-600"
              >
                👤 마이페이지
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  window.location.href = '/login';
                }}
                className="rounded-xl px-3 py-2 text-xs font-bold text-gray-500 transition hover:bg-gray-100 hover:text-blue-600"
              >
                로그인
              </button>
              <button
                type="button"
                onClick={() => {
                  window.location.href = '/login?tab=guest';
                }}
                className="rounded-xl px-3 py-2 text-xs font-bold text-blue-600 transition hover:bg-blue-50"
              >
                비회원 주문조회
              </button>
            </>
          )}

          <button
            type="button"
            onClick={() => setIsCartModalOpen(true)}
            className="relative rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-bold text-gray-700 shadow-sm transition hover:border-blue-300 hover:bg-blue-50"
          >
            🛒 장바구니
            {cartItemCount > 0 && (
              <span className="absolute -right-2 -top-2 flex h-6 min-w-6 items-center justify-center rounded-full bg-red-500 px-1.5 text-[11px] font-extrabold text-white shadow">
                {cartItemCount}
              </span>
            )}
          </button>
        </div>
      </div>
    </header>
  );
}
