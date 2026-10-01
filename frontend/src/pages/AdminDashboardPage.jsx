import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AdminStatsChart from '../components/admin/AdminStatsChart';
import AdminProductDashboard from '../components/admin/AdminProductDashboard';
import AdminOrderDashboard from '../components/admin/AdminOrderDashboard';
import AdminInquiryDashboard from '../components/admin/AdminInquiryDashboard';
import { ORDER_STATUS_LABEL } from '../utils/validation';
import { adminAuthFail, adminHeaders } from '../utils/adminApi';

export default function AdminDashboardPage() {
  const [tab, setTab] = useState('dashboard');
  const [data, setData] = useState([]);
  const [liveStats, setLiveStats] = useState(null);

  const loadDashboard = useCallback(() => {
    const headers = adminHeaders();
    fetch('/api/admin/dashboard-stats', { headers })
      .then((res) => {
        if (adminAuthFail(res)) return [];
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
  }, []);

  useEffect(() => {
    if (tab === 'dashboard') {
      loadDashboard();
    }
  }, [tab, loadDashboard]);

  const totalSales = useMemo(
    () =>
      (Array.isArray(data) ? data : []).reduce(
        (sum, d) => sum + Number(d?.sales || 0),
        0
      ),
    [data]
  );
  const totalSettlement = useMemo(
    () =>
      (Array.isArray(data) ? data : []).reduce(
        (sum, d) => sum + Number(d?.settlement || 0),
        0
      ),
    [data]
  );

  const pieData = useMemo(
    () =>
      Array.isArray(liveStats?.statusCounts) && liveStats.statusCounts.length > 0
        ? liveStats.statusCounts.map((row) => ({
            name: ORDER_STATUS_LABEL[row.name] || row.name,
            value: Number(row.value || 0),
          }))
        : [
            { name: '배송 완료 (DELIVERED)', value: 0 },
            { name: '결제 완료 (PAID)', value: 0 },
            { name: '환불 완료 (REFUNDED)', value: 0 },
          ],
    [liveStats]
  );

  const handleDownloadExcel = useCallback(() => {
    window.location.href = '/api/admin/download-excel';
  }, []);

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
          <h1 className="text-2xl font-bold tracking-tight text-gray-800">
            👑 VibeCommerce 최고관리자 백오피스
          </h1>
          <div className="mt-1 text-sm font-medium text-gray-400">실데이터 연동 운영 콘솔</div>
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
              tab === item.id ? 'bg-blue-600 text-white' : 'border bg-white text-gray-600'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === 'dashboard' && (
        <AdminStatsChart
          data={data}
          liveStats={liveStats}
          totalSales={totalSales}
          totalSettlement={totalSettlement}
          pieData={pieData}
        />
      )}
      {tab === 'products' && <AdminProductDashboard />}
      {tab === 'orders' && <AdminOrderDashboard />}
      {tab === 'inquiries' && <AdminInquiryDashboard />}
    </div>
  );
}
