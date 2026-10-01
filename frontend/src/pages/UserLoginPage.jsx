import React, { useState } from 'react';

export default function UserLoginPage() {
  const [activeTab, setActiveTab] = useState('member');
  const [userKey, setUserKey] = useState('');
  const [password, setPassword] = useState('');
  const [guestName, setGuestName] = useState('');
  const [guestPhone, setGuestPhone] = useState('');
  const [guestOrder, setGuestOrder] = useState(null);
  const [error, setError] = useState('');

  const handleUserLogin = (e) => {
    e.preventDefault();
    setError('');

    fetch('/api/user/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userKey, password })
    })
      .then((res) => {
        if (!res.ok) throw new Error('데이터베이스 원장 대조 결과 자격 증명 정보가 일치하지 않습니다.');
        return res.json();
      })
      .then((resData) => {
        if (resData.status === 'SUCCESS' && resData.accessToken) {
          localStorage.setItem('userToken', resData.accessToken);

          const guestCart = localStorage.getItem('guestCart');
          if (guestCart) {
            fetch('/api/cart/merge', {
              method: 'POST',
              headers: {
                'Authorization': `Bearer ${resData.accessToken}`,
                'Content-Type': 'application/json'
              },
              body: guestCart
            })
            .then(mergeRes => mergeRes.json())
            .then(mergeData => {
              if(mergeData.status === 'SUCCESS') {
                alert(`🔄 [물리 DB 마이그레이션 최종 완료]\n\n비회원 임시 상품 리스트 원장 데이터가 네트워크 세션을 타고 Docker PostgreSQL 데이터베이스 'cart_items' 테이블 원장에 100% 실물 영구 저장 완료되었습니다!`);
                localStorage.removeItem('guestCart');
                window.location.href = '/';
              }
            })
            .catch(err => console.error("DB 이관 통신 오류: ", err));
          } else {
            window.location.href = '/';
          }
        }
      })
      .catch((err) => {
        setError(err.message || '소비자 로그인 연동 중 장애 발생');
      });
  };

  const handleGuestLookup = (e) => {
    e.preventDefault();
    setError('');
    setGuestOrder(null);

    fetch('/api/orders/non-user/lookup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ordererName: guestName.trim(),
        phoneNumber: guestPhone.replace(/\D/g, ''),
      }),
    })
      .then(async (res) => {
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(data.message || '일치하는 비회원 주문을 찾을 수 없습니다.');
        }
        return data;
      })
      .then((order) => {
        setGuestOrder(order);
      })
      .catch((err) => {
        setError(err.message || '비회원 주문 조회 중 오류가 발생했습니다.');
      });
  };

  const formatPrice = (price) => Number(price || 0).toLocaleString('ko-KR');

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100 font-sans px-4">
      <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 shadow-xl">
        <div className="text-center mb-6">
          <span className="text-3xl">🛍️</span>
          <h2 className="mt-2 text-2xl font-extrabold text-gray-800 tracking-tight">VibeCommerce</h2>
          <p className="mt-1 text-xs text-gray-400">회원 로그인 또는 비회원 주문조회</p>
        </div>

        <div className="mb-6 grid grid-cols-2 rounded-xl bg-gray-100 p-1">
          <button
            type="button"
            onClick={() => { setActiveTab('member'); setError(''); }}
            className={`rounded-lg py-2 text-xs font-bold ${activeTab === 'member' ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-500'}`}
          >
            회원 로그인
          </button>
          <button
            type="button"
            onClick={() => { setActiveTab('guest'); setError(''); }}
            className={`rounded-lg py-2 text-xs font-bold ${activeTab === 'guest' ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-500'}`}
          >
            비회원 주문조회
          </button>
        </div>

        {activeTab === 'member' ? (
        <form onSubmit={handleUserLogin} className="space-y-4">
          <div>
            <label className="text-xs font-bold text-gray-500 uppercase tracking-wider block mb-1">회원 ID (userKey)</label>
            <input 
              type="text" 
              required
              value={userKey}
              onChange={(e) => setUserKey(e.target.value)}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
              placeholder="가입한 아이디 입력"
            />
          </div>

          <div>
            <label className="text-xs font-bold text-gray-500 uppercase tracking-wider block mb-1">비밀번호</label>
            <input 
              type="password" 
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
              placeholder="••••••••"
            />
          </div>

          {error && (
            <div className="rounded-xl bg-red-50 p-3 text-xs font-bold text-red-500 border border-red-100">
              ⚠️ {error}
            </div>
          )}

          <button 
            type="submit"
            className="w-full rounded-xl bg-blue-600 py-3.5 text-sm font-bold text-white shadow-md hover:bg-blue-700 transition active:scale-95"
          >
            회원 로그인 및 쇼핑 계속하기
          </button>
          
          <div className="text-center mt-4">
            <a href="/signup" className="text-xs font-semibold text-gray-400 hover:text-blue-600 transition">
              처음 오셨나요? VibeCommerce 회원가입하기
            </a>
          </div>
        </form>
        ) : (
        <form onSubmit={handleGuestLookup} className="space-y-4">
          <div>
            <label className="text-xs font-bold text-gray-500 uppercase tracking-wider block mb-1">주문자 이름</label>
            <input
              type="text"
              required
              value={guestName}
              onChange={(e) => setGuestName(e.target.value)}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
              placeholder="주문 시 입력한 이름"
            />
          </div>
          <div>
            <label className="text-xs font-bold text-gray-500 uppercase tracking-wider block mb-1">휴대폰 번호</label>
            <input
              type="text"
              required
              value={guestPhone}
              onChange={(e) => setGuestPhone(e.target.value.replace(/\D/g, ''))}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
              placeholder="01012345678"
              maxLength={11}
            />
          </div>

          {error && (
            <div className="rounded-xl bg-red-50 p-3 text-xs font-bold text-red-500 border border-red-100">
              ⚠️ {error}
            </div>
          )}

          <button
            type="submit"
            className="w-full rounded-xl bg-gray-800 py-3.5 text-sm font-bold text-white shadow-md hover:bg-gray-900 transition active:scale-95"
          >
            최신 주문 조회
          </button>

          {guestOrder && (
            <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 text-sm">
              <p className="font-bold text-gray-800">주문번호 {guestOrder.orderMerchantUid}</p>
              <p className="mt-1 text-xs text-gray-500">{String(guestOrder.orderDate || '').replace('T', ' ').slice(0, 16)}</p>
              <p className="mt-2 font-extrabold text-blue-600">{formatPrice(guestOrder.netAmount)}원</p>
              <p className="mt-1 text-xs text-gray-600">상태: {guestOrder.status}</p>
            </div>
          )}

          <div className="text-center mt-4">
            <a href="/" className="text-xs font-semibold text-gray-400 hover:text-blue-600 transition">
              쇼핑하러 가기
            </a>
          </div>
        </form>
        )}
      </div>
    </div>
  );
}
