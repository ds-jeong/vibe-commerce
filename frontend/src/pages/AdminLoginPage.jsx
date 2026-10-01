import React, { useState } from 'react';

export default function AdminLoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const handleLogin = (e) => {
    e.preventDefault();
    setError('');

    // 🔓 백엔드 시큐리티 개방 채널 수동 로그인 POST API 동적 호출
    fetch('/api/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error('자격 증명에 실패했습니다. 계정 정보를 확인하세요.');
        }
        return res.json();
      })
      .then((resData) => {
        if (resData.status === 'SUCCESS' && resData.accessToken) {
          // ✨ 핵심 인프라: 발급된 JWT 인증 토큰을 브라우저 스토리지에 세션 영속 보관!
          localStorage.setItem('adminToken', resData.accessToken);
          // 대시보드로 통과 리다이렉트
          window.location.href = '/admin';
        }
      })
      .catch((err) => {
        setError(err.message || '로그인 연동 중 장애가 발생했습니다.');
      });
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100 font-sans px-4">
      <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 shadow-xl">
        <div className="text-center mb-6">
          <span className="text-3xl">👑</span>
          <h2 className="mt-2 text-2xl font-extrabold text-gray-800 tracking-tight">VibeCommerce 관리자 로그인</h2>
          <p className="mt-1 text-xs text-gray-400">최고관리자 백오피스 세션 자격 권한 증명</p>
        </div>

        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label className="text-xs font-bold text-gray-500 uppercase tracking-wider block mb-1">관리자 ID</label>
            <input 
              type="text" 
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-500 focus:bg-white focus:outline-none transition"
              placeholder="admin"
            />
          </div>

          <div>
            <label className="text-xs font-bold text-gray-500 uppercase tracking-wider block mb-1">비밀번호</label>
            <input 
              type="password" 
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-500 focus:bg-white focus:outline-none transition"
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
            className="w-full rounded-xl bg-gray-900 py-3.5 text-sm font-bold text-white shadow-md hover:bg-gray-800 transition active:scale-95"
          >
            보안 세션 로그인 가동
          </button>
        </form>
      </div>
    </div>
  );
}
