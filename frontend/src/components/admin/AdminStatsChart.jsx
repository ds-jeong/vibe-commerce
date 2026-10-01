import React from 'react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  BarChart, Bar, Legend, PieChart, Pie, Cell
} from 'recharts';

const COLORS = ['#3B82F6', '#10B981', '#EF4444', '#F59E0B', '#8B5CF6'];

export default function AdminStatsChart({ data, totalSales, totalSettlement, pieData }) {
  const chartData = Array.isArray(data) ? data : [];

  return (
    <>
      <div className="mb-8 grid grid-cols-1 gap-5 sm:grid-cols-3">
        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <p className="text-sm font-medium uppercase tracking-wider text-gray-400">30일 누적 총매출액</p>
          <p className="mt-2 text-3xl font-extrabold text-gray-800">₩{totalSales.toLocaleString()}</p>
        </div>
        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <p className="text-sm font-medium uppercase tracking-wider text-gray-400">플랫폼 순이익 (수수료 수입)</p>
          <p className="mt-2 text-3xl font-extrabold text-emerald-600">
            ₩{(totalSales - totalSettlement).toLocaleString()}
          </p>
        </div>
        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <p className="text-sm font-medium uppercase tracking-wider text-gray-400">파트너 정산 실지급액</p>
          <p className="mt-2 text-3xl font-extrabold text-blue-600">₩{totalSettlement.toLocaleString()}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm lg:col-span-2">
          <h2 className="mb-4 text-lg font-bold text-gray-700">📈 일별 매출 및 최종 정산 추이 트렌드</h2>
          <div className="flex w-full items-center justify-center" style={{ height: '320px' }}>
            <AreaChart width={720} height={300} data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="colorSales" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3B82F6" stopOpacity={0.2} />
                  <stop offset="95%" stopColor="#3B82F6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
              <XAxis dataKey="date" stroke="#9CA3AF" tickLine={false} fontSize={12} />
              <YAxis stroke="#9CA3AF" tickLine={false} fontSize={12} tickFormatter={(v) => `₩${v / 10000}만`} />
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
                {(pieData || []).map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip />
            </PieChart>
          </div>
        </div>

        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm lg:col-span-3">
          <h2 className="mb-4 text-lg font-bold text-gray-700">📊 수수료·정산 구성</h2>
          <div className="flex w-full items-center justify-center" style={{ height: '320px' }}>
            <BarChart width={1100} height={300} data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
              <XAxis dataKey="date" stroke="#9CA3AF" tickLine={false} fontSize={12} />
              <YAxis stroke="#9CA3AF" tickLine={false} fontSize={12} tickFormatter={(v) => `₩${v / 10000}만`} />
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
  );
}
