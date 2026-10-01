import React from 'react';
import {
  ORDER_STATUS_LABEL,
  canRequestReturn,
  canUserCancel,
  isPreparingOrLater,
  productImageSrc,
} from '../../utils/validation';

export default function OrderHistory({
  orders,
  formatDate,
  formatPrice,
  onCancel,
  onReturn,
}) {
  const rows = Array.isArray(orders) ? orders : [];

  return (
    <div className="space-y-4">
      {rows.length === 0 ? (
        <p className="py-16 text-center text-sm text-gray-400">
          주문 내역이 없습니다.
        </p>
      ) : (
        rows.map((order) => {
          const status = order?.status || 'ORDERED';
          const items = Array.isArray(order?.orderItems)
            ? order.orderItems
            : [];
          return (
            <div
              key={order?.id || order?.orderMerchantUid}
              className="rounded-xl border border-gray-100 p-4"
            >
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-xs text-gray-400">
                    {formatDate(order?.orderDate)}
                  </p>
                  <p className="font-bold text-gray-800">
                    {order?.orderMerchantUid}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs font-bold text-blue-600">
                    {ORDER_STATUS_LABEL[status] || status}
                  </p>
                  <p className="text-sm font-extrabold text-gray-800">
                    {formatPrice(order?.netAmount)}원
                  </p>
                  {order?.trackingNumber ? (
                    <p className="mt-1 text-[11px] text-gray-400">
                      운송장 {order.trackingNumber}
                    </p>
                  ) : null}
                </div>
              </div>

              <div className="space-y-2">
                {items.length === 0 ? (
                  <p className="text-xs text-gray-400">상품 정보 없음</p>
                ) : (
                  items.map((item, index) => (
                    <div
                      key={item?.id || index}
                      className="flex items-center gap-3"
                    >
                      <img
                        src={productImageSrc(item?.product)}
                        alt={item?.product?.name || '상품'}
                        className="h-14 w-14 rounded-lg object-cover bg-gray-100"
                        onError={(e) => {
                          e.currentTarget.src =
                            '/images/default-product.svg';
                        }}
                      />
                      <div className="min-w-0">
                        <p className="truncate text-sm font-bold text-gray-800">
                          {item?.product?.name || '상품'}
                        </p>
                        <p className="text-xs text-gray-400">
                          {item?.count || 0}개
                        </p>
                      </div>
                    </div>
                  ))
                )}
              </div>

              <div className="mt-3 flex gap-2">
                {canUserCancel(status) ? (
                  <button
                    type="button"
                    onClick={() => onCancel(order.id)}
                    className="rounded-lg bg-red-50 px-3 py-2 text-xs font-bold text-red-500"
                  >
                    즉시 주문취소
                  </button>
                ) : null}
                {isPreparingOrLater(status) && !canRequestReturn(status) ? (
                  <button
                    type="button"
                    disabled
                    className="rounded-lg bg-gray-100 px-3 py-2 text-xs font-bold text-gray-400"
                  >
                    취소 불가
                  </button>
                ) : null}
                {canRequestReturn(status) ? (
                  <button
                    type="button"
                    onClick={() => onReturn(order.id)}
                    className="rounded-lg bg-amber-50 px-3 py-2 text-xs font-bold text-amber-600"
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
