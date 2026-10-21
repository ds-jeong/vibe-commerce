import React, { useCallback, useEffect, useState } from 'react';
import Pagination from '../common/Pagination';
import ProductImage from '../common/ProductImage';

const PAGE_SIZE = 5;
const MAX_IMAGES = 5;

function Stars({ value }) {
  const score = Number(value) || 0;
  return (
    <span aria-label={`5점 만점에 ${score}점`} className="tracking-tight">
      {[1, 2, 3, 4, 5].map((star) => (
        <span key={star} className={star <= score ? 'text-amber-500' : 'text-slate-200'}>★</span>
      ))}
    </span>
  );
}

function StarPicker({ value, onChange, labelSuffix = '' }) {
  const [hovered, setHovered] = useState(0);
  const active = hovered || value;
  return (
    <div
      role="radiogroup"
      aria-label="평점"
      className="mt-2 flex gap-0.5"
      onMouseLeave={() => setHovered(0)}
    >
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          role="radio"
          aria-label={`${star}점${labelSuffix}`}
          aria-checked={value === star}
          onMouseEnter={() => setHovered(star)}
          onFocus={() => setHovered(star)}
          onBlur={() => setHovered(0)}
          onClick={() => onChange(star)}
          className={`text-2xl leading-none ${star <= active ? 'text-amber-500' : 'text-slate-300'}`}
        >
          ★
        </button>
      ))}
    </div>
  );
}

function formatWhen(value) {
  if (!value) {
    return '';
  }
  return String(value).replace('T', ' ').slice(0, 16);
}

export default function ProductReviews({ productId, userToken }) {
  const [page, setPage] = useState(0);
  const [reviews, setReviews] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [reviewCount, setReviewCount] = useState(0);
  const [averageRating, setAverageRating] = useState(null);
  const [canWrite, setCanWrite] = useState(false);
  const [reviewedByCurrentUser, setReviewedByCurrentUser] = useState(false);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');
  const [rating, setRating] = useState(5);
  const [content, setContent] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [images, setImages] = useState([]);
  const [pending, setPending] = useState(false);

  const load = useCallback(async (nextPage = page) => {
    setStatus('loading');
    setError('');
    try {
      const headers = {};
      if (userToken) {
        headers.Authorization = `Bearer ${userToken}`;
      }
      const response = await fetch(
        `/api/products/${productId}/reviews?page=${nextPage}&size=${PAGE_SIZE}`,
        { headers }
      );
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(data.message || '리뷰를 불러오지 못했습니다.');
      }
      const rows = Array.isArray(data.content) ? data.content : [];
      setReviews(rows);
      setTotalPages(Number(data.totalPages || 0));
      setReviewCount(Number(data.reviewCount || 0));
      setAverageRating(data.averageRating == null ? null : Number(data.averageRating));
      setCanWrite(Boolean(data.canWrite));
      setReviewedByCurrentUser(Boolean(data.reviewedByCurrentUser));
      setStatus('ready');
    } catch (loadError) {
      setStatus('error');
      setError(loadError.message || '리뷰를 불러오지 못했습니다.');
    }
  }, [page, productId, userToken]);

  useEffect(() => {
    setPage(0);
    setEditingId(null);
    setContent('');
    setRating(5);
    setImages([]);
  }, [productId, userToken]);

  useEffect(() => {
    load(page);
  }, [load, page]);

  const submit = async (event) => {
    event.preventDefault();
    if (!userToken || pending) {
      return;
    }
    setPending(true);
    setError('');
    try {
      const response = await fetch(
        editingId
          ? `/api/products/${productId}/reviews/${editingId}`
          : `/api/products/${productId}/reviews`,
        {
          method: editingId ? 'PUT' : 'POST',
          headers: {
            Authorization: `Bearer ${userToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ rating, content, imageUrls: images }),
        }
      );
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(data.message || '리뷰를 저장하지 못했습니다.');
      }
      setEditingId(null);
      setContent('');
      setRating(5);
      setImages([]);
      await load(page);
    } catch (submitError) {
      setError(submitError.message || '리뷰를 저장하지 못했습니다.');
    } finally {
      setPending(false);
    }
  };

  const remove = async (review) => {
    if (!userToken || pending) {
      return;
    }
    setPending(true);
    setError('');
    try {
      const response = await fetch(`/api/products/${productId}/reviews/${review.id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${userToken}` },
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(data.message || '리뷰를 삭제하지 못했습니다.');
      }
      const nextPage = reviews.length === 1 && page > 0 ? page - 1 : page;
      if (nextPage !== page) {
        setPage(nextPage);
      } else {
        await load(page);
      }
    } catch (deleteError) {
      setError(deleteError.message || '리뷰를 삭제하지 못했습니다.');
    } finally {
      setPending(false);
    }
  };

  const startEdit = (review) => {
    setEditingId(review.id);
    setRating(Number(review.rating) || 5);
    setContent(review.content || '');
    setImages(Array.isArray(review.imageUrls) ? review.imageUrls : []);
    setError('');
  };

  const addImages = async (fileList) => {
    const files = Array.from(fileList || []);
    if (files.length === 0 || pending) {
      return;
    }
    if (images.length + files.length > MAX_IMAGES) {
      setError('리뷰 이미지는 최대 5장까지 등록할 수 있습니다.');
      return;
    }
    setPending(true);
    setError('');
    try {
      const uploaded = [];
      for (const file of files) {
        const body = new FormData();
        body.append('file', file);
        const response = await fetch(`/api/products/${productId}/reviews/images`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${userToken}` },
          body,
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(data.message || '이미지를 올리지 못했습니다.');
        }
        uploaded.push(data.imageUrl);
      }
      setImages((current) => [...current, ...uploaded].slice(0, MAX_IMAGES));
    } catch (uploadError) {
      setError(uploadError.message || '이미지를 올리지 못했습니다.');
    } finally {
      setPending(false);
    }
  };

  const averageText = averageRating == null ? '0.0' : averageRating.toFixed(1);

  return (
    <section className="mx-auto mt-16 w-full max-w-3xl border-t border-slate-100 pt-10">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h3 className="text-sm font-semibold text-slate-500">리뷰</h3>
        <p className="text-sm text-slate-700">
          <Stars value={Math.round(averageRating || 0)} />
          <span className="ml-2 font-semibold text-black">{averageText}</span>
          <span className="ml-2 text-slate-500">리뷰 {reviewCount.toLocaleString('ko-KR')}개</span>
        </p>
      </div>

      {error ? (
        <p className="mt-4 rounded-md border border-red-100 bg-red-50 px-3 py-2 text-xs font-semibold text-red-500">
          {error}
        </p>
      ) : null}

      {userToken && canWrite ? (
        <form onSubmit={submit} className="mt-6 rounded-md border border-slate-100 p-4">
          <p className="text-xs font-semibold text-slate-500">평점</p>
          <StarPicker value={rating} onChange={setRating} />
          <label className="mt-4 block text-xs font-semibold text-slate-500" htmlFor="review-content">
            리뷰
          </label>
          <textarea
            id="review-content"
            value={content}
            maxLength={1000}
            required
            onChange={(event) => setContent(event.target.value)}
            className="mt-2 min-h-28 w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
            placeholder="상품은 어땠나요?"
          />
          <ImagePicker images={images} pending={pending} onAdd={addImages} onRemove={setImages} />
          <button
            type="submit"
            disabled={pending}
            className="mt-3 rounded-md bg-[#0A192F] px-4 py-2 text-xs font-semibold text-white disabled:opacity-60"
          >
            리뷰 등록
          </button>
        </form>
      ) : null}

      {userToken && status === 'ready' && !canWrite && !reviewedByCurrentUser ? (
        <p className="mt-4 text-xs text-slate-500">구매가 확인된 뒤에 리뷰를 작성할 수 있습니다.</p>
      ) : null}

      {status === 'loading' && reviews.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-400">리뷰를 불러오는 중입니다.</p>
      ) : null}

      {status === 'ready' && reviews.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-400">아직 작성된 리뷰가 없습니다.</p>
      ) : null}

      <ul className="mt-6 space-y-4">
        {reviews.map((review) => (
          <li key={review.id} className="rounded-md border border-slate-100 p-4">
            {editingId === review.id ? (
              <form onSubmit={submit}>
                <StarPicker value={rating} onChange={setRating} labelSuffix="으로 수정" />
                <textarea
                  value={content}
                  maxLength={1000}
                  required
                  onChange={(event) => setContent(event.target.value)}
                  className="mt-3 min-h-24 w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
                />
                <ImagePicker images={images} pending={pending} onAdd={addImages} onRemove={setImages} />
                <div className="mt-3 flex gap-2">
                  <button
                    type="submit"
                    disabled={pending}
                    className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white disabled:opacity-60"
                  >
                    저장
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditingId(null)}
                    className="rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600"
                  >
                    취소
                  </button>
                </div>
              </form>
            ) : (
              <>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-black">{review.authorName}</p>
                    <p className="mt-1 text-xs text-slate-400">
                      {formatWhen(review.createdAt)}
                      {review.updatedAt && review.updatedAt !== review.createdAt ? ' · 수정됨' : ''}
                    </p>
                  </div>
                  <Stars value={review.rating} />
                </div>
                <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">{review.content}</p>
                <ReviewImages imageUrls={review.imageUrls} />
                {review.mine ? (
                  <div className="mt-3 flex gap-2">
                    <button
                      type="button"
                      onClick={() => startEdit(review)}
                      className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600"
                    >
                      수정
                    </button>
                    <button
                      type="button"
                      disabled={pending}
                      onClick={() => remove(review)}
                      className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 disabled:opacity-60"
                    >
                      삭제
                    </button>
                  </div>
                ) : null}
              </>
            )}
          </li>
        ))}
      </ul>

      <Pagination
        currentPage={page}
        totalPages={totalPages}
        handlePreviousPage={() => setPage((current) => Math.max(0, current - 1))}
        handleNextPage={() => setPage((current) => (totalPages > 0 ? Math.min(totalPages - 1, current + 1) : current))}
        setCurrentPage={setPage}
      />
    </section>
  );
}

function ReviewImages({ imageUrls }) {
  const urls = Array.isArray(imageUrls) ? imageUrls : [];
  if (urls.length === 0) {
    return null;
  }
  return (
    <div className="mt-3 flex flex-wrap gap-3">
      {urls.map((url) => (
        <ProductImage
          key={url}
          src={url}
          alt="리뷰 이미지"
          className="h-48 w-48 rounded-md object-cover"
        />
      ))}
    </div>
  );
}

function ImagePicker({ images, pending, onAdd, onRemove }) {
  return (
    <div className="mt-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-500">사진 {images.length}/5</p>
        <label className="cursor-pointer rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600">
          사진 추가
          <input
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            multiple
            disabled={pending || images.length >= MAX_IMAGES}
            className="sr-only"
            onChange={(event) => {
              onAdd(event.target.files);
              event.target.value = '';
            }}
          />
        </label>
      </div>
      {images.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-3">
          {images.map((url) => (
            <div key={url} className="relative">
              <ProductImage src={url} alt="선택한 리뷰 이미지" className="h-36 w-36 rounded-md object-cover" />
              <button
                type="button"
                aria-label="사진 제거"
                onClick={() => onRemove(images.filter((item) => item !== url))}
                className="absolute right-1 top-1 rounded-full bg-white/90 px-1.5 text-[10px] font-semibold text-slate-700"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
