import React, { useCallback, useEffect, useState } from 'react';
import { adminAuthFail, adminHeaders } from '../../utils/adminApi';

function formatDate(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString('ko-KR');
}

export default function AdminCategoryPanel() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [draftName, setDraftName] = useState('');

  const loadCategories = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await fetch('/api/admin/categories', { headers: adminHeaders() });
      if (adminAuthFail(response)) {
        return;
      }
      const data = await response.json().catch(() => null);
      if (!response.ok || !Array.isArray(data)) {
        throw new Error(data?.message || '카테고리 목록을 불러오지 못했습니다.');
      }
      setCategories(data);
    } catch (loadError) {
      setError(loadError.message || '카테고리 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  const showNotice = (message) => {
    setNotice(message);
    window.setTimeout(() => setNotice(''), 2200);
  };

  const createCategory = async (event) => {
    event.preventDefault();
    if (saving) {
      return;
    }
    const nextName = name.trim();
    if (!nextName) {
      setError('카테고리명은 필수입니다.');
      return;
    }
    if (nextName.length > 50) {
      setError('카테고리명은 50자 이하로 입력해 주세요.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const response = await fetch('/api/admin/categories', {
        method: 'POST',
        headers: adminHeaders(),
        body: JSON.stringify({ name: nextName }),
      });
      const data = await response.json().catch(() => ({}));
      if (adminAuthFail(response)) {
        return;
      }
      if (!response.ok) {
        setError(data.message || '카테고리를 등록하지 못했습니다.');
        return;
      }
      setName('');
      showNotice('카테고리가 등록되었습니다.');
      await loadCategories();
    } catch (createError) {
      setError(createError.message || '카테고리를 등록하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const saveName = async (category) => {
    if (saving) {
      return;
    }
    const nextName = draftName.trim();
    if (!nextName) {
      setError('카테고리명은 필수입니다.');
      return;
    }
    if (nextName.length > 50) {
      setError('카테고리명은 50자 이하로 입력해 주세요.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const response = await fetch(`/api/admin/categories/${category.id}`, {
        method: 'PUT',
        headers: adminHeaders(),
        body: JSON.stringify({ name: nextName }),
      });
      const data = await response.json().catch(() => ({}));
      if (adminAuthFail(response)) {
        return;
      }
      if (!response.ok) {
        setError(data.message || '카테고리를 수정하지 못했습니다.');
        return;
      }
      setEditingId(null);
      setDraftName('');
      showNotice('카테고리명이 수정되었습니다.');
      await loadCategories();
    } catch (updateError) {
      setError(updateError.message || '카테고리를 수정하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const deleteCategory = async (category) => {
    const count = Number(category.productCount || 0);
    if (count > 0) {
      window.alert(
        `'${category.name}'에는 상품 ${count}개가 연결되어 있습니다. 삭제할 수 없으며, 상품은 삭제되거나 다른 카테고리로 옮겨지지 않습니다.`
      );
      return;
    }
    const confirmed = window.confirm(
      `'${category.name}' 카테고리를 삭제할까요? 연결된 상품은 없습니다.`
    );
    if (!confirmed || saving) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      const response = await fetch(`/api/admin/categories/${category.id}`, {
        method: 'DELETE',
        headers: adminHeaders(),
      });
      const data = await response.json().catch(() => ({}));
      if (adminAuthFail(response)) {
        return;
      }
      if (!response.ok || data.status === 'FAIL') {
        setError(data.message || '카테고리를 삭제하지 못했습니다.');
        return;
      }
      showNotice('카테고리가 삭제되었습니다.');
      await loadCategories();
    } catch (deleteError) {
      setError(deleteError.message || '카테고리를 삭제하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-2xl bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold">카테고리 관리</h2>
          <p className="mt-1 text-xs text-slate-500">이름은 앞뒤 공백을 제외하고, 같은 이름은 등록할 수 없습니다.</p>
        </div>
      </div>

      {notice ? (
        <div className="mt-4 rounded-xl border border-emerald-100 bg-emerald-50 p-3 text-xs font-bold text-emerald-700">
          {notice}
        </div>
      ) : null}
      {error ? (
        <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs font-semibold text-slate-700">
          {error}
        </div>
      ) : null}

      <form onSubmit={createCategory} className="mt-5 flex flex-col gap-2 sm:flex-row">
        <label htmlFor="category-name" className="sr-only">
          카테고리명
        </label>
        <input
          id="category-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="카테고리명"
          maxLength={50}
          className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm"
        />
        <button
          type="submit"
          disabled={saving}
          className="rounded-md bg-[#0A192F] px-4 py-3 text-sm font-semibold text-white disabled:opacity-50"
        >
          {saving ? '처리 중' : '카테고리 등록'}
        </button>
      </form>

      {loading ? (
        <p className="mt-8 text-sm text-slate-500">카테고리를 불러오는 중입니다.</p>
      ) : categories.length === 0 ? (
        <div className="mt-8 rounded-md border border-slate-100 bg-slate-50 py-16 text-center">
          <p className="text-sm text-slate-600">등록된 카테고리가 없습니다.</p>
          <button
            type="button"
            onClick={() => document.getElementById('category-name')?.focus()}
            className="mt-4 rounded-md bg-[#0A192F] px-4 py-2 text-sm font-semibold text-white"
          >
            카테고리 등록
          </button>
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-slate-100 text-xs text-slate-500">
              <tr>
                <th className="px-3 py-2 font-semibold">ID</th>
                <th className="px-3 py-2 font-semibold">카테고리명</th>
                <th className="px-3 py-2 font-semibold">상품 수</th>
                <th className="px-3 py-2 font-semibold">생성일</th>
                <th className="px-3 py-2 font-semibold">관리</th>
              </tr>
            </thead>
            <tbody>
              {categories.map((category) => (
                <tr key={category.id} className="border-b border-slate-50">
                  <td className="px-3 py-3">{category.id}</td>
                  <td className="px-3 py-3">
                    {editingId === category.id ? (
                      <input
                        aria-label={`${category.name} 수정`}
                        value={draftName}
                        onChange={(event) => setDraftName(event.target.value)}
                        className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
                      />
                    ) : (
                      category.name
                    )}
                  </td>
                  <td className="px-3 py-3">{Number(category.productCount || 0)}</td>
                  <td className="px-3 py-3 whitespace-nowrap">{formatDate(category.createdAt)}</td>
                  <td className="px-3 py-3">
                    <div className="flex flex-wrap gap-2">
                      {editingId === category.id ? (
                        <>
                          <button
                            type="button"
                            disabled={saving}
                            onClick={() => saveName(category)}
                            className="rounded-md bg-[#0A192F] px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                          >
                            저장
                          </button>
                          <button
                            type="button"
                            disabled={saving}
                            onClick={() => {
                              setEditingId(null);
                              setDraftName('');
                            }}
                            className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold"
                          >
                            취소
                          </button>
                        </>
                      ) : (
                        <button
                          type="button"
                          onClick={() => {
                            setEditingId(category.id);
                            setDraftName(category.name || '');
                            setError('');
                          }}
                          className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold"
                        >
                          수정
                        </button>
                      )}
                      <button
                        type="button"
                        disabled={saving}
                        onClick={() => deleteCategory(category)}
                        className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold disabled:opacity-50"
                      >
                        삭제
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
