import React, { useState } from 'react';
import { formatPriceInput, parsePriceInput } from '../../utils/media';

const emptyForm = {
  name: '',
  price: '',
  stockQuantity: '',
  imageUrl: '',
  description: '',
};

export default function ProductForm({ onSubmit, initialValue, submitLabel }) {
  const [form, setForm] = useState(() => {
    const seed = initialValue || emptyForm;
    return {
      ...emptyForm,
      ...seed,
      price: seed.price === '' || seed.price == null ? '' : formatPriceInput(seed.price),
    };
  });
  const [uploading, setUploading] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit({
      name: form.name,
      stockQuantity: Number(form.stockQuantity || 0),
      imageUrl: form.imageUrl,
      description: form.description,
      price: parsePriceInput(form.price),
    });
    if (!initialValue) {
      setForm(emptyForm);
    }
  };

  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) {
      return;
    }
    const token = localStorage.getItem('adminToken');
    const body = new FormData();
    body.append('file', file);
    setUploading(true);
    try {
      const res = await fetch('/api/admin/products/upload', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body,
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        alert(data.message || '이미지 업로드에 실패했습니다.');
        return;
      }
      setForm((prev) => ({ ...prev, imageUrl: data.imageUrl || '' }));
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  return (
    <form onSubmit={handleSubmit} className="grid gap-3 md:grid-cols-2">
      <input
        required
        value={form.name}
        onChange={(e) => setForm({ ...form, name: e.target.value })}
        placeholder="상품명"
        className="rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <input
        required
        inputMode="numeric"
        value={form.price}
        onChange={(e) =>
          setForm({ ...form, price: formatPriceInput(e.target.value) })
        }
        placeholder="가격 (원)"
        className="rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <input
        type="number"
        value={form.stockQuantity}
        onChange={(e) => setForm({ ...form, stockQuantity: e.target.value })}
        placeholder="재고"
        className="rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <div className="flex gap-2">
        <input
          value={form.imageUrl}
          onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
          placeholder="이미지 URL 또는 업로드"
          className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm"
        />
        <label className="whitespace-nowrap rounded-xl border px-3 py-3 text-xs font-bold text-gray-600">
          {uploading ? '업로드 중' : '파일'}
          <input type="file" accept="image/*" className="hidden" onChange={handleImageUpload} />
        </label>
      </div>
      <textarea
        value={form.description}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        placeholder="설명"
        className="md:col-span-2 rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <button
        type="submit"
        className="md:col-span-2 rounded-xl bg-blue-600 py-3 text-sm font-bold text-white"
      >
        {submitLabel || '상품 등록'}
      </button>
    </form>
  );
}
