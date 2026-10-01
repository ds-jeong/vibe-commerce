import React, { useEffect, useState } from 'react';
import OrderHistory from '../components/mypage/OrderHistory';
import ProfileEdit from '../components/mypage/ProfileEdit';
import InquiryPanel from '../components/mypage/InquiryPanel';

export default function MyPage() {
  const token = localStorage.getItem('userToken');
  const [activeTab, setActiveTab] = useState('orders');
  const [orders, setOrders] = useState([]);
  const [inquiries, setInquiries] = useState([]);
  const [profile, setProfile] = useState({
    userKey: '',
    name: '',
    email: '',
    phoneNumber: '',
    zipcode: '',
    roadAddress: '',
    detailAddress: '',
  });
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [inquiryTitle, setInquiryTitle] = useState('');
  const [inquiryContent, setInquiryContent] = useState('');
  const [showInquiryForm, setShowInquiryForm] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!token) {
      window.location.href = '/login';
    }
  }, [token]);

  useEffect(() => {
    const scriptId = 'daum-postcode-script';
    if (document.getElementById(scriptId)) {
      return;
    }
    const script = document.createElement('script');
    script.id = scriptId;
    script.src =
      'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
    script.async = true;
    document.body.appendChild(script);
  }, []);

  const authHeaders = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  const loadOrders = async () => {
    const res = await fetch('/api/orders/my', { headers: authHeaders });
    const data = await res.json().catch(() => []);
    setOrders(Array.isArray(data) ? data : []);
  };

  const loadProfile = async () => {
    const res = await fetch('/api/user/profile', { headers: authHeaders });
    const data = await res.json().catch(() => ({}));
    if (res.ok) {
      setProfile({
        userKey: data.userKey || '',
        name: data.name || '',
        email: data.email || '',
        phoneNumber: data.phoneNumber || '',
        zipcode: data.zipcode || '',
        roadAddress: data.roadAddress || '',
        detailAddress: data.detailAddress || '',
      });
    }
  };

  const loadInquiries = async () => {
    const res = await fetch('/api/inquiries', { headers: authHeaders });
    const data = await res.json().catch(() => []);
    setInquiries(Array.isArray(data) ? data : []);
  };

  useEffect(() => {
    if (!token) {
      return;
    }
    loadOrders();
    loadProfile();
    loadInquiries();
  }, [token]);

  const formatPrice = (price) =>
    Number(price || 0).toLocaleString('ko-KR');

  const formatDate = (value) => {
    if (!value) {
      return '-';
    }
    return String(value).replace('T', ' ').slice(0, 16);
  };

  const handleProfileSave = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/user/profile', {
        method: 'PUT',
        headers: authHeaders,
        body: JSON.stringify({
          name: profile.name,
          email: profile.email,
          phoneNumber: profile.phoneNumber,
          zipcode: profile.zipcode,
          roadAddress: profile.roadAddress,
          detailAddress: profile.detailAddress,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.message || '정보 수정에 실패했습니다.');
      }
      setMessage(data.message || '회원 정보가 수정되었습니다.');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handlePasswordSave = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/user/profile/password', {
        method: 'PUT',
        headers: authHeaders,
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.message || '비밀번호 변경에 실패했습니다.');
      }
      setCurrentPassword('');
      setNewPassword('');
      setMessage(data.message || '비밀번호가 변경되었습니다.');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleInquirySubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await fetch('/api/inquiries', {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({
          title: inquiryTitle,
          content: inquiryContent,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.message || '문의 등록에 실패했습니다.');
      }
      setInquiryTitle('');
      setInquiryContent('');
      setShowInquiryForm(false);
      setMessage('문의가 등록되었습니다.');
      await loadInquiries();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async (orderId) => {
    if (!window.confirm('주문을 즉시 취소할까요?')) {
      return;
    }
    const res = await fetch(`/api/orders/${orderId}/cancel`, {
      method: 'POST',
      headers: authHeaders,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      setError(data.message || '주문 취소에 실패했습니다.');
      return;
    }
    setMessage('주문이 취소되었습니다.');
    loadOrders();
  };

  const handleReturn = async (orderId) => {
    if (!window.confirm('반품을 신청할까요?')) {
      return;
    }
    const res = await fetch(`/api/orders/${orderId}/return-request`, {
      method: 'POST',
      headers: authHeaders,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      setError(data.message || '반품 신청에 실패했습니다.');
      return;
    }
    setMessage('반품이 신청되었습니다.');
    loadOrders();
  };

  const handleAddressSearch = () => {
    if (!window.daum || !window.daum.Postcode) {
      setError('주소 검색 서비스를 불러오는 중입니다.');
      return;
    }
    new window.daum.Postcode({
      oncomplete: function (data) {
        setProfile((prev) => ({
          ...prev,
          zipcode: data.zonecode,
          roadAddress: data.roadAddress || data.jibunAddress || '',
        }));
      },
    }).open();
  };

  const tabs = [
    { id: 'orders', label: '🛍️ 주문내역' },
    { id: 'profile', label: '⚙️ 내 정보 수정' },
    { id: 'inquiry', label: '💬 문의사항' },
  ];

  if (!token) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gray-50 font-sans">
      <header className="sticky top-0 z-40 border-b border-gray-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4">
          <div className="cursor-pointer" onClick={() => (window.location.href = '/')}>
            <h1 className="text-xl font-extrabold tracking-tight text-gray-800">
              🛍️ VibeCommerce
            </h1>
            <p className="mt-0.5 text-[10px] text-gray-400">My Page</p>
          </div>
          <button
            type="button"
            onClick={() => (window.location.href = '/')}
            className="rounded-xl px-3 py-2 text-xs font-bold text-gray-500 hover:bg-gray-100"
          >
            쇼핑 계속하기
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8">
        <h2 className="text-3xl font-extrabold tracking-tight text-gray-900">
          마이페이지
        </h2>
        <p className="mt-2 text-sm text-gray-500">
          주문내역, 회원정보, 1:1 문의를 한곳에서 관리하세요.
        </p>

        <div className="mt-8 grid gap-6 lg:grid-cols-[220px_1fr]">
          <aside className="h-fit rounded-2xl border border-gray-200 bg-white p-3 shadow-sm">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => {
                  setActiveTab(tab.id);
                  setError('');
                  setMessage('');
                }}
                className={`mb-1 w-full rounded-xl px-4 py-3 text-left text-sm font-bold transition ${
                  activeTab === tab.id
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </aside>

          <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
            {message && (
              <div className="mb-4 rounded-xl border border-emerald-100 bg-emerald-50 p-3 text-xs font-bold text-emerald-700">
                {message}
              </div>
            )}
            {error && (
              <div className="mb-4 rounded-xl border border-red-100 bg-red-50 p-3 text-xs font-bold text-red-500">
                ⚠️ {error}
              </div>
            )}

            {activeTab === 'orders' && (
              <OrderHistory
                orders={orders}
                formatDate={formatDate}
                formatPrice={formatPrice}
                onCancel={handleCancel}
                onReturn={handleReturn}
              />
            )}

            {activeTab === 'profile' && (
              <ProfileEdit
                profile={profile}
                setProfile={setProfile}
                currentPassword={currentPassword}
                setCurrentPassword={setCurrentPassword}
                newPassword={newPassword}
                setNewPassword={setNewPassword}
                loading={loading}
                onProfileSave={handleProfileSave}
                onPasswordSave={handlePasswordSave}
                onAddressSearch={handleAddressSearch}
              />
            )}

            {activeTab === 'inquiry' && (
              <InquiryPanel
                inquiries={inquiries}
                showInquiryForm={showInquiryForm}
                setShowInquiryForm={setShowInquiryForm}
                inquiryTitle={inquiryTitle}
                setInquiryTitle={setInquiryTitle}
                inquiryContent={inquiryContent}
                setInquiryContent={setInquiryContent}
                loading={loading}
                onSubmit={handleInquirySubmit}
                formatDate={formatDate}
              />
            )}
          </section>
        </div>
      </main>
    </div>
  );
}
