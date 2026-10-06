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
    <header className="sticky top-0 z-40 border-b border-slate-100 bg-white/95 backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div
          className="cursor-pointer"
          onClick={() => {
            window.location.href = '/';
          }}
        >
          <h1 className="text-lg font-semibold tracking-tight text-black sm:text-xl">
            VibeCommerce
          </h1>
          <p className="mt-0.5 text-[10px] uppercase tracking-[0.18em] text-slate-400">
            Minimal Store
          </p>
        </div>

        <form
          onSubmit={handleSearchProducts}
          className="flex w-full max-w-md items-center gap-2"
        >
          <input
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="상품 검색"
            className="w-full rounded-md border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none transition focus:border-[#0A192F] focus:bg-white"
          />
          <button
            type="submit"
            className="whitespace-nowrap rounded-md bg-[#0A192F] px-4 py-2.5 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
          >
            검색
          </button>
        </form>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => {
              window.location.href = '/admin/login';
            }}
            className="rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-500 transition hover:bg-slate-50 hover:text-black"
          >
            관리자
          </button>
          {userToken ? (
            <>
              <button
                type="button"
                onClick={handleLogout}
                className="rounded-md px-3 py-2 text-xs font-semibold text-slate-500 transition hover:bg-slate-50 hover:text-black"
              >
                로그아웃
              </button>
              <button
                type="button"
                onClick={() => {
                  window.location.href = '/mypage';
                }}
                className="rounded-md px-3 py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-black"
              >
                마이페이지
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  window.location.href = '/login';
                }}
                className="rounded-md px-3 py-2 text-xs font-semibold text-slate-500 transition hover:bg-slate-50 hover:text-black"
              >
                로그인
              </button>
              <button
                type="button"
                onClick={() => {
                  window.location.href = '/login?tab=guest';
                }}
                className="rounded-md px-3 py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-black"
              >
                비회원 주문조회
              </button>
            </>
          )}

          <button
            type="button"
            onClick={() => setIsCartModalOpen(true)}
            className="relative rounded-md border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-black shadow-sm transition hover:scale-[1.01] hover:bg-slate-50"
          >
            장바구니
            {cartItemCount > 0 && (
              <span className="absolute -right-2 -top-2 flex h-6 min-w-6 items-center justify-center rounded-full bg-[#0A192F] px-1.5 text-[11px] font-semibold text-white">
                {cartItemCount}
              </span>
            )}
          </button>
        </div>
      </div>
    </header>
  );
}
