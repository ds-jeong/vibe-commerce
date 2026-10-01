import React, { useEffect, useState } from 'react';

const STATUS_LABEL = {
  ORDERED: '주문완료',
  PAID: '결제완료',
  DELIVERING: '배송중',
  DELIVERED: '배송완료',
  CANCELLED: '취소',
  REFUND_REQUESTED: '환불요청',
  REFUNDED: '환불완료',
  PENDING: '대기',
  COMPLETED: '완료',
};

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

  const orderProductLabel = (order) => {
    const items = order.orderItems || [];
    if (items.length === 0) {
      return '상품 정보 없음';
    }
    const firstName = items[0].product?.name || '상품';
    const qty = items.reduce((sum, item) => sum + Number(item.count || 0), 0);
    if (items.length === 1) {
      return `${firstName} / ${qty}개`;
    }
    return `${firstName} 외 ${items.length - 1}건 / ${qty}개`;
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
              <div className="overflow-x-auto">
                <table className="min-w-full text-left text-sm">
                  <thead className="border-b border-gray-100 text-xs uppercase text-gray-400">
                    <tr>
                      <th className="px-3 py-3">주문일자</th>
                      <th className="px-3 py-3">주문번호</th>
                      <th className="px-3 py-3">상품 / 수량</th>
                      <th className="px-3 py-3">결제금액</th>
                      <th className="px-3 py-3">상태</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orders.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-3 py-16 text-center text-gray-400">
                          주문 내역이 없습니다.
                        </td>
                      </tr>
                    ) : (
                      orders.map((order) => (
                        <tr key={order.id} className="border-b border-gray-50">
                          <td className="px-3 py-3 text-gray-600">
                            {formatDate(order.orderDate)}
                          </td>
                          <td className="px-3 py-3 font-bold text-gray-800">
                            {order.orderMerchantUid}
                          </td>
                          <td className="px-3 py-3 text-gray-700">
                            {orderProductLabel(order)}
                          </td>
                          <td className="px-3 py-3 font-extrabold text-blue-600">
                            {formatPrice(order.netAmount)}원
                          </td>
                          <td className="px-3 py-3">
                            {STATUS_LABEL[order.status] || order.status}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            )}

            {activeTab === 'profile' && (
              <div className="grid gap-8 lg:grid-cols-2">
                <form onSubmit={handleProfileSave} className="space-y-3">
                  <h3 className="text-lg font-extrabold text-gray-800">기본 정보</h3>
                  <input
                    readOnly
                    value={profile.userKey}
                    className="w-full rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm"
                  />
                  <input
                    required
                    value={profile.name}
                    onChange={(e) =>
                      setProfile((prev) => ({ ...prev, name: e.target.value }))
                    }
                    placeholder="이름"
                    className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
                  />
                  <input
                    type="email"
                    value={profile.email}
                    onChange={(e) =>
                      setProfile((prev) => ({ ...prev, email: e.target.value }))
                    }
                    placeholder="이메일"
                    className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
                  />
                  <input
                    value={profile.phoneNumber}
                    onChange={(e) =>
                      setProfile((prev) => ({
                        ...prev,
                        phoneNumber: e.target.value,
                      }))
                    }
                    placeholder="전화번호"
                    className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
                  />
                  <button
                    type="button"
                    onClick={handleAddressSearch}
                    className="w-full rounded-xl bg-gray-800 py-3 text-sm font-bold text-white"
                  >
                    주소 찾기
                  </button>
                  <div className="grid grid-cols-3 gap-2">
                    <input
                      readOnly
                      value={profile.zipcode}
                      placeholder="우편번호"
                      className="rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm"
                    />
                    <input
                      readOnly
                      value={profile.roadAddress}
                      placeholder="도로명주소"
                      className="col-span-2 rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm"
                    />
                  </div>
                  <input
                    value={profile.detailAddress}
                    onChange={(e) =>
                      setProfile((prev) => ({
                        ...prev,
                        detailAddress: e.target.value,
                      }))
                    }
                    placeholder="상세주소"
                    className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
                  />
                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full rounded-xl bg-blue-600 py-3 text-sm font-bold text-white"
                  >
                    정보 저장
                  </button>
                </form>

                <form onSubmit={handlePasswordSave} className="space-y-3">
                  <h3 className="text-lg font-extrabold text-gray-800">비밀번호 변경</h3>
                  <input
                    type="password"
                    required
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="현재 비밀번호"
                    className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
                  />
                  <input
                    type="password"
                    required
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="새 비밀번호"
                    className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
                  />
                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full rounded-xl border border-gray-200 py-3 text-sm font-bold text-gray-700"
                  >
                    비밀번호 변경
                  </button>
                </form>
              </div>
            )}

            {activeTab === 'inquiry' && (
              <div>
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="text-lg font-extrabold text-gray-800">1:1 문의</h3>
                  <button
                    type="button"
                    onClick={() => setShowInquiryForm((prev) => !prev)}
                    className="rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white"
                  >
                    1:1 문의하기
                  </button>
                </div>

                {showInquiryForm && (
                  <form onSubmit={handleInquirySubmit} className="mb-6 space-y-3 rounded-xl bg-gray-50 p-4">
                    <input
                      required
                      value={inquiryTitle}
                      onChange={(e) => setInquiryTitle(e.target.value)}
                      placeholder="제목"
                      className="w-full rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm"
                    />
                    <textarea
                      required
                      rows={5}
                      value={inquiryContent}
                      onChange={(e) => setInquiryContent(e.target.value)}
                      placeholder="문의 내용"
                      className="w-full rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm"
                    />
                    <button
                      type="submit"
                      disabled={loading}
                      className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white"
                    >
                      등록
                    </button>
                  </form>
                )}

                <div className="space-y-3">
                  {inquiries.length === 0 ? (
                    <p className="py-10 text-center text-sm text-gray-400">
                      등록된 문의가 없습니다.
                    </p>
                  ) : (
                    inquiries.map((inquiry) => (
                      <div
                        key={inquiry.id}
                        className="rounded-xl border border-gray-100 p-4"
                      >
                        <div className="flex items-center justify-between gap-3">
                          <p className="font-bold text-gray-800">{inquiry.title}</p>
                          <span className="rounded-full bg-gray-100 px-3 py-1 text-[11px] font-bold text-gray-600">
                            {STATUS_LABEL[inquiry.status] || inquiry.status}
                          </span>
                        </div>
                        <p className="mt-1 text-xs text-gray-400">
                          {formatDate(inquiry.createdAt)}
                        </p>
                        <p className="mt-2 text-sm text-gray-600">{inquiry.content}</p>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}
