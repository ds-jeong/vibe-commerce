import React, { useState } from 'react';
import { formatPriceInput, parsePriceInput } from '../../utils/media';

const emptyForm = {
  name: '',
  price: '',
  stockQuantity: '',
  imageUrl: '',
  description: '',
};

const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const MIN_IMAGE_SIZE = 800;

function readImageSize(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve({ width: image.naturalWidth, height: image.naturalHeight });
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('이미지를 읽을 수 없습니다.'));
    };
    image.src = url;
  });
}

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
    if (file.size > MAX_IMAGE_BYTES) {
      alert('이미지 파일은 2MB 이하여야 합니다.');
      e.target.value = '';
      return;
    }
    try {
      const { width, height } = await readImageSize(file);
      if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
        alert(`이미지는 ${MIN_IMAGE_SIZE}×${MIN_IMAGE_SIZE}px 이상이어야 합니다. (선택한 파일: ${width}×${height}px)`);
        e.target.value = '';
        return;
      }
    } catch (err) {
      alert(err.message || '이미지를 확인할 수 없습니다.');
      e.target.value = '';
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
        alert(data.message || '이미지를 업로드하지 못했습니다.');
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
      <div className="md:col-span-2">
        <div className="flex gap-2">
          <input
            value={form.imageUrl}
            onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
            placeholder="이미지 URL 또는 업로드"
            className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm"
          />
          <label className="whitespace-nowrap rounded-md border border-slate-200 px-3 py-3 text-xs font-semibold text-slate-600 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50">
            {uploading ? '업로드 중' : '파일'}
            <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" className="hidden" onChange={handleImageUpload} />
          </label>
        </div>
        <p className="mt-1 text-xs font-semibold text-slate-500">
          권장 800×800px 이상, JPG/PNG/WebP, 최대 2MB
        </p>
      </div>
      <textarea
        value={form.description}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        placeholder="설명"
        className="md:col-span-2 rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <button
        type="submit"
        className="md:col-span-2 rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
      >
        {submitLabel || '상품 등록'}
      </button>
    </form>
  );
}
