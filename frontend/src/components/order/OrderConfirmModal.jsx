import React from 'react';
import { computeCheckoutSummary, resolveItemImage } from '../../utils/checkout';
import { resolveImageUrl } from '../../utils/media';

export default function OrderConfirmModal({
  open,
  items,
  formatPrice,
  loading,
  error,
  onBack,
  onConfirmPay,
}) {
  if (!open) {
    return null;
  }

  const rows = Array.isArray(items) ? items : [];
  const summary = computeCheckoutSummary(rows);

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 px-4 py-8">
      <div className="order-confirm-panel w-full max-w-lg rounded-md border border-slate-100 bg-white p-6 shadow-sm">
        <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-400">
          Order Review
        </p>
        <h3 className="mt-1 text-xl font-semibold tracking-tight text-black">
          최종 주문서 확인
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          결제 전 주문 내용을 확인해 주세요. 확인 후에만 결제가 진행됩니다.
        </p>

        <div className="mt-5 max-h-[40vh] space-y-3 overflow-y-auto">
          {rows.map((item) => (
            <div
              key={`${item.id}-${item.name}`}
              className="flex items-center gap-3 rounded-md border border-slate-100 bg-slate-50 p-3"
            >
              <img
                src={resolveImageUrl(resolveItemImage(item))}
                alt={item.name}
                className="h-16 w-16 rounded-md object-cover"
                onError={(e) => {
                  e.currentTarget.src = '/images/default-product.svg';
                }}
              />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-black">{item.name}</p>
                <p className="mt-1 text-xs text-slate-500">
                  {formatPrice(item.price)}원 · {Number(item.quantity || 1)}개
                </p>
              </div>
              <p className="shrink-0 text-sm font-semibold text-black">
                {formatPrice(Number(item.price || 0) * Number(item.quantity || 1))}원
              </p>
            </div>
          ))}
        </div>

        <div className="mt-5 space-y-2 border-t border-slate-100 pt-4 text-sm">
          <div className="flex justify-between text-slate-600">
            <span>상품 금액</span>
            <span>{formatPrice(summary.goodsAmount)}원</span>
          </div>
          <div className="flex justify-between text-slate-600">
            <span>배송비</span>
            <span>
              {summary.deliveryFee === 0
                ? '무료'
                : `${formatPrice(summary.deliveryFee)}원`}
            </span>
          </div>
          <div className="flex justify-between pt-1 text-base font-semibold text-black">
            <span>최종 결제 금액</span>
            <span>{formatPrice(summary.payableAmount)}원</span>
          </div>
          {summary.deliveryFee > 0 ? (
            <p className="text-[11px] text-slate-400">
              5만원 미만 주문은 배송비 3,000원이 포함됩니다.
            </p>
          ) : null}
        </div>

        {error ? (
          <p className="mt-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-600">{error}</p>
        ) : null}

        <div className="mt-6 flex gap-2">
          <button
            type="button"
            onClick={onBack}
            disabled={loading}
            className="flex-1 rounded-md border border-slate-200 py-3 text-sm font-semibold text-slate-600 transition hover:scale-[1.01] disabled:opacity-50"
          >
            이전
          </button>
          <button
            type="button"
            onClick={onConfirmPay}
            disabled={loading || rows.length === 0}
            className="flex-1 rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:opacity-50"
          >
            {loading ? '결제 진행 중...' : '최종 결제하기'}
          </button>
        </div>
      </div>
    </div>
  );
}
