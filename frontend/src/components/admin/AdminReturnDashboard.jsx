import React, { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { ORDER_STATUS_LABEL } from '../../utils/validation';
import Pagination from '../common/Pagination';
import OrderLineItems from '../order/OrderLineItems';
import { adminAuthFail, adminHeaders, unwrapPage } from '../../utils/adminApi';

const PAGE_SIZE = 8;

const RETURN_STATUS_FILTERS = [
  { id: 'ALL', label: '전체' },
  { id: 'REQUESTED', label: '요청됨' },
  { id: 'APPROVED', label: '승인됨' },
  { id: 'RECEIVED', label: '회수됨' },
  { id: 'REJECTED', label: '반려됨' },
  { id: 'COMPLETED', label: '완료됨' },
];

const RETURN_STATUS_LABEL = {
  REQUESTED: '반품 요청',
  APPROVED: '승인됨',
  RECEIVED: '회수 완료',
  REJECTED: '반려됨',
  COMPLETED: '처리 완료',
};

const MOCK_COMPLETE_CONFIRM =
  '모의 환불을 완료 처리할까요? 이 작업은 실제 PG 환불을 요청하지 않으며, 백엔드의 모의 환불 처리 및 재고 복구를 실행합니다.';

function extractErrorMessage(body, fallback) {
  if (typeof body === 'string' && body.trim()) {
    return body;
  }
  if (body && typeof body === 'object') {
    if (typeof body.message === 'string' && body.message.trim()) {
      return body.message;
    }
    if (typeof body.error === 'string' && body.error.trim()) {
      return body.error;
    }
  }
  return fallback;
}

function formatDateTime(value) {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString('ko-KR');
}

function normalizeReturnStatus(row) {
  const raw = row?.returnStatus;
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim().toUpperCase();
  }
  return 'REQUESTED';
}

function isLegacyRow(row) {
  return row?.source === 'LEGACY_ORDER' || row?.legacyIncomplete === true;
}

function rowKey(row, index) {
  if (row?.returnRequestId != null) {
    return `rr-${row.returnRequestId}`;
  }
  if (row?.id != null) {
    return `order-${row.id}-${row?.source || 'unknown'}`;
  }
  return `row-${index}`;
}

function actionMessageForStatus(httpStatus, body) {
  if (httpStatus === 400) {
    return extractErrorMessage(body, '요청 내용을 확인해 주세요.');
  }
  if (httpStatus === 404) {
    return extractErrorMessage(body, '대상 주문을 찾을 수 없습니다.');
  }
  if (httpStatus === 409) {
    return extractErrorMessage(
      body,
      '상태가 이미 변경되었거나 중복 처리되었을 수 있습니다. 목록을 다시 불러옵니다.'
    );
  }
  if (httpStatus >= 500) {
    return extractErrorMessage(
      body,
      '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
    );
  }
  return extractErrorMessage(body, '요청을 처리하지 못했습니다.');
}

const ReturnRow = memo(function ReturnRow({
  row,
  busy,
  rejectOpen,
  rejectDraft,
  onRejectDraftChange,
  onApprove,
  onOpenReject,
  onCancelReject,
  onConfirmReject,
  onReceive,
  onMockComplete,
}) {
  const returnStatus = normalizeReturnStatus(row);
  const orderStatus = row?.status;
  const orderDateLabel = formatDateTime(row?.orderDate);
  const reason = typeof row?.reason === 'string' ? row.reason.trim() : '';
  const legacy = isLegacyRow(row);

  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-bold">{row?.orderMerchantUid || '주문번호 없음'}</p>
          <p className="text-xs text-gray-400">주문 ID {row?.id ?? '-'}</p>
          {row?.returnRequestId != null ? (
            <p className="text-xs text-gray-400">반품 요청 ID {row.returnRequestId}</p>
          ) : null}
          {orderDateLabel ? (
            <p className="text-xs text-gray-400">주문일 {orderDateLabel}</p>
          ) : null}
          <p className="mt-1 text-xs font-semibold text-slate-700">
            {RETURN_STATUS_LABEL[returnStatus] || returnStatus}
          </p>
          <p className="text-xs text-gray-400">
            주문 상태 {ORDER_STATUS_LABEL[orderStatus] || orderStatus || '-'}
          </p>
        </div>
        <p className="font-extrabold text-blue-600">
          ₩{Number(row?.netAmount || row?.totalAmount || 0).toLocaleString()}
        </p>
      </div>

      {legacy ? (
        <p className="mt-2 rounded-lg bg-amber-50 px-2 py-1 text-xs font-bold text-amber-700">
          기존 반품 건 · 이력 불완전
        </p>
      ) : null}

      <div className="mt-3">
        <OrderLineItems items={row?.orderItems} />
      </div>

      {reason ? (
        <p className="mt-2 text-sm text-slate-600">
          {returnStatus === 'REJECTED' ? '반려 사유' : '사유'}: {reason}
        </p>
      ) : null}

      {returnStatus === 'REJECTED' && !reason ? (
        <p className="mt-2 text-xs text-gray-400">반려 이력 (사유 없음)</p>
      ) : null}

      {returnStatus === 'COMPLETED' ? (
        <p className="mt-3 text-xs font-semibold text-emerald-700">처리 완료</p>
      ) : null}

      {returnStatus === 'REQUESTED' ? (
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busy}
            className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:cursor-not-allowed disabled:opacity-40"
            onClick={() => onApprove(row)}
          >
            승인
          </button>
          <button
            type="button"
            disabled={busy}
            className="rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            onClick={() => onOpenReject(row)}
          >
            반려
          </button>
        </div>
      ) : null}

      {returnStatus === 'APPROVED' ? (
        <div className="mt-3">
          <button
            type="button"
            disabled={busy}
            className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:cursor-not-allowed disabled:opacity-40"
            onClick={() => onReceive(row)}
          >
            회수 완료
          </button>
        </div>
      ) : null}

      {returnStatus === 'RECEIVED' ? (
        <div className="mt-3">
          <button
            type="button"
            disabled={busy}
            className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:cursor-not-allowed disabled:opacity-40"
            onClick={() => onMockComplete(row)}
          >
            모의 환불 완료
          </button>
        </div>
      ) : null}

      {rejectOpen ? (
        <div className="mt-3 space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-semibold text-slate-700">반려 사유</p>
          <textarea
            value={rejectDraft}
            onChange={(e) => onRejectDraftChange(e.target.value)}
            rows={3}
            disabled={busy}
            placeholder="반려 사유를 입력하세요 (선택)"
            className="w-full rounded-lg border px-3 py-2 text-sm"
          />
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={busy}
              className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:cursor-not-allowed disabled:opacity-40"
              onClick={() => onConfirmReject(row)}
            >
              반려 확인
            </button>
            <button
              type="button"
              disabled={busy}
              className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              onClick={onCancelReject}
            >
              취소
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
});

export default function AdminReturnDashboard() {
  const [rows, setRows] = useState([]);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [toast, setToast] = useState('');
  const [busyKey, setBusyKey] = useState(null);
  const [rejectTargetKey, setRejectTargetKey] = useState(null);
  const [rejectDraft, setRejectDraft] = useState('');

  const showToast = useCallback((message) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 2800);
  }, []);

  const loadReturns = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const res = await fetch('/api/admin/returns', { headers: adminHeaders() });
      if (adminAuthFail(res)) {
        return;
      }
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        setRows([]);
        setLoadError(
          res.status === 404
            ? '반품 목록 API를 찾을 수 없습니다. 잠시 후 다시 시도해 주세요.'
            : actionMessageForStatus(res.status, body)
        );
        return;
      }
      const data = await res.json().catch(() => []);
      const parsed = unwrapPage(data);
      setRows(Array.isArray(parsed.rows) ? parsed.rows : []);
    } catch (error) {
      setRows([]);
      setLoadError('목록을 불러오지 못했습니다. 네트워크를 확인한 뒤 다시 시도해 주세요.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadReturns();
  }, [loadReturns]);

  const filteredRows = useMemo(() => {
    const list = Array.isArray(rows) ? rows : [];
    if (statusFilter === 'ALL') {
      return list;
    }
    return list.filter((row) => normalizeReturnStatus(row) === statusFilter);
  }, [rows, statusFilter]);

  const totalPages = Math.ceil(filteredRows.length / PAGE_SIZE);
  const pageRows = filteredRows.slice(
    currentPage * PAGE_SIZE,
    currentPage * PAGE_SIZE + PAGE_SIZE
  );

  useEffect(() => {
    if (currentPage > 0 && currentPage >= totalPages) {
      setCurrentPage(Math.max(0, totalPages - 1));
    }
  }, [currentPage, totalPages]);

  const runAction = useCallback(
    async (row, path, options = {}) => {
      const orderId = row?.id;
      if (!orderId || busyKey) {
        return;
      }
      const key = rowKey(row, 0);
      setBusyKey(key);
      try {
        const res = await fetch(`/api/admin/orders/${orderId}/returns/${path}`, {
          method: 'PUT',
          headers: adminHeaders(),
          body: options.body,
        });
        if (adminAuthFail(res)) {
          return;
        }
        const body = await res.json().catch(() => ({}));
        if (!res.ok) {
          showToast(actionMessageForStatus(res.status, body));
          if (res.status === 409) {
            await loadReturns();
          }
          return;
        }
        showToast(options.successMessage || '처리가 완료되었습니다.');
        if (options.clearReject) {
          setRejectTargetKey(null);
          setRejectDraft('');
        }
        await loadReturns();
      } catch (error) {
        showToast('요청에 실패했습니다. 네트워크를 확인한 뒤 다시 시도해 주세요.');
      } finally {
        setBusyKey(null);
      }
    },
    [busyKey, loadReturns, showToast]
  );

  const onApprove = useCallback(
    (row) => {
      runAction(row, 'approve', { successMessage: '반품이 승인되었습니다.' });
    },
    [runAction]
  );

  const onReceive = useCallback(
    (row) => {
      runAction(row, 'receive', { successMessage: '회수 완료로 변경했습니다.' });
    },
    [runAction]
  );

  const onMockComplete = useCallback(
    (row) => {
      if (!window.confirm(MOCK_COMPLETE_CONFIRM)) {
        return;
      }
      runAction(row, 'mock-complete', {
        successMessage: '모의 환불이 완료되었습니다. 실제 PG 환불은 요청하지 않았습니다.',
      });
    },
    [runAction]
  );

  const onOpenReject = useCallback((row) => {
    const key = rowKey(row, 0);
    setRejectTargetKey((prev) => {
      if (prev !== key) {
        setRejectDraft('');
      }
      return key;
    });
  }, []);

  const onCancelReject = useCallback(() => {
    setRejectTargetKey(null);
  }, []);

  const onConfirmReject = useCallback(
    (row) => {
      const trimmed = rejectDraft.trim();
      const payload = { reason: trimmed };
      runAction(row, 'reject', {
        body: JSON.stringify(payload),
        successMessage: '반품이 반려되었습니다.',
        clearReject: true,
      });
    },
    [rejectDraft, runAction]
  );

  return (
    <div className="space-y-4">
      {toast ? (
        <div className="rounded-xl border border-amber-100 bg-amber-50 p-3 text-xs font-bold text-amber-700">
          {toast}
        </div>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {RETURN_STATUS_FILTERS.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => {
              setStatusFilter(item.id);
              setCurrentPage(0);
            }}
            className={`rounded-md px-4 py-2 text-xs font-semibold shadow-sm transition hover:scale-[1.01] ${
              statusFilter === item.id
                ? 'bg-[#0A192F] text-white'
                : 'border border-slate-200 bg-white text-slate-700'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {loading ? (
        <p className="text-sm text-slate-500">반품 목록을 불러오는 중입니다.</p>
      ) : null}

      {!loading && loadError ? (
        <div className="space-y-2 rounded-xl border border-red-100 bg-red-50 p-4">
          <p className="text-sm font-semibold text-red-700">{loadError}</p>
          <button
            type="button"
            className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white"
            onClick={loadReturns}
          >
            다시 시도
          </button>
        </div>
      ) : null}

      {!loading && !loadError && filteredRows.length === 0 ? (
        <p className="text-sm text-slate-500">표시할 반품 요청이 없습니다.</p>
      ) : null}

      <div className="space-y-3">
        {pageRows.map((row, index) => {
          const key = rowKey(row, currentPage * PAGE_SIZE + index);
          return (
            <ReturnRow
              key={key}
              row={row}
              busy={busyKey != null}
              rejectOpen={rejectTargetKey === key}
              rejectDraft={rejectDraft}
              onRejectDraftChange={setRejectDraft}
              onApprove={onApprove}
              onOpenReject={onOpenReject}
              onCancelReject={onCancelReject}
              onConfirmReject={onConfirmReject}
              onReceive={onReceive}
              onMockComplete={onMockComplete}
            />
          );
        })}
      </div>

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
