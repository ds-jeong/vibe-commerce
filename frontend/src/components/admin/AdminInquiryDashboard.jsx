import React, { memo, useCallback, useEffect, useState } from 'react';
import { INQUIRY_STATUS_LABEL } from '../../utils/validation';
import Pagination from '../common/Pagination';
import { adminAuthFail, adminHeaders, unwrapPage } from '../../utils/adminApi';

const PAGE_SIZE = 8;

const InquiryItem = memo(function InquiryItem({
  inquiry,
  draft,
  onDraftChange,
  onAnswer,
  onDeleteAnswer,
}) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <p className="text-xs text-gray-400">{inquiry.userKey}</p>
      <p className="font-bold">{inquiry.title}</p>
      <p className="mt-1 text-sm text-gray-600">{inquiry.content}</p>
      <p className="mt-1 text-xs">{INQUIRY_STATUS_LABEL[inquiry.status] || inquiry.status}</p>
      {inquiry.answer ? (
        <p className="mt-2 text-sm text-blue-700">답변: {inquiry.answer}</p>
      ) : null}
      <div className="mt-2 flex flex-wrap gap-2">
        <input
          value={draft}
          onChange={(e) => onDraftChange(inquiry.id, e.target.value)}
          placeholder="답변 입력"
          className="min-w-[200px] flex-1 rounded-lg border px-3 py-2 text-sm"
        />
        <button
          type="button"
          className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-bold text-white"
          onClick={() => onAnswer(inquiry.id)}
        >
          {inquiry.answer ? '답변 수정' : '답변 등록'}
        </button>
        {inquiry.answer ? (
          <button
            type="button"
            className="rounded-lg border px-3 py-2 text-xs font-bold text-gray-500"
            onClick={() => onDeleteAnswer(inquiry.id)}
          >
            답변 삭제
          </button>
        ) : null}
      </div>
    </div>
  );
});

export default function AdminInquiryDashboard() {
  const [inquiries, setInquiries] = useState([]);
  const [answerDraft, setAnswerDraft] = useState({});
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const loadInquiries = useCallback(async () => {
    const res = await fetch(
      `/api/admin/inquiries?page=${currentPage}&size=${PAGE_SIZE}`,
      { headers: adminHeaders() }
    );
    if (adminAuthFail(res)) {
      return;
    }
    const data = await res.json().catch(() => []);
    const parsed = unwrapPage(data);
    setInquiries(parsed.rows);
    setTotalPages(parsed.totalPages);
  }, [currentPage]);

  useEffect(() => {
    loadInquiries();
  }, [loadInquiries]);

  const onDraftChange = useCallback((id, value) => {
    setAnswerDraft((prev) => ({ ...prev, [id]: value }));
  }, []);

  const answerInquiry = useCallback(
    async (id) => {
      const answer = (answerDraft[id] || '').trim();
      const res = await fetch(`/api/admin/inquiries/${id}/answer`, {
        method: 'PUT',
        headers: adminHeaders(),
        body: JSON.stringify({ answer }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        alert(data.message || '답변 등록에 실패했습니다.');
        return;
      }
      setAnswerDraft((prev) => ({ ...prev, [id]: '' }));
      await loadInquiries();
    },
    [answerDraft, loadInquiries]
  );

  const deleteAnswer = useCallback(
    async (id) => {
      if (!window.confirm('답변을 삭제하고 대기 상태로 되돌릴까요?')) {
        return;
      }
      const res = await fetch(`/api/admin/inquiries/${id}/answer`, {
        method: 'DELETE',
        headers: adminHeaders(),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        alert(data.message || '답변 삭제에 실패했습니다.');
        return;
      }
      setAnswerDraft((prev) => ({ ...prev, [id]: '' }));
      await loadInquiries();
    },
    [loadInquiries]
  );

  return (
    <div className="space-y-3">
      {inquiries.map((inquiry) => (
        <InquiryItem
          key={inquiry.id}
          inquiry={inquiry}
          draft={answerDraft[inquiry.id] ?? inquiry.answer ?? ''}
          onDraftChange={onDraftChange}
          onAnswer={answerInquiry}
          onDeleteAnswer={deleteAnswer}
        />
      ))}
      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        handlePreviousPage={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
        handleNextPage={() =>
          setCurrentPage((prev) =>
            totalPages > 0 ? Math.min(totalPages - 1, prev + 1) : prev
          )
        }
        setCurrentPage={setCurrentPage}
      />
    </div>
  );
}
