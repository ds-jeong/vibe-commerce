import { getAdminToken, getUserToken } from './session';

export const CHAT_MESSAGE_LIMIT = 1000;
export const CHAT_HISTORY_LIMIT = 10;
export const RETURN_CONFIRM_PHRASE = '반품 신청 확인';
export const RETURN_CANCEL_MESSAGE = '반품 신청을 취소합니다.';

export function chatAuthToken() {
  return getUserToken() || getAdminToken() || '';
}

export function chatDraftError(value) {
  const trimmed = String(value ?? '').trim();
  if (!trimmed) {
    return '메시지를 입력해 주세요.';
  }
  if (trimmed.length > CHAT_MESSAGE_LIMIT) {
    return '메시지는 1000자까지 입력할 수 있습니다.';
  }
  return '';
}

export function toChatHistory(messages) {
  return (messages || [])
    .filter(
      (message) =>
        message &&
        (message.role === 'user' || message.role === 'assistant') &&
        typeof message.content === 'string' &&
        message.content.trim()
    )
    .slice(-CHAT_HISTORY_LIMIT)
    .map((message) => ({
      role: message.role,
      content: message.content.trim(),
    }));
}

export function readableChatError(status, body) {
  if (status === 401 || status === 403) {
    return '로그인 후 이용해 주세요.';
  }
  const message = body && typeof body.message === 'string' ? body.message.trim() : '';
  if ((status === 400 || status === 429 || status === 503) && body?.status === 'FAIL' && isSafeMessage(message)) {
    return message;
  }
  if (status === 503) {
    return '챗봇 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.';
  }
  return '챗봇 응답을 받지 못했습니다. 다시 시도해 주세요.';
}

function isSafeMessage(message) {
  if (!message || message.length > 300) {
    return false;
  }
  return !message.includes('Exception') && !message.includes('\tat ') && !message.includes('java.');
}

export async function postChat(message, history) {
  const token = chatAuthToken();
  if (!token) {
    const error = new Error('로그인 후 이용해 주세요.');
    error.needsLogin = true;
    throw error;
  }

  let response;
  try {
    response = await fetch('/api/chat', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message,
        history: toChatHistory(history),
      }),
    });
  } catch {
    throw new Error('챗봇에 연결하지 못했습니다. 다시 시도해 주세요.');
  }

  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok || body?.status !== 'SUCCESS' || typeof body?.reply !== 'string' || !body.reply.trim()) {
    const error = new Error(readableChatError(response.status, body));
    error.needsLogin = response.status === 401 || response.status === 403;
    throw error;
  }
  return body.reply.trim();
}
