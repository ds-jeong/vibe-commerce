import React from 'react';

export default function Pagination({
  currentPage,
  totalPages,
  handlePreviousPage,
  handleNextPage,
  setCurrentPage,
}) {
  if (!totalPages || totalPages <= 1) {
    return null;
  }

  const pages = Array.from({ length: totalPages }, (_, i) => i);
  const windowSize = 7;
  const start = Math.max(0, Math.min(currentPage - 3, totalPages - windowSize));
  const visible = pages.slice(start, start + windowSize);

  return (
    <div className="mt-10 flex flex-wrap items-center justify-center gap-2">
      <button
        type="button"
        onClick={handlePreviousPage}
        disabled={currentPage === 0}
        className="rounded-md border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-600 transition hover:scale-[1.01] hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
      >
        이전
      </button>
      {visible.map((page) => (
        <button
          key={page}
          type="button"
          onClick={() => setCurrentPage(page)}
          className={`rounded-md px-3 py-2 text-xs font-semibold transition hover:scale-[1.01] ${
            page === currentPage
              ? 'bg-[#0A192F] text-white'
              : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
          }`}
        >
          {page + 1}
        </button>
      ))}
      <button
        type="button"
        onClick={handleNextPage}
        disabled={currentPage >= totalPages - 1}
        className="rounded-md border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-600 transition hover:scale-[1.01] hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
      >
        다음
      </button>
    </div>
  );
}
