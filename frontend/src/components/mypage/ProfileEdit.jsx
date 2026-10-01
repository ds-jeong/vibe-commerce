import React, { useState } from 'react';
import {
  EMAIL_REGEX,
  NAME_REGEX,
  PHONE_REGEX,
  PW_REGEX,
  ZIP_REGEX,
} from '../../utils/validation';

export default function ProfileEdit({
  profile,
  setProfile,
  currentPassword,
  setCurrentPassword,
  newPassword,
  setNewPassword,
  loading,
  onProfileSave,
  onPasswordSave,
  onAddressSearch,
}) {
  const [localError, setLocalError] = useState('');

  const validateAndSave = (e) => {
    e.preventDefault();
    if (!NAME_REGEX.test((profile?.name || '').trim())) {
      setLocalError('이름은 한글 또는 영문 2~20자로 입력해 주세요.');
      return;
    }
    if (!EMAIL_REGEX.test((profile?.email || '').trim())) {
      setLocalError('올바른 이메일 형식으로 입력해 주세요.');
      return;
    }
    if (!PHONE_REGEX.test(profile?.phoneNumber || '')) {
      setLocalError('전화번호는 010으로 시작하는 숫자 10~11자리로 입력해 주세요.');
      return;
    }
    if (!ZIP_REGEX.test(profile?.zipcode || '')) {
      setLocalError('주소찾기를 이용해 올바른 우편번호를 입력해 주세요.');
      return;
    }
    if (!(profile?.roadAddress || '').trim() || !(profile?.detailAddress || '').trim()) {
      setLocalError('배송지 주소를 모두 입력해 주세요.');
      return;
    }
    setLocalError('');
    onProfileSave(e);
  };

  const validatePassword = (e) => {
    e.preventDefault();
    if (!PW_REGEX.test(newPassword || '')) {
      setLocalError('비밀번호는 8~16자의 영문, 숫자, 특수문자를 모두 포함해야 합니다.');
      return;
    }
    setLocalError('');
    onPasswordSave(e);
  };

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      {localError ? (
        <div className="lg:col-span-2 rounded-xl border border-red-100 bg-red-50 p-3 text-xs font-bold text-red-500">
          ⚠️ {localError}
        </div>
      ) : null}
      <form onSubmit={validateAndSave} className="space-y-3">
        <h3 className="text-lg font-extrabold text-gray-800">기본 정보</h3>
        <input
          readOnly
          value={profile?.userKey || ''}
          className="w-full rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm"
        />
        <input
          required
          value={profile?.name || ''}
          onChange={(e) =>
            setProfile((prev) => ({ ...prev, name: e.target.value }))
          }
          placeholder="이름"
          className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
        />
        <input
          type="email"
          required
          value={profile?.email || ''}
          onChange={(e) =>
            setProfile((prev) => ({ ...prev, email: e.target.value }))
          }
          placeholder="이메일"
          className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm"
        />
        {profile?.email ? (
          <p
            className={`text-xs font-semibold ${
              EMAIL_REGEX.test((profile.email || '').trim())
                ? 'text-green-600'
                : 'text-red-500'
            }`}
          >
            {EMAIL_REGEX.test((profile.email || '').trim())
              ? '사용 가능한 이메일 형식입니다.'
              : '올바른 이메일 형식으로 입력해 주세요.'}
          </p>
        ) : null}
        <input
          required
          value={profile?.phoneNumber || ''}
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
          onClick={onAddressSearch}
          className="w-full rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
        >
          주소 찾기
        </button>
        <div className="grid grid-cols-3 gap-2">
          <input
            readOnly
            value={profile?.zipcode || ''}
            placeholder="우편번호"
            className="rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm"
          />
          <input
            readOnly
            value={profile?.roadAddress || ''}
            placeholder="도로명주소"
            className="col-span-2 rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm"
          />
        </div>
        <input
          required
          value={profile?.detailAddress || ''}
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
          className="w-full rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:opacity-50"
        >
          정보 저장
        </button>
      </form>

      <form onSubmit={validatePassword} className="space-y-3">
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
          className="w-full rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B] disabled:opacity-50"
        >
          비밀번호 변경
        </button>
      </form>
    </div>
  );
}
