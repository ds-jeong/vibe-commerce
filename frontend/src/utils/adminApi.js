export function adminHeaders() {
  const token = localStorage.getItem('adminToken');
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export function unwrapPage(payload) {
  if (Array.isArray(payload)) {
    return {
      rows: payload,
      totalPages: payload.length > 0 ? 1 : 0,
      totalElements: payload.length,
    };
  }
  if (Array.isArray(payload?.content)) {
    return {
      rows: payload.content,
      totalPages: Number(payload.totalPages || 0),
      totalElements: Number(payload.totalElements || payload.content.length || 0),
    };
  }
  return { rows: [], totalPages: 0, totalElements: 0 };
}

export function adminAuthFail(res) {
  if (res.status === 403 || res.status === 401) {
    alert('로그인이 만료되었습니다. 다시 로그인해 주세요.');
    window.location.href = '/admin/login';
    return true;
  }
  return false;
}
