import React, { useCallback, useEffect, useState } from 'react';
import Pagination from '../common/Pagination';
import { INQUIRY_STATUS_LABEL } from '../../utils/validation';
import { adminAuthFail, adminHeaders, unwrapPage } from '../../utils/adminApi';

const PAGE_SIZE = 8;

export default function AdminInquiryOverview() {
  const [rows, setRows] = useState([]);
  const [status, setStatus] = useState('ALL');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = useCallback(async () => {
    const res = await fetch(
      `/api/admin/analytics/inquiries?status=${status}&page=${currentPage}&size=${PAGE_SIZE}`,
      { headers: adminHeaders() }
    );
    if (adminAuthFail(res)) {
      return;
    }
    const data = await res.json().catch(() => ({}));
    const parsed = unwrapPage(data);
    setRows(parsed.rows);
    setTotalPages(parsed.totalPages);
  }, [currentPage, status]);

  useEffect(() => {
    load();
  }, [load]);

  const filters = [
    { id: 'ALL', label: '전체' },
    { id: 'PENDING', label: '대기 (PENDING)' },
    { id: 'ANSWERED', label: '답변완료 (ANSWERED)' },
  ];

  return (
    <section className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-bold text-gray-800">문의 현황</h2>
        <div className="flex flex-wrap gap-2">
          {filters.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => { setStatus(item.id); setCurrentPage(0); }}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold shadow-sm transition hover:scale-[1.01] ${
                status === item.id ? 'bg-[#0A192F] text-white' : 'border border-slate-200 bg-white text-slate-600'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
      <div className="space-y-3">
        {rows.length === 0 ? (
          <p className="py-8 text-center text-sm text-gray-400">문의 내역이 없습니다.</p>
        ) : (
          rows.map((inquiry) => (
            <div key={inquiry.id} className="rounded-lg border border-gray-100 px-3 py-2">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-bold text-gray-800">{inquiry.title}</p>
                <span className="text-xs font-bold text-indigo-600">
                  {INQUIRY_STATUS_LABEL[inquiry.status] || inquiry.status}
                </span>
              </div>
              <p className="mt-1 text-xs text-gray-500">{inquiry.userKey}</p>
              <p className="mt-1 line-clamp-2 text-xs text-gray-600">{inquiry.content}</p>
            </div>
          ))
        )}
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
