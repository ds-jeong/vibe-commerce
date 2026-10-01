import React, { useEffect, useState } from 'react';
import { 
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  BarChart, Bar, Legend, PieChart, Pie, Cell
} from 'recharts';

export default function AdminDashboardPage() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalSales, setTotalSales] = useState(0);
  const [totalSettlement, setTotalSettlement] = useState(0);

  // 1. 백엔드 배치 요약 API 데이터 연동 파이프라인
  useEffect(() => {
    // 💾 로컬 스토리지에 안착된 암호화 전표 수급
    const token = localStorage.getItem('adminToken');

    fetch('/api/admin/dashboard-stats', {
      method: 'GET',
      headers: {
        // ✨ 중요: 백엔드 JwtAuthenticationFilter 가 검문할 헤더 전표 레이어 수동 주입 바인딩!
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })
      .then((res) => {
        if (res.status === 403 || res.status === 401) {
          // 토큰이 없거나 무단 탈취 침입 시 로그인 창으로 강제 리다이렉션 튕구기 격리 조치
          alert('최고관리자 보안 권한 전표가 없거나 만료되었습니다.');
          window.location.href = '/admin/login';
          return;
        }
        return res.json();
      })
      .then((stats) => {
        if (!stats) return;
        setData(stats);
        
        let salesSum = 0;
        let settlementSum = 0;
        stats.forEach(d => {
          if (d.sales) salesSum += Number(d.sales);
          if (d.settlement) settlementSum += Number(d.settlement);
        });

        setTotalSales(salesSum);
        setTotalSettlement(settlementSum);
        setLoading(false);
      })
      .catch((err) => console.error("대시보드 API 통신 장애: ", err));
  }, []);

  // 2. ✨ [신설] 백엔드 엑셀 스트리밍 다운로드 트리거 함수
  const handleDownloadExcel = () => {
    // 브라우저가 바이너리 파일 세션을 강제로 다운로드 팝업으로 인식하게 유도하는 정석 리다이렉트
    window.location.href = '/api/admin/download-excel';
  };

  if (loading || data.length === 0) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50 text-gray-500 font-semibold text-lg tracking-tight">
        📊 백엔드 최고관리자 정산 장부 파이프라인 동기화 중...
      </div>
    );
  }

  const pieData = [
    { name: '배송 완료 (DELIVERED)', value: 80 },
    { name: '결제 완료 (PAID)', value: 10 },
    { name: '환불 완료 (REFUNDED)', value: 10 },
  ];
  const COLORS = ['#3B82F6', '#10B981', '#EF4444'];

  return (
    <div className="min-h-screen bg-gray-50 p-6 font-sans">
      {/* 상단 GNB 레이아웃 */}
      <div className="mb-8 flex items-center justify-between border-b border-gray-200 pb-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-800 tracking-tight">👑 VibeCommerce 최고관리자 백오피스</h1>
          <div className="mt-1 text-sm text-gray-400 font-medium">데이터 기준일자: 2026년 9월 30일 현재</div>
        </div>
        
        {/* 📊 [신설 추가] 최고관리자 증빙용 정산서 실물 엑셀 다운로드 수동 버튼 */}
        <button 
          onClick={handleDownloadExcel}
          className="flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-emerald-700 active:scale-95"
        >
          💚 정산 원장 Excel 다운로드
        </button>
      </div>

      {/* 🎰 3대 핵심 지표 요약 카드 컴포넌트 레이아웃 */}
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

      {/* 메인 차트 그리드 레이아웃 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        
        {/* 그래프 1: [Area Chart] */}
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
              <Tooltip formatter={(value) => `₩${value.toLocaleString()}`} />
              <Area type="monotone" dataKey="sales" name="총매출액" stroke="#3B82F6" strokeWidth={2} fillOpacity={1} fill="url(#colorSales)" />
              <Area type="monotone" dataKey="settlement" name="순정산액" stroke="#10B981" strokeWidth={2} fill="none" />
            </AreaChart>
          </div>
        </div>

        {/* 그래프 2: [Donut Chart] */}
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
            <div className="mt-2 grid grid-cols-3 gap-2 text-xs font-semibold">
              {pieData.map((entry, index) => (
                <div key={entry.name} className="flex flex-col items-center">
                  <span className="h-3 w-3 rounded-full" style={{ backgroundColor: COLORS[index] }}></span>
                  <span className="mt-1 text-gray-500 text-center">{entry.name.split(' ')}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 그래프 3: [Stacked Bar Chart] */}
        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm lg:col-span-3">
          <h2 className="mb-4 text-lg font-bold text-gray-700">📊 일별 총매출 대비 PG(3.3%) 및 플랫폼(10%) 수수료 장부 대조</h2>
          <div className="w-full flex justify-center items-center" style={{ height: '320px' }}>
            <BarChart width={1100} height={300} data={data} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
              <XAxis dataKey="date" stroke="#9CA3AF" tickLine={false} fontSize={12} />
              <YAxis stroke="#9CA3AF" tickLine={false} fontSize={12} tickFormatter={(v) => `₩${v/10000}만`} />
              <Tooltip formatter={(value) => `₩${value.toLocaleString()}`} />
              <Legend />
              <Bar dataKey="settlement" name="파트너 실지급액" stackId="a" fill="#3B82F6" />
              <Bar dataKey="platformFee" name="플랫폼 수수료(10%)" stackId="a" fill="#10B981" />
              <Bar dataKey="pgFee" name="PG 수수료(3.3%)" stackId="a" fill="#F59E0B" />
            </BarChart>
          </div>
        </div>

      </div>
    </div>
  );
}
