import React, { useEffect, useId, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  CHAT_MESSAGE_LIMIT,
  RETURN_CANCEL_MESSAGE,
  RETURN_CONFIRM_PHRASE,
  chatAuthToken,
  chatDraftError,
  postChat,
} from '../../utils/chatApi';

export default function ChatWidget() {
  const { pathname } = useLocation();
  const titleId = useId();
  const inputRef = useRef(null);
  const loginButtonRef = useRef(null);
  const openButtonRef = useRef(null);
  const logRef = useRef(null);
  const sendingRef = useRef(false);
  const idRef = useRef(1);

  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [needsLogin, setNeedsLogin] = useState(false);
  const [retryText, setRetryText] = useState('');
  const [authed, setAuthed] = useState(() => Boolean(chatAuthToken()));

  const lastMessage = messages[messages.length - 1];
  const showReturnConfirm =
    !sending && lastMessage?.role === 'assistant' && lastMessage.content.includes(RETURN_CONFIRM_PHRASE);
  const draftError = draft.trim() ? chatDraftError(draft) : '';

  useEffect(() => {
    const syncAuth = () => setAuthed(Boolean(chatAuthToken()));
    window.addEventListener('focus', syncAuth);
    return () => window.removeEventListener('focus', syncAuth);
  }, []);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const frame = window.requestAnimationFrame(() => {
      if (authed) {
        inputRef.current?.focus();
      } else {
        loginButtonRef.current?.focus();
      }
    });
    return () => window.cancelAnimationFrame(frame);
  }, [open, authed]);

  useEffect(() => {
    if (!open || !logRef.current) {
      return;
    }
    logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [open, messages, sending, error, showReturnConfirm]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        closePanel();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open]);

  function closePanel() {
    setOpen(false);
    window.requestAnimationFrame(() => openButtonRef.current?.focus());
  }

  async function sendMessage(text, options = {}) {
    const content = String(text ?? '').trim();
    const validationError = chatDraftError(content);
    if (validationError) {
      setError(validationError);
      return;
    }
    if (sendingRef.current) {
      return;
    }
    if (!chatAuthToken()) {
      setAuthed(false);
      setNeedsLogin(true);
      setError('로그인 후 이용해 주세요.');
      return;
    }

    sendingRef.current = true;
    setSending(true);
    setError('');
    setNeedsLogin(false);
    setRetryText('');
    if (!options.retry) {
      setDraft('');
      setMessages((current) => [...current, { id: idRef.current++, role: 'user', content }]);
    }

    const history = options.retry ? messages.slice(0, -1) : messages;
    try {
      const reply = await postChat(content, history);
      setMessages((current) => [...current, { id: idRef.current++, role: 'assistant', content: reply }]);
    } catch (sendError) {
      setError(sendError?.message || '챗봇 응답을 받지 못했습니다. 다시 시도해 주세요.');
      setNeedsLogin(Boolean(sendError?.needsLogin));
      setRetryText(content);
    } finally {
      sendingRef.current = false;
      setSending(false);
    }
  }

  function submitDraft() {
    sendMessage(draft);
  }

  function onInputKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submitDraft();
    }
  }

  if (pathname.startsWith('/admin')) {
    return null;
  }

  return (
    <div className="fixed bottom-[max(1rem,env(safe-area-inset-bottom))] right-4 z-30 flex w-[min(100vw-2rem,24rem)] flex-col items-end gap-3">
      {open && (
        <section
          id="shop-chat-panel"
          role="dialog"
          aria-labelledby={titleId}
          className="flex max-h-[calc(100vh-6rem)] w-full flex-col overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl"
        >
          <header className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div>
              <h2 id={titleId} className="text-sm font-semibold text-black">
                쇼핑 도우미
              </h2>
              <p className="text-[11px] text-slate-400">주문·배송 안내와 반품 신청</p>
            </div>
            <button
              type="button"
              onClick={closePanel}
              className="rounded-md px-3 py-2 text-xs font-semibold text-slate-500 transition hover:bg-slate-50 hover:text-black"
            >
              닫기
            </button>
          </header>

          <div ref={logRef} role="log" aria-live="polite" aria-relevant="additions" className="flex min-h-48 flex-1 flex-col gap-3 overflow-y-auto px-4 py-4">
            {messages.length === 0 && (
              <p className="text-sm leading-6 text-slate-500">
                주문 상태, 배송, 반품 절차를 물어볼 수 있습니다. 대화는 이 화면에만 있고 저장하지 않습니다.
              </p>
            )}
            {messages.map((message) => (
              <div key={message.id} className={message.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
                <p
                  className={
                    message.role === 'user'
                      ? 'max-w-[85%] whitespace-pre-wrap break-words rounded-md bg-[#0A192F] px-3 py-2 text-sm leading-6 text-white'
                      : 'max-w-[85%] whitespace-pre-wrap break-words rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm leading-6 text-slate-800'
                  }
                >
                  {message.content}
                </p>
              </div>
            ))}
            {sending && (
              <p className="text-sm text-slate-500" role="status">
                답변을 준비하고 있습니다.
              </p>
            )}
          </div>

          {showReturnConfirm && (
            <div className="border-t border-slate-100 bg-slate-50 px-4 py-3">
              <p className="text-sm leading-6 text-slate-700">
                위 내용으로 반품을 접수하려면 확인을 누르세요. 확인 문구는 서버에서 다시 검사하며, 취소하면 접수되지 않습니다.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => sendMessage(RETURN_CONFIRM_PHRASE)}
                  className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white transition hover:bg-[#1E293B]"
                >
                  반품 신청 확인
                </button>
                <button
                  type="button"
                  onClick={() => sendMessage(RETURN_CANCEL_MESSAGE)}
                  className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-50"
                >
                  취소
                </button>
              </div>
            </div>
          )}

          {error && (
            <div className="border-t border-slate-100 px-4 py-3" role="alert">
              <p className="text-sm leading-6 text-slate-700">{error}</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {needsLogin ? (
                  <button
                    type="button"
                    onClick={() => {
                      window.location.href = '/login';
                    }}
                    className="rounded-md bg-[#0A192F] px-3 py-2 text-xs font-semibold text-white"
                  >
                    로그인
                  </button>
                ) : (
                  retryText && (
                    <button
                      type="button"
                      onClick={() => sendMessage(retryText, { retry: true })}
                      disabled={sending}
                      className="rounded-md border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 disabled:opacity-50"
                    >
                      다시 시도
                    </button>
                  )
                )}
              </div>
            </div>
          )}

          {authed ? (
            <form
              className="border-t border-slate-100 p-3"
              onSubmit={(event) => {
                event.preventDefault();
                submitDraft();
              }}
            >
              <label htmlFor="shop-chat-input" className="sr-only">
                메시지
              </label>
              <textarea
                id="shop-chat-input"
                ref={inputRef}
                value={draft}
                maxLength={CHAT_MESSAGE_LIMIT}
                rows={3}
                disabled={sending}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={onInputKeyDown}
                placeholder="메시지를 입력하세요. Enter로 전송, Shift+Enter로 줄바꿈"
                className="w-full resize-none rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm leading-6 outline-none transition focus:border-[#0A192F] focus:bg-white disabled:opacity-60"
              />
              <div className="mt-2 flex items-center justify-between gap-2">
                <p className="text-[11px] text-slate-400">
                  {draft.length > 800 ? `${draft.trim().length}/${CHAT_MESSAGE_LIMIT}` : 'Enter 전송'}
                </p>
                <button
                  type="submit"
                  disabled={sending || Boolean(chatDraftError(draft))}
                  className="rounded-md bg-[#0A192F] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#1E293B] disabled:cursor-not-allowed disabled:opacity-40"
                >
                  보내기
                </button>
              </div>
              {draftError && <p className="mt-2 text-xs text-slate-600">{draftError}</p>}
            </form>
          ) : (
            <div className="border-t border-slate-100 px-4 py-4">
              <p className="text-sm leading-6 text-slate-600">로그인 후 주문 조회와 반품 안내를 이용할 수 있습니다.</p>
              <button
                ref={loginButtonRef}
                type="button"
                onClick={() => {
                  window.location.href = '/login';
                }}
                className="mt-3 rounded-md bg-[#0A192F] px-4 py-2 text-xs font-semibold text-white"
              >
                로그인
              </button>
            </div>
          )}
        </section>
      )}

      <button
        ref={openButtonRef}
        type="button"
        aria-expanded={open}
        aria-controls="shop-chat-panel"
        onClick={() => (open ? closePanel() : setOpen(true))}
        className="rounded-full bg-[#0A192F] px-4 py-3 text-sm font-semibold text-white shadow-lg transition hover:bg-[#1E293B]"
      >
        {open ? '챗봇 닫기' : '챗봇 열기'}
      </button>
    </div>
  );
}
