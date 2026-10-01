import React, { memo } from 'react';
import { resolveImageUrl } from '../../utils/media';

function AdminProductRow({ product, onEdit, onDelete }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border p-3">
      <div className="flex min-w-0 items-center gap-3">
        <img
          src={resolveImageUrl(product.imageUrl)}
          alt={product.name}
          className="h-12 w-12 rounded-lg bg-gray-100 object-cover"
          onError={(e) => {
            e.currentTarget.src = '/images/default-product.svg';
          }}
        />
        <p className="truncate text-sm font-bold">
          {product.name} / ₩{Number(product.price || 0).toLocaleString()}
        </p>
      </div>
      <div className="flex gap-2">
        <button
          type="button"
          className="rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50"
          onClick={() => onEdit(product)}
        >
          수정
        </button>
        <button
          type="button"
          className="rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50"
          onClick={() => onDelete(product.id)}
        >
          삭제
        </button>
      </div>
    </div>
  );
}

export default memo(AdminProductRow);
