import React, { useMemo, useState } from 'react';
import { getUserToken, startAdminSession } from '../utils/session';

export default function AdminLoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const memberLoggedIn = useMemo(() => Boolean(getUserToken()), []);

  const handleLogin = (e) => {
    e.preventDefault();
    setError('');

    fetch('/api/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error('아이디 또는 비밀번호가 올바르지 않습니다.');
        }
        return res.json();
      })
      .then(async (resData) => {
        if (resData.status === 'SUCCESS' && resData.accessToken) {
          await startAdminSession(resData.accessToken);
          window.location.href = '/admin';
        }
      })
      .catch((err) => {
        setError(err.message || '로그인에 실패했습니다. 다시 시도해 주세요.');
      });
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100 font-sans px-4">
      <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 shadow-xl">
        <div className="text-center mb-6">
          <h2 className="mt-2 text-2xl font-semibold text-black tracking-tight">관리자 로그인</h2>
          <p className="mt-1 text-xs text-slate-400">관리자 계정으로 로그인해 주세요.</p>
          <p className="mt-3 text-sm font-semibold text-red-500">
            테스트 계정 ID: admin / PW: admin1234
          </p>
          {memberLoggedIn ? (
            <p className="mt-2 text-xs font-medium text-red-500">
              회원으로 로그인된 상태입니다. 관리자 로그인 시 회원 세션은 종료됩니다.
            </p>
          ) : null}
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
            className="w-full rounded-md bg-[#0A192F] py-3.5 text-sm font-semibold text-white shadow-sm hover:bg-[#1E293B] transition hover:scale-[1.01]"
          >
            {memberLoggedIn ? '회원 로그아웃 후 관리자 로그인' : '로그인'}
          </button>
          <button
            type="button"
            onClick={() => {
              window.location.href = '/';
            }}
            className="w-full py-2 text-xs font-semibold text-slate-400 transition hover:text-black"
          >
            쇼핑몰로 돌아가기
          </button>
        </form>
      </div>
    </div>
  );
}
