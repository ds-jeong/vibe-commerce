import React, { memo, useCallback, useEffect, useState } from 'react';
import { ORDER_STATUS_LABEL } from '../../utils/validation';
import { trackingUrl } from '../../utils/media';
import Pagination from '../common/Pagination';
import OrderLineItems from '../order/OrderLineItems';
import { adminAuthFail, adminHeaders, unwrapPage } from '../../utils/adminApi';

const PAGE_SIZE = 8;

const AdminOrderRow = memo(function AdminOrderRow({
  order,
  trackingDraft,
  onTrackingChange,
  onUpdateStatus,
  subTab,
}) {
  const status = order?.status || 'ORDERED';
  const items = Array.isArray(order?.orderItems) ? order.orderItems : [];
  const trackHref = trackingUrl(order.trackingNumber);

  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-bold">{order.orderMerchantUid}</p>
          <p className="text-xs text-gray-400">{ORDER_STATUS_LABEL[status] || status}</p>
        </div>
        <p className="font-extrabold text-blue-600">
          ₩{Number(order.netAmount || 0).toLocaleString()}
        </p>
      </div>

      <div className="mt-3">
        <OrderLineItems items={items} />
      </div>

      {subTab === 'orders' && (status === 'ORDERED' || status === 'PAID') ? (
        <div className="mt-3">
          <button
            type="button"
            className="rounded-lg bg-gray-800 px-3 py-2 text-xs font-bold text-white"
            onClick={() => onUpdateStatus(order.id, 'PREPARING')}
          >
            배송 준비
          </button>
        </div>
      ) : null}

      {subTab === 'orders' && status === 'PREPARING' ? (
        <div className="mt-3 flex flex-wrap gap-2">
          <input
            value={trackingDraft[order.id] || order.trackingNumber || ''}
            onChange={(e) => onTrackingChange(order.id, e.target.value)}
            placeholder="우체국 운송장 번호"
            className="min-w-[180px] flex-1 rounded-lg border px-3 py-2 text-xs"
          />
          <button
            type="button"
            className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-bold text-white"
            onClick={() =>
              onUpdateStatus(
                order.id,
                'SHIPPING',
                trackingDraft[order.id] || order.trackingNumber
              )
            }
          >
            운송장 등록
          </button>
        </div>
      ) : null}

      {subTab === 'shipping' ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {trackHref ? (
            <a
              href={trackHref}
              target="_blank"
              rel="noreferrer"
              className="rounded-lg bg-blue-50 px-3 py-2 text-xs font-bold text-blue-600"
            >
              📦 배송조회
            </a>
          ) : null}
          {(status === 'SHIPPING' || status === 'DELIVERING') && (
            <button
              type="button"
              className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-bold text-white"
              onClick={() => onUpdateStatus(order.id, 'DELIVERED', order.trackingNumber)}
            >
              배송 완료
            </button>
          )}
        </div>
      ) : null}
    </div>
  );
});

export default function AdminOrderDashboard() {
  const [subTab, setSubTab] = useState('orders');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [orderPage, setOrderPage] = useState(0);
  const [deliveryPage, setDeliveryPage] = useState(0);
  const [orderTotalPages, setOrderTotalPages] = useState(0);
  const [deliveryTotalPages, setDeliveryTotalPages] = useState(0);
  const [orders, setOrders] = useState([]);
  const [trackingDraft, setTrackingDraft] = useState({});

  const currentPage = subTab === 'shipping' ? deliveryPage : orderPage;
  const totalPages = subTab === 'shipping' ? deliveryTotalPages : orderTotalPages;

  const loadOrders = useCallback(async () => {
    const params = new URLSearchParams({
      page: String(currentPage),
      size: String(PAGE_SIZE),
      scope: subTab === 'shipping' ? 'shipping' : 'orders',
    });
    if (appliedKeyword.trim()) {
      params.set('keyword', appliedKeyword.trim());
    }
    if (dateFrom) {
      params.set('dateFrom', dateFrom);
    }
    if (dateTo) {
      params.set('dateTo', dateTo);
    }
    const res = await fetch(`/api/admin/orders?${params.toString()}`, {
      headers: adminHeaders(),
    });
    if (adminAuthFail(res)) {
      return;
    }
    const data = await res.json().catch(() => []);
    const parsed = unwrapPage(data);
    setOrders(parsed.rows);
    if (subTab === 'shipping') {
      setDeliveryTotalPages(parsed.totalPages);
    } else {
      setOrderTotalPages(parsed.totalPages);
    }
  }, [currentPage, subTab, appliedKeyword, dateFrom, dateTo]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const updateOrderStatus = useCallback(
    async (id, status, trackingNumber) => {
      const res = await fetch(`/api/admin/orders/${id}/status`, {
        method: 'PUT',
        headers: adminHeaders(),
        body: JSON.stringify({ status, trackingNumber }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        alert(body.message || '상태 변경 실패');
        return;
      }
      if (String(status).toUpperCase() === 'SHIPPING') {
        setSubTab('shipping');
        setDeliveryPage(0);
        return;
      }
      await loadOrders();
    },
    [loadOrders]
  );

  const onTrackingChange = useCallback((id, value) => {
    setTrackingDraft((prev) => ({ ...prev, [id]: value }));
  }, []);

  const setCurrentPage = (page) => {
    if (subTab === 'shipping') {
      setDeliveryPage(page);
    } else {
      setOrderPage(page);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => setSubTab('orders')}
          className={`rounded-xl px-4 py-2 text-xs font-bold ${
            subTab === 'orders' ? 'bg-blue-600 text-white' : 'border bg-white'
          }`}
        >
          주문 관리
        </button>
        <button
          type="button"
          onClick={() => setSubTab('shipping')}
          className={`rounded-xl px-4 py-2 text-xs font-bold ${
            subTab === 'shipping' ? 'bg-blue-600 text-white' : 'border bg-white'
          }`}
        >
          배송 관리
        </button>
      </div>

      <form
        className="grid gap-2 md:grid-cols-4"
        onSubmit={(e) => {
          e.preventDefault();
          setCurrentPage(0);
          setAppliedKeyword(keyword.trim());
        }}
      >
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="주문번호 / 운송장 검색"
          className="rounded-xl border px-3 py-2 text-sm"
        />
        <input
          type="date"
          value={dateFrom}
          onChange={(e) => {
            setDateFrom(e.target.value);
            setCurrentPage(0);
          }}
          className="rounded-xl border px-3 py-2 text-sm"
        />
        <input
          type="date"
          value={dateTo}
          onChange={(e) => {
            setDateTo(e.target.value);
            setCurrentPage(0);
          }}
          className="rounded-xl border px-3 py-2 text-sm"
        />
        <button
          type="submit"
          className="rounded-xl bg-gray-800 px-3 py-2 text-xs font-bold text-white"
        >
          검색
        </button>
      </form>

      <div className="space-y-3">
        {orders.map((order) => (
          <AdminOrderRow
            key={order.id}
            order={order}
            subTab={subTab}
            trackingDraft={trackingDraft}
            onTrackingChange={onTrackingChange}
            onUpdateStatus={updateOrderStatus}
          />
        ))}
      </div>

      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        handlePreviousPage={() => setCurrentPage(Math.max(0, currentPage - 1))}
        handleNextPage={() =>
          setCurrentPage(totalPages > 0 ? Math.min(totalPages - 1, currentPage + 1) : currentPage)
        }
        setCurrentPage={setCurrentPage}
      />
    </div>
  );
}
