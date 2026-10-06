import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AdminStatsChart from '../components/admin/AdminStatsChart';
import AdminProductDashboard from '../components/admin/AdminProductDashboard';
import AdminOrderDashboard from '../components/admin/AdminOrderDashboard';
import AdminInquiryDashboard from '../components/admin/AdminInquiryDashboard';
import AdminAnalyticsTab from '../components/admin/AdminAnalyticsTab';
import { ORDER_STATUS_LABEL } from '../utils/validation';
import { adminAuthFail, adminHeaders } from '../utils/adminApi';

export default function AdminDashboardPage() {
  const [tab, setTab] = useState('dashboard');
  const [grain, setGrain] = useState('daily');
  const [data, setData] = useState([]);
  const [liveStats, setLiveStats] = useState(null);

  const loadDashboard = useCallback(() => {
    const headers = adminHeaders();
    fetch(`/api/admin/analytics/sales-trend?grain=${grain}`, { headers })
      .then((res) => {
        if (adminAuthFail(res)) return [];
        return res.json();
      })
      .then((stats) => {
        if (Array.isArray(stats) && stats.length > 0) {
          setData(stats);
          return;
        }
        return fetch('/api/admin/dashboard-stats', { headers })
          .then((res) => (res.ok ? res.json() : []))
          .then((fallback) => setData(Array.isArray(fallback) ? fallback : []));
      })
      .catch(() => setData([]));

    fetch('/api/admin/dashboard/stats', { headers })
      .then((res) => (res.ok ? res.json() : {}))
      .then((stats) => setLiveStats(stats || {}))
      .catch(() => setLiveStats({}));
  }, [grain]);

  useEffect(() => {
    if (!localStorage.getItem('adminToken')) {
      window.location.replace('/admin/login');
    }
  }, []);

  useEffect(() => {
    if (!localStorage.getItem('adminToken')) {
      return;
    }
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

  const handleDownloadExcel = useCallback(async () => {
    const res = await fetch('/api/admin/download-excel', { headers: adminHeaders() });
    if (adminAuthFail(res)) {
      return;
    }
    if (!res.ok) {
      alert('정산 내역을 내려받지 못했습니다.');
      return;
    }
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `settlement_report_${new Date().toISOString().slice(0, 10)}.xlsx`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  }, []);

  const tabs = [
    { id: 'dashboard', label: '대시보드' },
    { id: 'analytics', label: '판매/문의 통계' },
    { id: 'products', label: '상품 관리' },
    { id: 'orders', label: '주문/배송' },
    { id: 'inquiries', label: '1:1 문의' },
  ];

  return (
    <div className="min-h-screen bg-slate-50 p-6 font-sans text-[#0A192F]">
      <div className="mb-8 flex items-center justify-between border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-black">
            VibeCommerce 관리자
          </h1>
          <div className="mt-1 text-sm font-medium text-slate-400">매출·주문 운영 콘솔</div>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              window.location.href = '/';
            }}
            className="rounded-md border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition hover:scale-[1.01] hover:bg-slate-50"
          >
            쇼핑몰
          </button>
          <button
          onClick={handleDownloadExcel}
          className="flex items-center gap-2 rounded-md bg-[#0A192F] px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
        >
          정산 내역 다운로드
        </button>
        </div>
      </div>

      <div className="mb-6 flex flex-wrap gap-2">
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setTab(item.id)}
            className={`rounded-md px-4 py-2 text-sm font-semibold transition hover:scale-[1.01] ${
              tab === item.id ? 'bg-[#0A192F] text-white' : 'border border-slate-200 bg-white text-slate-600'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === 'dashboard' && (
        <>
          <div className="mb-4 flex gap-2">
            <button
              type="button"
              onClick={() => setGrain('daily')}
              className={`rounded-lg px-3 py-1.5 text-xs font-bold ${
                grain === 'daily' ? 'bg-[#0A192F] text-white' : 'border bg-white text-slate-600'
              }`}
            >
              일별
            </button>
            <button
              type="button"
              onClick={() => setGrain('monthly')}
              className={`rounded-lg px-3 py-1.5 text-xs font-bold ${
                grain === 'monthly' ? 'bg-[#0A192F] text-white' : 'border bg-white text-slate-600'
              }`}
            >
              월별
            </button>
          </div>
          <AdminStatsChart
            data={data}
            totalSales={totalSales}
            totalSettlement={totalSettlement}
            pieData={pieData}
          />
        </>
      )}
      {tab === 'analytics' && <AdminAnalyticsTab />}
      {tab === 'products' && <AdminProductDashboard />}
      {tab === 'orders' && <AdminOrderDashboard />}
      {tab === 'inquiries' && <AdminInquiryDashboard />}
    </div>
  );
}
