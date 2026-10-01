import React, { useState } from 'react';

const emptyForm = {
  name: '',
  price: '',
  stockQuantity: '',
  imageUrl: '',
  description: '',
};

export default function ProductForm({ onSubmit, initialValue, submitLabel }) {
  const [form, setForm] = useState(initialValue || emptyForm);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit({
      ...form,
      price: Number(form.price || 0),
      stockQuantity: Number(form.stockQuantity || 0),
    });
    if (!initialValue) {
      setForm(emptyForm);
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
        type="number"
        value={form.price}
        onChange={(e) => setForm({ ...form, price: e.target.value })}
        placeholder="가격"
        className="rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <input
        type="number"
        value={form.stockQuantity}
        onChange={(e) => setForm({ ...form, stockQuantity: e.target.value })}
        placeholder="재고"
        className="rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <input
        value={form.imageUrl}
        onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
        placeholder="이미지 URL"
        className="rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
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
