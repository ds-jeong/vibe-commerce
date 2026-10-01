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
        className="rounded-xl border border-gray-200 bg-white px-4 py-2 text-xs font-bold text-gray-600 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
      >
        ← 이전
      </button>
      {visible.map((page) => (
        <button
          key={page}
          type="button"
          onClick={() => setCurrentPage(page)}
          className={`rounded-xl px-3 py-2 text-xs font-bold transition ${
            page === currentPage
              ? 'bg-blue-600 text-white'
              : 'border border-gray-200 bg-white text-gray-600 hover:bg-gray-100'
          }`}
        >
          {page + 1}
        </button>
      ))}
      <button
        type="button"
        onClick={handleNextPage}
        disabled={currentPage >= totalPages - 1}
        className="rounded-xl border border-gray-200 bg-white px-4 py-2 text-xs font-bold text-gray-600 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
      >
        다음 →
      </button>
    </div>
  );
}
