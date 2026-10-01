import React, { useCallback, useEffect, useState } from 'react';
import Pagination from '../common/Pagination';
import { adminAuthFail, adminHeaders, unwrapPage } from '../../utils/adminApi';
import { resolveImageUrl } from '../../utils/media';

const PAGE_SIZE = 8;

export default function AdminProductSalesPanel() {
  const [rows, setRows] = useState([]);
  const [sort, setSort] = useState('amount');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = useCallback(async () => {
    const res = await fetch(
      `/api/admin/analytics/product-sales?page=${currentPage}&size=${PAGE_SIZE}&sort=${sort}`,
      { headers: adminHeaders() }
    );
    if (adminAuthFail(res)) {
      return;
    }
    const data = await res.json().catch(() => ({}));
    const parsed = unwrapPage(data);
    setRows(parsed.rows);
    setTotalPages(parsed.totalPages);
  }, [currentPage, sort]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <section className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-bold text-gray-800">상품별 판매 통계</h2>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => { setSort('quantity'); setCurrentPage(0); }}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold ${
              sort === 'quantity' ? 'bg-[#0A192F] text-white' : 'border bg-white text-slate-600'
            }`}
          >
            판매량순
          </button>
          <button
            type="button"
            onClick={() => { setSort('amount'); setCurrentPage(0); }}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold ${
              sort === 'amount' ? 'bg-[#0A192F] text-white' : 'border bg-white text-slate-600'
            }`}
          >
            매출순
          </button>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b text-xs uppercase tracking-wide text-gray-400">
            <tr>
              <th className="px-2 py-2 font-bold">상품</th>
              <th className="px-2 py-2 font-bold">상품명</th>
              <th className="px-2 py-2 font-bold">총 판매량</th>
              <th className="px-2 py-2 font-bold">누적 매출액</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-2 py-8 text-center text-gray-400">판매 데이터가 없습니다.</td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={row.id || row.name} className="border-b border-gray-50">
                  <td className="px-2 py-2">
                    <img
                      src={resolveImageUrl(row.imageUrl)}
                      alt={row.name || '상품'}
                      className="h-12 w-12 rounded-lg object-cover"
                    />
                  </td>
                  <td className="px-2 py-2 font-semibold text-gray-800">{row.name}</td>
                  <td className="px-2 py-2">{Number(row.quantity || 0).toLocaleString()}개</td>
                  <td className="px-2 py-2 font-semibold text-slate-800">
                    {Number(row.amount || 0).toLocaleString()}원
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        handlePreviousPage={() => setCurrentPage((p) => Math.max(0, p - 1))}
        handleNextPage={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
        setCurrentPage={setCurrentPage}
      />
    </section>
  );
}
