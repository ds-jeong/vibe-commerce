import React, { useCallback, useEffect, useState } from 'react';
import ProductForm from './ProductForm';
import AdminProductRow from './AdminProductRow';
import Pagination from '../common/Pagination';
import { adminAuthFail, adminHeaders, unwrapPage } from '../../utils/adminApi';

const PAGE_SIZE = 8;

export default function AdminProductDashboard() {
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [products, setProducts] = useState([]);
  const [editingProduct, setEditingProduct] = useState(null);
  const [toast, setToast] = useState('');

  const showToast = useCallback((message) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 2200);
  }, []);

  const loadProducts = useCallback(async () => {
    const params = new URLSearchParams({
      page: String(currentPage),
      size: String(PAGE_SIZE),
    });
    if (appliedKeyword) {
      params.set('keyword', appliedKeyword);
    }
    const res = await fetch(`/api/admin/products?${params.toString()}`, {
      headers: adminHeaders(),
    });
    if (adminAuthFail(res)) {
      return;
    }
    const data = await res.json().catch(() => []);
    const parsed = unwrapPage(data);
    setProducts(parsed.rows);
    setTotalPages(parsed.totalPages);
  }, [currentPage, appliedKeyword]);

  useEffect(() => {
    loadProducts();
  }, [loadProducts]);

  const handleSearch = (e) => {
    e.preventDefault();
    setCurrentPage(0);
    setAppliedKeyword(keyword.trim());
  };

  const createProduct = useCallback(
    async (payload) => {
      await fetch('/api/admin/products', {
        method: 'POST',
        headers: adminHeaders(),
        body: JSON.stringify(payload),
      });
      showToast('상품이 등록되었습니다.');
      await loadProducts();
    },
    [loadProducts, showToast]
  );

  const updateProduct = useCallback(
    async (payload) => {
      if (!editingProduct?.id) {
        return;
      }
      await fetch(`/api/admin/products/${editingProduct.id}`, {
        method: 'PUT',
        headers: adminHeaders(),
        body: JSON.stringify(payload),
      });
      setEditingProduct(null);
      showToast('상품이 수정되었습니다.');
      await loadProducts();
    },
    [editingProduct, loadProducts, showToast]
  );

  const deleteProduct = useCallback(
    async (id) => {
      if (!window.confirm('정말 삭제하시겠습니까?')) {
        return;
      }
      const res = await fetch(`/api/admin/products/${id}`, {
        method: 'DELETE',
        headers: adminHeaders(),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        alert(data.message || '상품을 삭제하지 못했습니다.');
        return;
      }
      showToast('상품이 삭제되었습니다.');
      await loadProducts();
    },
    [loadProducts, showToast]
  );

  return (
    <div className="rounded-2xl bg-white p-6 shadow-sm">
      {toast ? (
        <div className="mb-4 rounded-xl border border-emerald-100 bg-emerald-50 p-3 text-xs font-bold text-emerald-700">
          {toast}
        </div>
      ) : null}

      <h2 className="mb-4 text-lg font-bold">상품 등록</h2>
      <ProductForm onSubmit={createProduct} />

      {editingProduct ? (
        <div className="mt-6">
          <h3 className="mb-2 font-bold">상품 수정</h3>
          <ProductForm
            key={editingProduct.id}
            initialValue={{
              name: editingProduct.name || '',
              price: editingProduct.price || '',
              stockQuantity: editingProduct.stockQuantity || '',
              imageUrl: editingProduct.imageUrl || '',
              description: editingProduct.description || '',
            }}
            onSubmit={updateProduct}
            submitLabel="상품 수정"
          />
        </div>
      ) : null}

      <form onSubmit={handleSearch} className="mt-6 flex gap-2">
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="상품명 검색"
          className="w-full rounded-xl border px-4 py-2.5 text-sm"
        />
        <button
          type="submit"
          className="rounded-md bg-[#0A192F] px-4 py-2.5 text-xs font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
        >
          검색
        </button>
      </form>

      <div className="mt-4 space-y-2">
        {products.map((product) => (
          <AdminProductRow
            key={product.id}
            product={product}
            onEdit={setEditingProduct}
            onDelete={deleteProduct}
          />
        ))}
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
