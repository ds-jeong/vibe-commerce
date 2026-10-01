import React from 'react';
import { INQUIRY_STATUS_LABEL } from '../../utils/validation';

export default function InquiryPanel({
  inquiries,
  showInquiryForm,
  setShowInquiryForm,
  inquiryTitle,
  setInquiryTitle,
  inquiryContent,
  setInquiryContent,
  loading,
  onSubmit,
  formatDate,
}) {
  const rows = Array.isArray(inquiries) ? inquiries : [];

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-extrabold text-gray-800">1:1 문의</h3>
        <button
          type="button"
          onClick={() => setShowInquiryForm((prev) => !prev)}
          className="rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white"
        >
          1:1 문의하기
        </button>
      </div>

      {showInquiryForm && (
        <form onSubmit={onSubmit} className="mb-6 space-y-3 rounded-xl bg-gray-50 p-4">
          <input
            required
            value={inquiryTitle}
            onChange={(e) => setInquiryTitle(e.target.value)}
            placeholder="제목"
            className="w-full rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm"
          />
          <textarea
            required
            rows={5}
            value={inquiryContent}
            onChange={(e) => setInquiryContent(e.target.value)}
            placeholder="문의 내용"
            className="w-full rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm"
          />
          <button
            type="submit"
            disabled={loading}
            className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white"
          >
            등록
          </button>
        </form>
      )}

      <div className="space-y-3">
        {rows.length === 0 ? (
          <p className="py-10 text-center text-sm text-gray-400">
            등록된 문의가 없습니다.
          </p>
        ) : (
          rows.map((inquiry) => (
            <div
              key={inquiry?.id}
              className="rounded-xl border border-gray-100 p-4"
            >
              <div className="flex items-center justify-between gap-3">
                <p className="font-bold text-gray-800">{inquiry?.title}</p>
                <span className="rounded-full bg-gray-100 px-3 py-1 text-[11px] font-bold text-gray-600">
                  {INQUIRY_STATUS_LABEL[inquiry?.status] || inquiry?.status}
                </span>
              </div>
              <p className="mt-1 text-xs text-gray-400">
                {formatDate(inquiry?.createdAt)}
              </p>
              <p className="mt-2 text-sm text-gray-600">{inquiry?.content}</p>
              {inquiry?.answer ? (
                <div className="mt-3 rounded-lg bg-blue-50 p-3 text-sm text-blue-800">
                  <p className="text-xs font-bold">관리자 답변</p>
                  <p className="mt-1">{inquiry.answer}</p>
                </div>
              ) : null}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
