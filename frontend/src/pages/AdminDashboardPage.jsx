import React, { useEffect, useState } from 'react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  BarChart, Bar, Legend, PieChart, Pie, Cell
} from 'recharts';
import ProductForm from '../components/admin/ProductForm';
import { ORDER_STATUS_LABEL, INQUIRY_STATUS_LABEL } from '../utils/validation';

const COLORS = ['#3B82F6', '#10B981', '#EF4444', '#F59E0B', '#8B5CF6'];

export default function AdminDashboardPage() {
  const token = localStorage.getItem('adminToken');
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  const [tab, setTab] = useState('dashboard');
  const [data, setData] = useState([]);
  const [liveStats, setLiveStats] = useState(null);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [inquiries, setInquiries] = useState([]);
  const [editingProduct, setEditingProduct] = useState(null);
  const [trackingDraft, setTrackingDraft] = useState({});
  const [answerDraft, setAnswerDraft] = useState({});

  const authFail = (res) => {
    if (res.status === 403 || res.status === 401) {
      alert('최고관리자 보안 권한 전표가 없거나 만료되었습니다.');
      window.location.href = '/admin/login';
      return true;
    }
    return false;
  };

  const loadDashboard = () => {
    fetch('/api/admin/dashboard-stats', { headers })
      .then((res) => {
        if (authFail(res)) return [];
        return res.json();
      })
      .then((stats) => {
        if (Array.isArray(stats)) {
          setData(stats);
        }
      })
      .catch(() => setData([]));

    fetch('/api/admin/dashboard/stats', { headers })
      .then((res) => (res.ok ? res.json() : {}))
      .then((stats) => setLiveStats(stats || {}))
      .catch(() => setLiveStats({}));
  };

  const loadProducts = () => {
    fetch('/api/admin/products', { headers })
      .then((res) => (res.ok ? res.json() : []))
      .then((rows) => setProducts(Array.isArray(rows) ? rows : []))
      .catch(() => setProducts([]));
  };

  const loadOrders = () => {
    fetch('/api/admin/orders', { headers })
      .then((res) => (res.ok ? res.json() : []))
      .then((rows) => setOrders(Array.isArray(rows) ? rows : []))
      .catch(() => setOrders([]));
  };

  const loadInquiries = () => {
    fetch('/api/admin/inquiries', { headers })
      .then((res) => (res.ok ? res.json() : []))
      .then((rows) => setInquiries(Array.isArray(rows) ? rows : []))
      .catch(() => setInquiries([]));
  };

  useEffect(() => {
    loadDashboard();
    loadProducts();
    loadOrders();
    loadInquiries();
  }, []);

  const totalSales = (Array.isArray(data) ? data : []).reduce(
    (sum, d) => sum + Number(d?.sales || 0),
    0
  );
  const totalSettlement = (Array.isArray(data) ? data : []).reduce(
    (sum, d) => sum + Number(d?.settlement || 0),
    0
  );

  const pieData =
    Array.isArray(liveStats?.statusCounts) && liveStats.statusCounts.length > 0
      ? liveStats.statusCounts.map((row) => ({
          name: ORDER_STATUS_LABEL[row.name] || row.name,
          value: Number(row.value || 0),
        }))
      : [
          { name: '배송 완료 (DELIVERED)', value: 0 },
          { name: '결제 완료 (PAID)', value: 0 },
          { name: '환불 완료 (REFUNDED)', value: 0 },
        ];

  const handleDownloadExcel = () => {
    window.location.href = '/api/admin/download-excel';
  };

  const createProduct = async (payload) => {
    await fetch('/api/admin/products', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
    loadProducts();
  };

  const updateProduct = async (payload) => {
    if (!editingProduct?.id) return;
    await fetch(`/api/admin/products/${editingProduct.id}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(payload),
    });
    setEditingProduct(null);
    loadProducts();
  };

  const deleteProduct = async (id) => {
    if (!window.confirm('상품을 삭제할까요?')) return;
    await fetch(`/api/admin/products/${id}`, { method: 'DELETE', headers });
    loadProducts();
  };

  const updateOrderStatus = async (id, status, trackingNumber) => {
    const res = await fetch(`/api/admin/orders/${id}/status`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ status, trackingNumber }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      alert(data.message || '상태 변경 실패');
      return;
    }
    loadOrders();
  };

  const answerInquiry = async (id) => {
    const answer = answerDraft[id];
    const res = await fetch(`/api/admin/inquiries/${id}/answer`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ answer }),
    });
    if (res.ok) {
      setAnswerDraft((prev) => ({ ...prev, [id]: '' }));
      loadInquiries();
    }
  };

  const tabs = [
    { id: 'dashboard', label: '대시보드' },
    { id: 'products', label: '상품 관리' },
    { id: 'orders', label: '주문/배송' },
    { id: 'inquiries', label: '1:1 문의' },
  ];

  return (
    <div className="min-h-screen bg-gray-50 p-6 font-sans">
      <div className="mb-8 flex items-center justify-between border-b border-gray-200 pb-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-800 tracking-tight">👑 VibeCommerce 최고관리자 백오피스</h1>
          <div className="mt-1 text-sm text-gray-400 font-medium">실데이터 연동 운영 콘솔</div>
        </div>
        <button
          onClick={handleDownloadExcel}
          className="flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-emerald-700 active:scale-95"
        >
          💚 정산 원장 Excel 다운로드
        </button>
      </div>

      <div className="mb-6 flex flex-wrap gap-2">
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setTab(item.id)}
            className={`rounded-xl px-4 py-2 text-sm font-bold ${
              tab === item.id ? 'bg-blue-600 text-white' : 'bg-white text-gray-600 border'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === 'dashboard' && (
        <>
          <div className="mb-8 grid grid-cols-1 gap-5 sm:grid-cols-3">
            <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
              <p className="text-sm font-medium text-gray-400 uppercase tracking-wider">30일 누적 총매출액</p>
              <p className="mt-2 text-3xl font-extrabold text-gray-800">₩{totalSales.toLocaleString()}</p>
            </div>
            <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
              <p className="text-sm font-medium text-gray-400 uppercase tracking-wider">플랫폼 순이익 (수수료 수입)</p>
              <p className="mt-2 text-3xl font-extrabold text-emerald-600">₩{(totalSales - totalSettlement).toLocaleString()}</p>
            </div>
            <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
              <p className="text-sm font-medium text-gray-400 uppercase tracking-wider">파트너 정산 실지급액</p>
              <p className="mt-2 text-3xl font-extrabold text-blue-600">₩{totalSettlement.toLocaleString()}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm lg:col-span-2">
              <h2 className="mb-4 text-lg font-bold text-gray-700">📈 일별 매출 및 최종 정산 추이 트렌드</h2>
              <div className="w-full flex justify-center items-center" style={{ height: '320px' }}>
                <AreaChart width={720} height={300} data={data} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorSales" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3B82F6" stopOpacity={0.2}/>
                      <stop offset="95%" stopColor="#3B82F6" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                  <XAxis dataKey="date" stroke="#9CA3AF" tickLine={false} fontSize={12} />
                  <YAxis stroke="#9CA3AF" tickLine={false} fontSize={12} tickFormatter={(v) => `₩${v/10000}만`} />
                  <Tooltip formatter={(value) => `₩${Number(value || 0).toLocaleString()}`} />
                  <Area type="monotone" dataKey="sales" name="총매출액" stroke="#3B82F6" strokeWidth={2} fillOpacity={1} fill="url(#colorSales)" />
                  <Area type="monotone" dataKey="settlement" name="순정산액" stroke="#10B981" strokeWidth={2} fill="none" />
                </AreaChart>
              </div>
            </div>

            <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-bold text-gray-700">🎯 주문 상태 트랜잭션 비중</h2>
              <div className="flex flex-col items-center justify-center" style={{ height: '320px' }}>
                <PieChart width={240} height={200}>
                  <Pie data={pieData} cx="50%" cy="50%" innerRadius={55} outerRadius={75} paddingAngle={5} dataKey="value">
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </div>
            </div>

            <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm lg:col-span-3">
              <h2 className="mb-4 text-lg font-bold text-gray-700">📊 상품별 판매 통계 / 문의 현황</h2>
              <div className="grid gap-4 md:grid-cols-2">
                <div>
                  {(liveStats?.productSales || []).map((row) => (
                    <p key={row.name} className="text-sm text-gray-700">
                      {row.name}: {row.quantity}개 / ₩{Number(row.amount || 0).toLocaleString()}
                    </p>
                  ))}
                </div>
                <div>
                  {(liveStats?.inquiry || []).map((row) => (
                    <p key={row.status} className="text-sm text-gray-700">
                      {INQUIRY_STATUS_LABEL[row.status] || row.status}: {row.count}건
                    </p>
                  ))}
                </div>
              </div>
              <div className="mt-6 w-full flex justify-center items-center" style={{ height: '320px' }}>
                <BarChart width={1100} height={300} data={data} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                  <XAxis dataKey="date" stroke="#9CA3AF" tickLine={false} fontSize={12} />
                  <YAxis stroke="#9CA3AF" tickLine={false} fontSize={12} tickFormatter={(v) => `₩${v/10000}만`} />
                  <Tooltip formatter={(value) => `₩${Number(value || 0).toLocaleString()}`} />
                  <Legend />
                  <Bar dataKey="settlement" name="파트너 실지급액" stackId="a" fill="#3B82F6" />
                  <Bar dataKey="platformFee" name="플랫폼 수수료(10%)" stackId="a" fill="#10B981" />
                  <Bar dataKey="pgFee" name="PG 수수료(3.3%)" stackId="a" fill="#F59E0B" />
                </BarChart>
              </div>
            </div>
          </div>
        </>
      )}

      {tab === 'products' && (
        <div className="rounded-2xl bg-white p-6 shadow-sm">
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
          <div className="mt-6 space-y-2">
            {products.map((product) => (
              <div key={product.id} className="flex items-center justify-between rounded-xl border p-3">
                <p className="text-sm font-bold">{product.name} / ₩{Number(product.price || 0).toLocaleString()}</p>
                <div className="flex gap-2">
                  <button type="button" className="text-xs font-bold text-blue-600" onClick={() => setEditingProduct(product)}>수정</button>
                  <button type="button" className="text-xs font-bold text-red-500" onClick={() => deleteProduct(product.id)}>삭제</button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === 'orders' && (
        <div className="space-y-3">
          {orders.map((order) => {
            const status = order?.status || 'ORDERED';
            return (
              <div key={order.id} className="rounded-xl border bg-white p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-bold">{order.orderMerchantUid}</p>
                    <p className="text-xs text-gray-400">{ORDER_STATUS_LABEL[status] || status}</p>
                  </div>
                  <p className="font-extrabold text-blue-600">₩{Number(order.netAmount || 0).toLocaleString()}</p>
                </div>
                {(status === 'PREPARING' || status === 'PAID' || status === 'ORDERED') && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {status !== 'PREPARING' ? (
                      <button
                        type="button"
                        className="rounded-lg bg-gray-800 px-3 py-2 text-xs font-bold text-white"
                        onClick={() => updateOrderStatus(order.id, 'PREPARING')}
                      >
                        배송 준비
                      </button>
                    ) : null}
                    {status === 'PREPARING' ? (
                      <>
                        <input
                          value={trackingDraft[order.id] || order.trackingNumber || ''}
                          onChange={(e) =>
                            setTrackingDraft((prev) => ({ ...prev, [order.id]: e.target.value }))
                          }
                          placeholder="운송장 번호"
                          className="rounded-lg border px-3 py-2 text-xs"
                        />
                        <button
                          type="button"
                          className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-bold text-white"
                          onClick={() =>
                            updateOrderStatus(
                              order.id,
                              'SHIPPING',
                              trackingDraft[order.id] || order.trackingNumber
                            )
                          }
                        >
                          배송 시작
                        </button>
                      </>
                    ) : null}
                  </div>
                )}
                <div className="mt-3 flex gap-2">
                  <select
                    value={status}
                    onChange={(e) => updateOrderStatus(order.id, e.target.value, order.trackingNumber)}
                    className="rounded-lg border px-3 py-2 text-xs"
                  >
                    {['ORDERED', 'PAID', 'PREPARING', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURNED'].map((value) => (
                      <option key={value} value={value}>
                        {ORDER_STATUS_LABEL[value] || value}
                      </option>
                    ))}
                  </select>
                  {(status === 'RETURN_REQUESTED' || status === 'REFUND_REQUESTED') && (
                    <button
                      type="button"
                      className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-bold text-white"
                      onClick={() => updateOrderStatus(order.id, 'RETURNED')}
                    >
                      반품 승인
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {tab === 'inquiries' && (
        <div className="space-y-3">
          {inquiries.map((inquiry) => (
            <div key={inquiry.id} className="rounded-xl border bg-white p-4">
              <p className="text-xs text-gray-400">{inquiry.userKey}</p>
              <p className="font-bold">{inquiry.title}</p>
              <p className="mt-1 text-sm text-gray-600">{inquiry.content}</p>
              <p className="mt-1 text-xs">{INQUIRY_STATUS_LABEL[inquiry.status] || inquiry.status}</p>
              {inquiry.answer ? <p className="mt-2 text-sm text-blue-700">답변: {inquiry.answer}</p> : null}
              <div className="mt-2 flex gap-2">
                <input
                  value={answerDraft[inquiry.id] || ''}
                  onChange={(e) =>
                    setAnswerDraft((prev) => ({ ...prev, [inquiry.id]: e.target.value }))
                  }
                  placeholder="답변 입력"
                  className="flex-1 rounded-lg border px-3 py-2 text-sm"
                />
                <button
                  type="button"
                  className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-bold text-white"
                  onClick={() => answerInquiry(inquiry.id)}
                >
                  답변 등록
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
