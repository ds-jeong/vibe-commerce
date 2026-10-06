export function getUserToken() {
  return localStorage.getItem('userToken');
}

export function getAdminToken() {
  return localStorage.getItem('adminToken');
}

export async function endUserSession() {
  const token = getUserToken();
  localStorage.removeItem('userToken');
  if (!token) {
    return;
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 2500);
  try {
    await fetch('/api/user/logout', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      signal: controller.signal,
    });
  } catch {
    /* 토큰 제거가 우선이므로 네트워크 실패는 무시합니다. */
  } finally {
    clearTimeout(timer);
  }
}

export function endAdminSession() {
  localStorage.removeItem('adminToken');
}

export async function startAdminSession(accessToken) {
  await endUserSession();
  localStorage.setItem('adminToken', accessToken);
}

export function startUserSession(accessToken) {
  endAdminSession();
  localStorage.setItem('userToken', accessToken);
}

export function goToAdminLogin() {
  window.location.href = '/admin/login';
}
