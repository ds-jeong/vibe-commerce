import React, { useEffect, useState } from 'react';
import { formatPriceInput, parsePriceInput } from '../../utils/media';
import ProductImage from '../common/ProductImage';

const emptyForm = {
  name: '',
  price: '',
  stockQuantity: '',
  imageUrl: '',
  description: '',
  detailImages: [],
  categoryId: '',
  saleStatus: 'ON_SALE',
};

const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const MIN_IMAGE_SIZE = 800;
const MAX_DETAIL_IMAGES = 10;

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

function normalizeDetailImages(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter((item) => item && item.imageUrl)
    .slice(0, MAX_DETAIL_IMAGES)
    .map((item) => ({
      id: item.id ?? null,
      imageUrl: item.imageUrl,
    }));
}

async function uploadImageFile(file) {
  if (file.size > MAX_IMAGE_BYTES) {
    throw new Error('이미지 파일은 2MB 이하여야 합니다.');
  }
  const { width, height } = await readImageSize(file);
  if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
    throw new Error(`이미지는 ${MIN_IMAGE_SIZE}×${MIN_IMAGE_SIZE}px 이상이어야 합니다. (선택한 파일: ${width}×${height}px)`);
  }
  const token = localStorage.getItem('adminToken');
  const body = new FormData();
  body.append('file', file);
  const res = await fetch('/api/admin/products/upload', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || !data.imageUrl) {
    throw new Error(data.message || '이미지를 업로드하지 못했습니다.');
  }
  return data.imageUrl;
}

export default function ProductForm({ onSubmit, initialValue, submitLabel }) {
  const [form, setForm] = useState(() => {
    const seed = initialValue || emptyForm;
    return {
      ...emptyForm,
      ...seed,
      price: seed.price === '' || seed.price == null ? '' : formatPriceInput(seed.price),
      detailImages: normalizeDetailImages(seed.detailImages),
      categoryId: seed.categoryId == null || seed.categoryId === '' ? '' : String(seed.categoryId),
      saleStatus: seed.saleStatus === 'STOPPED' ? 'STOPPED' : 'ON_SALE',
    };
  });
  const [categories, setCategories] = useState([]);
  const [categoryState, setCategoryState] = useState('loading');
  const [categoryMessage, setCategoryMessage] = useState('');
  const categoryFieldId = initialValue ? 'product-category-edit' : 'product-category';
  const saleStatusFieldId = initialValue ? 'product-sale-status-edit' : 'product-sale-status';
  const [uploading, setUploading] = useState(false);
  const [uploadingDetail, setUploadingDetail] = useState(false);
  const [detailNotice, setDetailNotice] = useState('');

  const loadCategories = async () => {
    setCategoryState('loading');
    setCategoryMessage('');
    try {
      const response = await fetch('/api/categories');
      const data = await response.json().catch(() => null);
      if (!response.ok || !Array.isArray(data)) {
        throw new Error('카테고리 목록을 불러오지 못했습니다.');
      }
      setCategories(data);
      setCategoryState('ready');
    } catch (error) {
      setCategories([]);
      setCategoryState('error');
      setCategoryMessage(error.message || '카테고리 목록을 불러오지 못했습니다.');
    }
  };

  useEffect(() => {
    loadCategories();
  }, []);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (uploading || uploadingDetail || categoryState !== 'ready') {
      return;
    }
    onSubmit({
      name: form.name,
      stockQuantity: Number(form.stockQuantity || 0),
      imageUrl: form.imageUrl,
      description: form.description,
      price: parsePriceInput(form.price),
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      saleStatus: form.saleStatus === 'STOPPED' ? 'STOPPED' : 'ON_SALE',
      detailImages: form.detailImages.map((image, index) => ({
        ...(image.id ? { id: image.id } : {}),
        imageUrl: image.imageUrl,
        sortOrder: index,
      })),
    });
    if (!initialValue) {
      setForm(emptyForm);
      setDetailNotice('');
    }
  };

  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) {
      return;
    }
    setUploading(true);
    try {
      const imageUrl = await uploadImageFile(file);
      setForm((prev) => ({ ...prev, imageUrl }));
    } catch (err) {
      alert(err.message || '이미지를 업로드하지 못했습니다.');
    } finally {
      setUploading(false);
    }
  };

  const handleDetailUpload = async (e) => {
    const selected = Array.from(e.target.files || []);
    e.target.value = '';
    if (!selected.length) {
      return;
    }
    const room = MAX_DETAIL_IMAGES - form.detailImages.length;
    if (room <= 0) {
      const message = '상세 이미지는 최대 10장까지 등록할 수 있습니다.';
      setDetailNotice(message);
      alert(message);
      return;
    }
    const accepted = selected.slice(0, room);
    const skipped = selected.length - accepted.length;
    const uploaded = [];
    const failures = [];
    setUploadingDetail(true);
    for (const file of accepted) {
      try {
        const imageUrl = await uploadImageFile(file);
        uploaded.push({ id: null, imageUrl });
      } catch (err) {
        failures.push(`${file.name}: ${err.message || '이미지를 업로드하지 못했습니다.'}`);
      }
    }
    setUploadingDetail(false);
    if (uploaded.length) {
      setForm((prev) => ({
        ...prev,
        detailImages: [...prev.detailImages, ...uploaded].slice(0, MAX_DETAIL_IMAGES),
      }));
    }
    const lines = [];
    if (uploaded.length) {
      lines.push(`상세 이미지 ${uploaded.length}장을 추가했습니다.`);
    }
    if (skipped) {
      lines.push(`최대 10장이라 ${skipped}장은 추가하지 않았습니다.`);
    }
    if (failures.length) {
      lines.push(failures.join('\n'));
    }
    const notice = lines.join('\n');
    setDetailNotice(notice);
    if (skipped || failures.length) {
      alert(notice);
    }
  };

  const moveDetailImage = (index, direction) => {
    const nextIndex = index + direction;
    setForm((prev) => {
      if (nextIndex < 0 || nextIndex >= prev.detailImages.length) {
        return prev;
      }
      const detailImages = [...prev.detailImages];
      const [moved] = detailImages.splice(index, 1);
      detailImages.splice(nextIndex, 0, moved);
      return { ...prev, detailImages };
    });
  };

  const removeDetailImage = (index) => {
    setForm((prev) => ({
      ...prev,
      detailImages: prev.detailImages.filter((_, imageIndex) => imageIndex !== index),
    }));
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
      <div>
        <label htmlFor={saleStatusFieldId} className="mb-1 block text-xs font-semibold text-slate-500">
          판매 상태
        </label>
        <select
          id={saleStatusFieldId}
          value={form.saleStatus}
          onChange={(e) => setForm({ ...form, saleStatus: e.target.value })}
          className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm"
        >
          <option value="ON_SALE">판매중</option>
          <option value="STOPPED">판매중지</option>
        </select>
      </div>
      <div>
        <label htmlFor={categoryFieldId} className="mb-1 block text-xs font-semibold text-slate-500">
          카테고리
        </label>
        <select
          id={categoryFieldId}
          value={form.categoryId}
          disabled={categoryState !== 'ready'}
          onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
          className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm"
        >
          <option value="">미지정</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
          {form.categoryId && !categories.some((category) => String(category.id) === String(form.categoryId)) ? (
            <option value={form.categoryId}>{initialValue?.categoryName || `카테고리 ${form.categoryId}`}</option>
          ) : null}
        </select>
        {categoryState === 'loading' ? (
          <p className="mt-1 text-xs text-slate-500">카테고리를 불러오는 중입니다.</p>
        ) : null}
        {categoryState === 'error' ? (
          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-600">
            <p>{categoryMessage} 카테고리를 확인하기 전에는 저장하지 않습니다.</p>
            <button
              type="button"
              onClick={loadCategories}
              className="rounded-md border border-slate-200 px-2 py-1 font-semibold"
            >
              다시 시도
            </button>
          </div>
        ) : null}
      </div>
      <div className="md:col-span-2 rounded-xl border border-slate-200 p-4">
        <p className="text-sm font-semibold text-black">대표 이미지</p>
        <p className="mt-1 text-xs text-slate-500">목록, 장바구니, 주문에 표시되는 이미지 1장입니다.</p>
        <div className="mt-3 flex gap-2">
          <input
            value={form.imageUrl}
            onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
            placeholder="대표 이미지 URL 또는 업로드"
            className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm"
          />
          <label className="whitespace-nowrap rounded-md border border-slate-200 px-3 py-3 text-xs font-semibold text-slate-600 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50">
            {uploading ? '업로드 중' : '파일'}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              className="hidden"
              disabled={uploading}
              onChange={handleImageUpload}
            />
          </label>
        </div>
        {form.imageUrl ? (
          <ProductImage src={form.imageUrl} alt="대표 이미지" className="mt-3 h-24 w-24 rounded-lg object-cover" />
        ) : null}
        <p className="mt-2 text-xs font-semibold text-slate-500">
          권장 800×800px 이상, JPG/PNG/WebP/GIF, 최대 2MB
        </p>
      </div>
      <div className="md:col-span-2 rounded-xl border border-slate-200 p-4">
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm font-semibold text-black">상세 이미지</p>
          <p className="text-xs font-semibold text-slate-500">{form.detailImages.length}/10</p>
        </div>
        <p className="mt-1 text-xs text-slate-500">상세페이지에만 순서대로 표시됩니다. 대표 이미지와 별개이며 0장도 가능합니다.</p>
        <label className="mt-3 inline-flex cursor-pointer rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 shadow-sm transition hover:bg-slate-50">
          {uploadingDetail ? '업로드 중' : '상세 이미지 추가'}
          <input
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp,image/gif"
            className="hidden"
            disabled={uploadingDetail || form.detailImages.length >= MAX_DETAIL_IMAGES}
            onChange={handleDetailUpload}
          />
        </label>
        {detailNotice ? (
          <p className="mt-2 whitespace-pre-line text-xs text-slate-600">{detailNotice}</p>
        ) : null}
        <div className="mt-3 space-y-2">
          {form.detailImages.map((image, index) => (
            <div key={`${image.id || 'new'}-${image.imageUrl}-${index}`} className="flex items-center gap-3 rounded-lg border border-slate-100 p-2">
              <span className="w-6 text-center text-xs font-semibold text-slate-500">{index + 1}</span>
              <ProductImage src={image.imageUrl} alt={`상세 이미지 ${index + 1}`} className="h-14 w-14 rounded-md object-cover" />
              <p className="min-w-0 flex-1 truncate text-xs text-slate-500">{image.imageUrl}</p>
              <button
                type="button"
                onClick={() => moveDetailImage(index, -1)}
                disabled={index === 0}
                className="rounded-md border border-slate-200 px-2 py-1 text-xs disabled:opacity-40"
              >
                위로
              </button>
              <button
                type="button"
                onClick={() => moveDetailImage(index, 1)}
                disabled={index === form.detailImages.length - 1}
                className="rounded-md border border-slate-200 px-2 py-1 text-xs disabled:opacity-40"
              >
                아래로
              </button>
              <button
                type="button"
                onClick={() => removeDetailImage(index)}
                className="rounded-md border border-slate-200 px-2 py-1 text-xs"
              >
                제거
              </button>
            </div>
          ))}
        </div>
      </div>
      <textarea
        value={form.description}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
        placeholder="설명"
        className="md:col-span-2 rounded-xl border border-gray-200 px-4 py-3 text-sm"
      />
      <button
        type="submit"
        disabled={uploading || uploadingDetail || categoryState !== 'ready'}
        className="md:col-span-2 rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:opacity-50"
      >
        {submitLabel || '상품 등록'}
      </button>
    </form>
  );
}
