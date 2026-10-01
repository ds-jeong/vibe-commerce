import React, { useState, useEffect } from 'react';

export default function UserSignupPage() {
  // ========================================
  // 회원가입 입력값
  // ========================================

  const [userKey, setUserKey] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setPasswordConfirm] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [zipcode, setZipcode] = useState('');
  const [roadAddress, setRoadAddress] = useState('');
  const [detailAddress, setDetailAddress] = useState('');

  // ========================================
  // 검증 메시지
  // ========================================

  const [idFeedback, setIdFeedback] = useState({
    type: '',
    msg: '',
  });

  const [pwFeedback, setPwFeedback] = useState({
    type: '',
    msg: '',
  });

  const [confirmPwFeedback, setConfirmPwFeedback] = useState({
    type: '',
    msg: '',
  });

  const [phoneFeedback, setPhoneFeedback] = useState({
    type: '',
    msg: '',
  });

  const [emailFeedback, setEmailFeedback] = useState({
    type: '',
    msg: '',
  });

  const [error, setError] = useState('');

  // ========================================
  // 정규식
  // ========================================

  // 아이디
  // 4~12자 영문 소문자 + 숫자
  const idRegex = /^[a-z0-9]{4,12}$/;

  // 비밀번호
  // 8~16자
  // 영문 1개 이상
  // 숫자 1개 이상
  // 특수문자 1개 이상
  const pwRegex =
    /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,16}$/;

  // 전화번호
  // 010으로 시작하는 숫자 10~11자리
  const phoneRegex = /^010\d{7,8}$/;

  // 우편번호
  // 숫자 5자리
  const zipRegex = /^\d{5}$/;
  const emailRegex =
    /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  const nameRegex = /^[가-힣a-zA-Z\s]{2,20}$/;


  // ========================================
  // Daum/Kakao 우편번호 API 스크립트 로딩
  // ========================================

  useEffect(() => {
    const scriptId = 'daum-postcode-script';

    // 이미 스크립트가 있으면 다시 추가하지 않음
    if (document.getElementById(scriptId)) {
      return;
    }

    const script = document.createElement('script');

    script.id = scriptId;
    script.src =
      'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
    script.async = true;

    document.body.appendChild(script);

    return () => {
      // 페이지가 완전히 제거될 때 스크립트를 삭제하지 않음
      // 다른 페이지에서도 사용할 수 있도록 유지
    };
  }, []);


  // ========================================
  // 주소찾기
  // ========================================

  const handleAddressSearch = () => {
    setError('');

    // API 스크립트가 아직 로딩되지 않은 경우
    if (!window.daum || !window.daum.Postcode) {
      setError(
        '주소 검색 서비스를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.'
      );

      return;
    }

    new window.daum.Postcode({
      oncomplete: function (data) {
        let selectedAddress = '';

        // 사용자가 도로명주소를 선택한 경우
        if (data.userSelectedType === 'R') {
          selectedAddress = data.roadAddress;
        } else {
          // 사용자가 지번주소를 선택한 경우
          selectedAddress = data.jibunAddress;
        }

        // 우편번호 자동 입력
        setZipcode(data.zonecode);

        // 도로명주소 자동 입력
        setRoadAddress(selectedAddress);

        // 상세주소 초기화
        setDetailAddress('');

        // 상세주소 입력창으로 포커스
        setTimeout(() => {
          document.getElementById('detailAddress')?.focus();
        }, 100);
      },
    }).open();
  };


  // ========================================
  // 아이디 실시간 검사 + 중복 확인
  // ========================================

  useEffect(() => {
    if (!userKey) {
      setIdFeedback({
        type: '',
        msg: '',
      });

      return;
    }

    // 아이디 정규식 검사
    if (!idRegex.test(userKey)) {
      setIdFeedback({
        type: 'error',
        msg: '❌ 4~12자의 영문 소문자와 숫자만 사용 가능합니다.',
      });

      return;
    }

    // 200ms 디바운싱
    const delayDebounceFn = setTimeout(() => {
      fetch(`/api/user/check-id?userKey=${encodeURIComponent(userKey)}`)
        .then((res) => {
          if (res.status === 409) {
            setIdFeedback({
              type: 'error',
              msg: '❌ 이미 사용 중인 아이디입니다.',
            });
          } else if (res.ok) {
            setIdFeedback({
              type: 'success',
              msg: '🟢 사용 가능한 아이디입니다.',
            });
          } else {
            setIdFeedback({
              type: 'error',
              msg: '❌ 아이디 중복 확인에 실패했습니다.',
            });
          }
        })
        .catch(() => {
          setIdFeedback({
            type: 'error',
            msg: '❌ 서버 통신 중 오류가 발생했습니다.',
          });
        });
    }, 200);

    return () => clearTimeout(delayDebounceFn);
  }, [userKey]);


  // ========================================
  // 비밀번호 실시간 검사
  // ========================================

  useEffect(() => {
    if (!password) {
      setPwFeedback({
        type: '',
        msg: '',
      });

      return;
    }

    if (!pwRegex.test(password)) {
      setPwFeedback({
        type: 'error',
        msg: '❌ 8~16자의 영문, 숫자, 특수문자를 모두 포함해야 합니다.',
      });
    } else {
      setPwFeedback({
        type: 'success',
        msg: '🟢 안전한 비밀번호 형식입니다.',
      });
    }
  }, [password]);


  // ========================================
  // 비밀번호 확인 실시간 검사
  // ========================================

  useEffect(() => {
    if (!confirmPassword) {
      setConfirmPwFeedback({
        type: '',
        msg: '',
      });

      return;
    }

    if (password !== confirmPassword) {
      setConfirmPwFeedback({
        type: 'error',
        msg: '❌ 비밀번호가 일치하지 않습니다.',
      });
    } else {
      setConfirmPwFeedback({
        type: 'success',
        msg: '🟢 비밀번호가 일치합니다.',
      });
    }
  }, [password, confirmPassword]);


  // ========================================
  // 전화번호 실시간 검사
  // ========================================

  useEffect(() => {
    if (!phoneNumber) {
      setPhoneFeedback({
        type: '',
        msg: '',
      });

      return;
    }

    if (!phoneRegex.test(phoneNumber)) {
      setPhoneFeedback({
        type: 'error',
        msg: '❌ 하이픈(-) 없이 010으로 시작하는 숫자만 입력하세요.',
      });
    } else {
      setPhoneFeedback({
        type: 'success',
        msg: '🟢 올바른 연락처 형식입니다.',
      });
    }
  }, [phoneNumber]);


  // ========================================
  // 회원가입 제출
  // ========================================

  const handleSignupSubmit = (e) => {
    e.preventDefault();

    setError('');

    // ----------------------------------------
    // 아이디 정규식 최종 검사
    // ----------------------------------------

    if (!idRegex.test(userKey)) {
      setError(
        '아이디는 4~12자의 영문 소문자와 숫자만 사용할 수 있습니다.'
      );

      return;
    }

    // ----------------------------------------
    // 아이디 중복 확인
    // ----------------------------------------

    if (idFeedback.type !== 'success') {
      setError('사용 가능한 아이디인지 확인해 주세요.');

      return;
    }

    // ----------------------------------------
    // 비밀번호 정규식 최종 검사
    // ----------------------------------------

    if (!pwRegex.test(password)) {
      setError(
        '비밀번호는 8~16자의 영문, 숫자, 특수문자를 모두 포함해야 합니다.'
      );

      return;
    }

    // ----------------------------------------
    // 비밀번호 확인
    // ----------------------------------------

    if (password !== confirmPassword) {
      setError('비밀번호와 비밀번호 확인이 일치하지 않습니다.');

      return;
    }

    // ----------------------------------------
    // 이름 검사
    // ----------------------------------------

    if (!nameRegex.test(name.trim())) {
      setError('이름은 한글 또는 영문 2~20자로 입력해 주세요.');

      return;
    }

    if (!email.trim()) {
      setError('이메일을 입력해 주세요.');

      return;
    }

    if (!emailRegex.test(email.trim())) {
      setError('올바른 이메일 형식으로 입력해 주세요.');

      return;
    }

    // ----------------------------------------
    // 전화번호 정규식 최종 검사
    // ----------------------------------------

    if (!phoneRegex.test(phoneNumber)) {
      setError(
        '전화번호는 010으로 시작하는 숫자 10~11자리로 입력해 주세요.'
      );

      return;
    }

    // ----------------------------------------
    // 우편번호 정규식 최종 검사
    // ----------------------------------------

    if (!zipRegex.test(zipcode)) {
      setError('주소찾기를 이용해 올바른 우편번호를 입력해 주세요.');

      return;
    }

    // ----------------------------------------
    // 도로명주소 검사
    // ----------------------------------------

    if (!roadAddress.trim()) {
      setError('주소찾기를 이용해 주소를 입력해 주세요.');

      return;
    }

    // ----------------------------------------
    // 상세주소 검사
    // ----------------------------------------

    if (!detailAddress.trim()) {
      setError('상세주소를 입력해 주세요.');

      return;
    }

    // ----------------------------------------
    // 회원가입 API 요청
    // ----------------------------------------

    fetch('/api/user/signup', {
      method: 'POST',

      headers: {
        'Content-Type': 'application/json',
      },

      body: JSON.stringify({
        userKey,
        password,
        name,
        email,
        phoneNumber,
        zipcode,
        roadAddress,
        detailAddress,
      }),
    })
      .then((res) => res.json())

      .then((resData) => {
        if (resData.status === 'SUCCESS') {
          alert(
            `회원가입이 완료되었습니다.\n${name}님, 로그인해 주세요.`
          );

          window.location.href = '/login';
        } else {
          setError(
            resData.message ||
              '회원가입 처리 중 오류가 발생했습니다.'
          );
        }
      })

      .catch(() => {
        setError('네트워크 오류가 발생했습니다.');
      });
  };


  // ========================================
  // 화면
  // ========================================

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100 font-sans px-4 py-12">

      <div className="w-full max-w-lg rounded-2xl border border-gray-200 bg-white p-8 shadow-xl">

        {/* ========================================
            제목
        ======================================== */}

        <div className="text-center mb-6">

          <span className="text-3xl">
            🛒
          </span>

          <h2 className="mt-2 text-2xl font-extrabold text-gray-800 tracking-tight">
            VibeCommerce 회원가입
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            회원정보를 입력해 주세요.
          </p>

        </div>


        <form
          onSubmit={handleSignupSubmit}
          className="space-y-4"
        >

          {/* ========================================
              아이디
          ======================================== */}

          <div>

            <label className="text-xs font-bold text-gray-500 block mb-1">
              가입 ID (4~12자 영문 소문자, 숫자)
            </label>

            <input
              type="text"
              required
              value={userKey}
              onChange={(e) => setUserKey(e.target.value)}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
              placeholder="myid123"
            />

            {idFeedback.msg && (
              <p
                className={`mt-1.5 text-xs font-bold ${
                  idFeedback.type === 'success'
                    ? 'text-emerald-600'
                    : 'text-red-500'
                }`}
              >
                {idFeedback.msg}
              </p>
            )}

          </div>


          {/* ========================================
              비밀번호
          ======================================== */}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">

            {/* 비밀번호 */}

            <div>

              <label className="text-xs font-bold text-gray-500 block mb-1">
                비밀번호 (8~16자 영문/숫자/특수문자)
              </label>

              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
                placeholder="••••••••"
              />

              {pwFeedback.msg && (
                <p
                  className={`mt-1.5 text-xs font-bold ${
                    pwFeedback.type === 'success'
                      ? 'text-emerald-600'
                      : 'text-red-500'
                  }`}
                >
                  {pwFeedback.msg}
                </p>
              )}

            </div>


            {/* 비밀번호 확인 */}

            <div>

              <label className="text-xs font-bold text-gray-500 block mb-1">
                비밀번호 확인
              </label>

              <input
                type="password"
                required
                value={confirmPassword}
                onChange={(e) =>
                  setPasswordConfirm(e.target.value)
                }
                className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
                placeholder="••••••••"
              />

              {confirmPwFeedback.msg && (
                <p
                  className={`mt-1.5 text-xs font-bold ${
                    confirmPwFeedback.type === 'success'
                      ? 'text-emerald-600'
                      : 'text-red-500'
                  }`}
                >
                  {confirmPwFeedback.msg}
                </p>
              )}

            </div>

          </div>


          {/* ========================================
              이름 / 전화번호
          ======================================== */}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">

            {/* 이름 */}

            <div>

              <label className="text-xs font-bold text-gray-500 block mb-1">
                고객 실명
              </label>

              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
                placeholder="홍길동"
              />

            </div>


            {/* 전화번호 */}

            <div>

              <label className="text-xs font-bold text-gray-500 block mb-1">
                연락처 (숫자만 입력)
              </label>

              <input
                type="text"
                required
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
                placeholder="01012345678"
                maxLength={11}
              />

              {phoneFeedback.msg && (
                <p
                  className={`mt-1.5 text-xs font-bold ${
                    phoneFeedback.type === 'success'
                      ? 'text-emerald-600'
                      : 'text-red-500'
                  }`}
                >
                  {phoneFeedback.msg}
                </p>
              )}

            </div>

          </div>


          <div>
            <label className="text-xs font-bold text-gray-500 block mb-1">
              이메일
            </label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => {
                const value = e.target.value;
                setEmail(value);
                if (!value) {
                  setEmailFeedback({ type: '', msg: '' });
                  return;
                }
                if (!emailRegex.test(value.trim())) {
                  setEmailFeedback({
                    type: 'error',
                    msg: '올바른 이메일 형식으로 입력해 주세요.',
                  });
                } else {
                  setEmailFeedback({
                    type: 'success',
                    msg: '사용 가능한 이메일 형식입니다.',
                  });
                }
              }}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
              placeholder="you@example.com"
            />
            {emailFeedback.msg && (
              <p
                className={`mt-1.5 text-xs font-bold ${
                  emailFeedback.type === 'success'
                    ? 'text-emerald-600'
                    : 'text-red-500'
                }`}
              >
                {emailFeedback.msg}
              </p>
            )}
          </div>


          {/* ========================================
              배송지
          ======================================== */}

          <div className="border-t border-dashed border-gray-200 pt-4 space-y-3">

            <label className="text-xs font-bold text-blue-600 uppercase tracking-wider block">
              🏡 실물 상품 수령 배송지 등록
            </label>


            {/* 주소 찾기 버튼 */}

            <button
              type="button"
              onClick={handleAddressSearch}
              className="w-full rounded-md bg-[#0A192F] py-3 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
            >
              📍 주소 찾기
            </button>


            {/* 우편번호 + 도로명주소 */}

            <div className="grid grid-cols-3 gap-2">

              {/* 우편번호 */}

              <input
                type="text"
                required
                readOnly
                value={zipcode}
                className="col-span-1 rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm font-semibold text-gray-700 outline-none"
                placeholder="우편번호"
              />


              {/* 도로명주소 */}

              <input
                type="text"
                required
                readOnly
                value={roadAddress}
                className="col-span-2 rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm font-semibold text-gray-700 outline-none"
                placeholder="주소 찾기를 눌러주세요"
              />

            </div>


            {/* 상세주소 */}

            <input
              id="detailAddress"
              type="text"
              required
              value={detailAddress}
              onChange={(e) =>
                setDetailAddress(e.target.value)
              }
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm focus:border-blue-600 focus:bg-white focus:outline-none transition"
              placeholder="상세주소 입력 (예: 101동 203호)"
            />

          </div>


          {/* ========================================
              최종 에러 메시지
          ======================================== */}

          {error && (
            <div className="rounded-xl bg-red-50 p-3 text-xs font-bold text-red-500 border border-red-100">
              ⚠️ {error}
            </div>
          )}


          {/* ========================================
              회원가입 버튼
          ======================================== */}

          <button
            type="submit"
            className="w-full rounded-md bg-[#0A192F] py-3.5 text-sm font-semibold text-white shadow-sm transition hover:scale-[1.01] hover:bg-[#1E293B]"
          >
            회원가입 완료
          </button>

        </form>

      </div>

    </div>
  );
}

