import React, { useMemo } from 'react';
import {
  ORDER_STATUS_LABEL,
  canRequestReturn,
  canTrackOrder,
  canUserCancel,
  isPreparingOrLater,
} from '../../utils/validation';
import { trackingUrl } from '../../utils/media';
import OrderLineItems from '../order/OrderLineItems';

export default function OrderHistory({
  orders,
  formatDate,
  formatPrice,
  onCancel,
  onReturn,
  statusFilter,
}) {
  const rows = useMemo(() => {
    const allRows = Array.isArray(orders) ? orders : [];
    if (!statusFilter || statusFilter === 'ALL') {
      return allRows;
    }
    return allRows.filter((order) => (order?.status || 'ORDERED') === statusFilter);
  }, [orders, statusFilter]);

  const openTracking = (order) => {
    const href = trackingUrl(order?.trackingNumber);
    if (!href) {
      alert('운송장 번호가 아직 등록되지 않았습니다.');
      return;
    }
    window.open(href, '_blank', 'noopener,noreferrer');
  };

  return (
    <div className="space-y-4">
      {rows.length === 0 ? (
        <p className="py-16 text-center text-sm text-gray-400">주문 내역이 없습니다.</p>
      ) : (
        rows.map((order) => {
          const status = order?.status || 'ORDERED';
          const items = Array.isArray(order?.orderItems) ? order.orderItems : [];
          const canTrack = canTrackOrder(status);
          return (
            <div
              key={order?.id || order?.orderMerchantUid}
              className="rounded-xl border border-gray-100 p-4"
            >
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-xs text-gray-400">{formatDate(order?.orderDate)}</p>
                  <p className="font-bold text-gray-800">{order?.orderMerchantUid}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs font-bold text-blue-600">
                    {ORDER_STATUS_LABEL[status] || status}
                  </p>
                  <p className="text-sm font-extrabold text-gray-800">
                    {formatPrice(order?.netAmount)}원
                  </p>
                </div>
              </div>

              <OrderLineItems items={items} formatPrice={formatPrice} />

              <div className="mt-3 flex flex-wrap gap-2">
                {canTrack ? (
                  <button
                    type="button"
                    onClick={() => openTracking(order)}
                    className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
                  >
                    배송조회
                  </button>
                ) : null}
                {canUserCancel(status) ? (
                  <button
                    type="button"
                    onClick={() => onCancel(order.id)}
                    className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50"
                  >
                    주문 취소
                  </button>
                ) : null}
                {isPreparingOrLater(status) && !canRequestReturn(status) && !canTrack ? (
                  <button
                    type="button"
                    disabled
                    className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-400"
                  >
                    취소 불가
                  </button>
                ) : null}
                {canRequestReturn(status) ? (
                  <button
                    type="button"
                    onClick={() => onReturn(order.id)}
                    className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50"
                  >
                    반품 신청
                  </button>
                ) : null}
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}
