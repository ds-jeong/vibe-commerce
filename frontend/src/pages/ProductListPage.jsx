import React, { useEffect, useMemo, useState } from 'react';
import * as PortOne from '@portone/browser-sdk/v2';

export default function ProductListPage() {
  // =========================================================
  // PortOne V2 설정
  // =========================================================

  const PORTONE_STORE_ID =
    'store-22346845-e8d2-4963-bc8a-c5057af09053';

  const PORTONE_CHANNEL_KEY =
    'channel-key-6c90ad46-b43b-4b03-9b77-2e875b45d99c';

  // =========================================================
  // 상품 목록
  // =========================================================

  const [products, setProducts] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);

  const pageSize = 10;

  // =========================================================
  // 바로구매 / 비회원 주문
  // =========================================================

  const [isChoiceModalOpen, setIsChoiceModalOpen] =
    useState(false);

  const [isGuestOrderFormOpen, setIsGuestOrderFormOpen] =
    useState(false);

  const [selectedProduct, setSelectedProduct] =
    useState(null);

  const [guestName, setGuestName] = useState('');
  const [guestPhone, setGuestPhone] = useState('');
  const [guestZipcode, setGuestZipcode] = useState('');
  const [guestRoadAddress, setGuestRoadAddress] =
    useState('');
  const [guestDetailAddress, setGuestDetailAddress] =
    useState('');

  const [guestNameMessage, setGuestNameMessage] =
    useState('');

  const [guestPhoneMessage, setGuestPhoneMessage] =
    useState('');

  const [guestOrderError, setGuestOrderError] =
    useState('');

  const [guestOrderLoading, setGuestOrderLoading] =
    useState(false);

  // =========================================================
  // 장바구니
  // =========================================================

  const [guestCart, setGuestCart] = useState([]);
  const [isCartModalOpen, setIsCartModalOpen] =
    useState(false);

  // =========================================================
  // 정규식
  // =========================================================

  const guestNameRegex = /^[가-힣a-zA-Z\s]{2,20}$/;
  const guestPhoneRegex = /^010\d{7,8}$/;
  const guestZipRegex = /^\d{5}$/;

  // =========================================================
  // 로그인 여부
  // =========================================================

  const userToken = localStorage.getItem('userToken');

  // =========================================================
  // 상품 목록 조회
  // =========================================================

  useEffect(() => {
    fetchProducts();
  }, [currentPage]);

  const fetchProducts = async () => {
    setLoading(true);

    try {
      const response = await fetch(
        `/api/products?page=${currentPage}&size=${pageSize}`
      );

      if (!response.ok) {
        throw new Error(
          '상품 목록을 불러오지 못했습니다.'
        );
      }

      const data = await response.json();

      // 백엔드 응답 구조 대응
      const productList =
        data.content ||
        data.products ||
        data.data?.content ||
        data.data?.products ||
        [];

      const pages =
        data.totalPages ??
        data.data?.totalPages ??
        0;

      setProducts(productList);
      setTotalPages(pages);
    } catch (error) {
      console.error('상품 조회 오류:', error);
      alert(
        '상품 목록을 불러오는 중 오류가 발생했습니다.'
      );
    } finally {
      setLoading(false);
    }
  };

  // =========================================================
  // 장바구니 목록 새로고침
  // =========================================================

  const refreshCartList = async () => {
    const token = localStorage.getItem('userToken');

    // =======================================================
    // 비회원
    // =======================================================

    if (!token) {
      try {
        const localCart = JSON.parse(
          localStorage.getItem('guestCart') || '[]'
        );

        if (Array.isArray(localCart)) {
          setGuestCart(localCart);
        } else {
          setGuestCart([]);
        }
      } catch (error) {
        console.error(
          '비회원 장바구니 불러오기 오류:',
          error
        );

        setGuestCart([]);
      }

      return;
    }

    // =======================================================
    // 회원
    // =======================================================

    try {
      const response = await fetch('/api/cart/list', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(
          'DB 장바구니 목록 조회에 실패했습니다.'
        );
      }

      const dbCartItems = await response.json();

      /*
       * 백엔드 CartItem 구조
       *
       * {
       *   id,
       *   username,
       *   productId,
       *   productName,
       *   price,
       *   quantity
       * }
       *
       * ↓ 프론트 장바구니 구조로 변환
       *
       * {
       *   id,
       *   name,
       *   price,
       *   quantity
       * }
       */

      const mappedCart = Array.isArray(dbCartItems)
        ? dbCartItems.map((item) => ({
            id: item.productId,
            name: item.productName,
            price: item.price,
            quantity: item.quantity,
          }))
        : [];

      setGuestCart(mappedCart);

      console.log(
        '회원 DB 장바구니 동기화 완료:',
        mappedCart
      );
    } catch (error) {
      console.error(
        '회원 DB 장바구니 조회 오류:',
        error
      );

      setGuestCart([]);

      alert(
        '회원 장바구니를 불러오는 중 오류가 발생했습니다.'
      );
    }
  };

  // =========================================================
  // 장바구니 초기 조회
  // =========================================================

  useEffect(() => {
    refreshCartList();
  }, []);

  // =========================================================
  // 비회원 장바구니만 localStorage에 저장
  // =========================================================

  useEffect(() => {
    const token = localStorage.getItem('userToken');

    // 회원이면 localStorage에 저장하지 않는다.
    if (token) {
      return;
    }

    localStorage.setItem(
      'guestCart',
      JSON.stringify(guestCart)
    );
  }, [guestCart]);

  // =========================================================
  // 장바구니 총 상품 개수
  // =========================================================

  const cartItemCount = useMemo(() => {
    return guestCart.reduce(
      (total, item) =>
        total + Number(item.quantity || 0),
      0
    );
  }, [guestCart]);

  // =========================================================
  // 장바구니 총 금액
  // =========================================================

  const cartTotalPrice = useMemo(() => {
    return guestCart.reduce(
      (total, item) =>
        total +
        Number(item.price || 0) *
          Number(item.quantity || 0),
      0
    );
  }, [guestCart]);

  // =========================================================
  // 가격 표시
  // =========================================================

  const formatPrice = (price) => {
    return Number(price || 0).toLocaleString('ko-KR');
  };

  // =========================================================
  // 장바구니 담기
  // =========================================================

  const handleAddToCart = (product) => {
    const token = localStorage.getItem('userToken');

    // =======================================================
    // 회원
    // =======================================================

    if (token) {
      alert(
        '회원 장바구니 DB 연동 준비가 완료되었습니다.\n\n' +
          '현재는 회원 장바구니 조회 기능이 연결되어 있습니다.'
      );

      return;
    }

    // =======================================================
    // 비회원
    // =======================================================

    setGuestCart((prevCart) => {
      const existingIndex = prevCart.findIndex(
        (item) =>
          Number(item.id) === Number(product.id)
      );

      // 이미 담겨있는 상품이면 수량 +1
      if (existingIndex !== -1) {
        return prevCart.map((item, index) =>
          index === existingIndex
            ? {
                ...item,
                quantity:
                  Number(item.quantity || 0) + 1,
              }
            : item
        );
      }

      // 처음 담는 상품
      return [
        ...prevCart,
        {
          id: product.id,
          name: product.name,
          price: product.price,
          quantity: 1,
        },
      ];
    });

    alert(
      `"${product.name}" 상품을 장바구니에 담았습니다.`
    );
  };

  // =========================================================
  // 장바구니 수량 증가
  // =========================================================

  const increaseCartQuantity = (productId) => {
    const token = localStorage.getItem('userToken');

    // 회원 DB 수량 변경 API는 아직 연결하지 않음
    if (token) {
      alert(
        '회원 장바구니 수량 변경 API 연결이 필요합니다.'
      );

      return;
    }

    setGuestCart((prevCart) =>
      prevCart.map((item) =>
        Number(item.id) === Number(productId)
          ? {
              ...item,
              quantity:
                Number(item.quantity || 0) + 1,
            }
          : item
      )
    );
  };

  // =========================================================
  // 장바구니 수량 감소
  // =========================================================

  const decreaseCartQuantity = (productId) => {
    const token = localStorage.getItem('userToken');

    // 회원 DB 수량 변경 API는 아직 연결하지 않음
    if (token) {
      alert(
        '회원 장바구니 수량 변경 API 연결이 필요합니다.'
      );

      return;
    }

    setGuestCart((prevCart) =>
      prevCart
        .map((item) =>
          Number(item.id) === Number(productId)
            ? {
                ...item,
                quantity:
                  Number(item.quantity || 0) - 1,
              }
            : item
        )
        .filter(
          (item) =>
            Number(item.quantity || 0) > 0
        )
    );
  };

  // =========================================================
  // 장바구니 상품 삭제
  // =========================================================

  const removeFromCart = (productId) => {
    const token = localStorage.getItem('userToken');

    // 회원 DB 삭제 API는 아직 연결하지 않음
    if (token) {
      alert(
        '회원 장바구니 삭제 API 연결이 필요합니다.'
      );

      return;
    }

    setGuestCart((prevCart) =>
      prevCart.filter(
        (item) =>
          Number(item.id) !== Number(productId)
      )
    );
  };

  // =========================================================
  // 장바구니 전체 비우기
  // =========================================================

  const clearCart = () => {
    const token = localStorage.getItem('userToken');

    // 회원 DB 전체 삭제 API는 아직 연결하지 않음
    if (token) {
      alert(
        '회원 장바구니 전체 삭제 API 연결이 필요합니다.'
      );

      return;
    }

    if (guestCart.length === 0) {
      return;
    }

    const confirmed = window.confirm(
      '장바구니의 모든 상품을 삭제하시겠습니까?'
    );

    if (!confirmed) {
      return;
    }

    setGuestCart([]);
  };

  // =========================================================
  // 바로구매
  // =========================================================

  const handleImmediateBuy = (product) => {
    setSelectedProduct(product);
    setIsChoiceModalOpen(true);
  };

  // =========================================================
  // 회원 바로구매
  // =========================================================

  const handleSelectMemberOrder = () => {
    setIsChoiceModalOpen(false);
    window.location.href = '/login';
  };

  // =========================================================
  // 비회원 바로구매
  // =========================================================

  const handleSelectGuestOrder = () => {
    setIsChoiceModalOpen(false);

    setGuestName('');
    setGuestPhone('');
    setGuestZipcode('');
    setGuestRoadAddress('');
    setGuestDetailAddress('');
    setGuestNameMessage('');
    setGuestPhoneMessage('');
    setGuestOrderError('');

    setIsGuestOrderFormOpen(true);
  };

  // =========================================================
  // 비회원 이름 입력
  // =========================================================

  const handleGuestNameChange = (e) => {
    const value = e.target.value;

    setGuestName(value);

    if (!value) {
      setGuestNameMessage('');
      return;
    }

    if (!guestNameRegex.test(value)) {
      setGuestNameMessage(
        '이름은 한글 또는 영문 2~20자로 입력해주세요.'
      );
    } else {
      setGuestNameMessage(
        '✓ 올바른 이름 형식입니다.'
      );
    }
  };

  // =========================================================
  // 비회원 전화번호 입력
  // =========================================================

  const handleGuestPhoneChange = (e) => {
    const value = e.target.value.replace(/\D/g, '');

    setGuestPhone(value);

    if (!value) {
      setGuestPhoneMessage('');
      return;
    }

    if (!guestPhoneRegex.test(value)) {
      setGuestPhoneMessage(
        '휴대폰 번호는 010으로 시작하는 숫자만 입력해주세요.'
      );
    } else {
      setGuestPhoneMessage(
        '✓ 올바른 전화번호 형식입니다.'
      );
    }
  };

  // =========================================================
  // 우편번호 검색
  // =========================================================

  const handleAddressSearch = () => {
    if (
      !window.daum ||
      !window.daum.Postcode
    ) {
      alert(
        '주소 검색 서비스를 불러오지 못했습니다.\n잠시 후 다시 시도해주세요.'
      );

      return;
    }

    new window.daum.Postcode({
      oncomplete: function (data) {
        setGuestZipcode(data.zonecode);

        setGuestRoadAddress(
          data.roadAddress ||
            data.jibunAddress ||
            ''
        );

        setTimeout(() => {
          const detailInput =
            document.getElementById(
              'guest-detail-address'
            );

          if (detailInput) {
            detailInput.focus();
          }
        }, 100);
      },
    }).open();
  };

  // =========================================================
  // Daum Postcode Script 로드
  // =========================================================

  useEffect(() => {
    const existingScript =
      document.querySelector(
        'script[src*="postcode.v2.js"]'
      );

    if (existingScript) {
      return;
    }

    const script =
      document.createElement('script');

    script.src =
      '//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';

    script.async = true;

    document.body.appendChild(script);

    return () => {
      // 다른 페이지에서도 주소 검색을 사용할 수 있으므로
      // script를 제거하지 않는다.
    };
  }, []);

  // =========================================================
  // 비회원 주문 제출 + PortOne V2 카카오페이
  // =========================================================

  const handleGuestOrderSubmit = async (e) => {
    e.preventDefault();

    setGuestOrderError('');

    // =======================================================
    // 1. 입력값 검사
    // =======================================================

    if (!selectedProduct) {
      setGuestOrderError(
        '주문할 상품이 선택되지 않았습니다.'
      );

      return;
    }

    if (
      !guestNameRegex.test(
        guestName.trim()
      )
    ) {
      setGuestOrderError(
        '이름은 한글 또는 영문 2~20자로 입력해주세요.'
      );

      return;
    }

    if (!guestPhoneRegex.test(guestPhone)) {
      setGuestOrderError(
        '휴대폰 번호는 010으로 시작하는 숫자 형식으로 입력해주세요.'
      );

      return;
    }

    if (!guestZipRegex.test(guestZipcode)) {
      setGuestOrderError(
        '우편번호 5자리를 정확하게 입력해주세요.'
      );

      return;
    }

    if (!guestRoadAddress.trim()) {
      setGuestOrderError(
        '주소를 검색해주세요.'
      );

      return;
    }

    if (!guestDetailAddress.trim()) {
      setGuestOrderError(
        '상세주소를 입력해주세요.'
      );

      return;
    }

    setGuestOrderLoading(true);

    try {
      // =====================================================
      // 2. 백엔드 주문 생성
      // =====================================================

      const response = await fetch(
        '/api/orders/place',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            ordererName: guestName.trim(),
            phoneNumber: guestPhone,
            zipcode: guestZipcode,
            roadAddress:
              guestRoadAddress.trim(),
            detailAddress:
              guestDetailAddress.trim(),
            productName:
              selectedProduct.name,
            price: Number(
              selectedProduct.price || 0
            ),
          }),
        }
      );

      const orderData =
        await response.json().catch(
          () => ({})
        );

      console.log(
        '📦 주문 생성 응답:',
        orderData
      );

      if (!response.ok) {
        throw new Error(
          orderData.message ||
            '비회원 주문 생성에 실패했습니다.'
        );
      }

      if (
        orderData.status !==
        'SUCCESS'
      ) {
        throw new Error(
          orderData.message ||
            '비회원 주문 생성에 실패했습니다.'
        );
      }

      // =====================================================
      // 3. 주문 데이터 확인
      // =====================================================

      if (!orderData.merchantUid) {
        throw new Error(
          '주문번호(merchantUid)를 받지 못했습니다.'
        );
      }

      if (
        orderData.amount === undefined ||
        orderData.amount === null
      ) {
        throw new Error(
          '결제 금액(amount)을 받지 못했습니다.'
        );
      }

      const paymentId =
        String(orderData.merchantUid);

      const totalAmount =
        Number(orderData.amount);

      if (
        !Number.isFinite(totalAmount) ||
        totalAmount <= 0
      ) {
        throw new Error(
          '결제 금액이 올바르지 않습니다.'
        );
      }

      console.log(
        '💳 PortOne V2 결제 요청 준비'
      );

      console.log(
        'Store ID:',
        PORTONE_STORE_ID
      );

      console.log(
        'Channel Key:',
        PORTONE_CHANNEL_KEY
      );

      console.log(
        'Payment ID:',
        paymentId
      );

      console.log(
        'Amount:',
        totalAmount
      );

      // =====================================================
      // 4. PortOne V2 카카오페이 결제창 호출
      // =====================================================
      //
      // V2에서는
      //
      // window.IMP
      // IMP.init()
      // IMP.request_pay()
      //
      // 를 사용하지 않는다.
      //
      // PortOne.requestPayment()를 사용한다.
      //
      // 카카오페이는 payMethod: "EASY_PAY"
      // =====================================================
      
      const paymentResponse =

        await PortOne.requestPayment({
          storeId:
            PORTONE_STORE_ID,

          channelKey:
            PORTONE_CHANNEL_KEY,

          paymentId:
            `payment-${window.crypto.randomUUID()}`,

          orderName:
            selectedProduct.name,

          totalAmount:
            totalAmount,

          currency: 'KRW',

          payMethod: 'EASY_PAY',
          
          easyPay: {
            provider: "KAKAO_PAY", // 카카오페이 지정
          },

          customer: {
            fullName:
              guestName.trim(),

            phoneNumber:
              guestPhone,

            address: {
              addressLine1:
                guestRoadAddress.trim(),

              addressLine2:
                guestDetailAddress.trim(),

              postalCode:
                guestZipcode,

              countryCode:
                'KR',
            },
          },
        });

      console.log(
        '💳 PortOne V2 결제 응답:',
        paymentResponse
      );

      // =====================================================
      // 5. 결제창 호출/결제 결과 확인
      // =====================================================

      if (!paymentResponse) {
        throw new Error(
          'PortOne 결제 응답을 받지 못했습니다.'
        );
      }

      if (paymentResponse.code) {
        console.error(
          '❌ PortOne 결제 실패'
        );

        console.error(
          'code:',
          paymentResponse.code
        );

        console.error(
          'message:',
          paymentResponse.message
        );

        console.error(
          'pgCode:',
          paymentResponse.pgCode
        );

        console.error(
          'pgMessage:',
          paymentResponse.pgMessage
        );

        throw new Error(
          paymentResponse.message ||
            paymentResponse.pgMessage ||
            '결제가 실패했습니다.'
        );
      }

      // =====================================================
      // 6. V2 결제 응답 확인
      // =====================================================

      const completedPaymentId =
        paymentResponse.paymentId ||
        paymentId;

      const txId =
        paymentResponse.txId || null;

      console.log(
        '✅ PortOne 결제 요청 성공'
      );

      console.log(
        'Payment ID:',
        completedPaymentId
      );

      console.log(
        'TX ID:',
        txId
      );

      // =====================================================
      // 7. 백엔드 결제 검증
      // =====================================================
      //
      // 중요:
      // V2에서는 V1의 rsp.imp_uid를 사용하지 않는다.
      //
      // 따라서 백엔드 /api/orders/verify 역시
      // V2 paymentId 기준 검증으로 맞춰야 한다.
      //
      // 아래에서는 기존 API를 최대한 유지하면서
      // V2 데이터를 함께 전달한다.
      // =====================================================

      const verifyResponse =
        await fetch(
          '/api/orders/verify',
          {
            method: 'POST',
            headers: {
              'Content-Type':
                'application/json',
            },
            body: JSON.stringify({
              paymentId:
                completedPaymentId,

              txId: txId,

              merchantUid:
                orderData.merchantUid,

              totalAmount:
                totalAmount,
            }),
          }
        );

      const verifyData =
        await verifyResponse
          .json()
          .catch(() => ({}));

      console.log(
        '🔐 결제 검증 응답:',
        verifyData
      );

      if (!verifyResponse.ok) {
        throw new Error(
          verifyData.message ||
            '결제 검증에 실패했습니다.'
        );
      }

      // =====================================================
      // 8. 결제 완료
      // =====================================================

      alert(
        `결제가 완료되었습니다!\n주문번호: ${completedPaymentId}`
      );

      // =====================================================
      // 9. 폼 초기화
      // =====================================================

      setGuestName('');
      setGuestPhone('');
      setGuestZipcode('');
      setGuestRoadAddress('');
      setGuestDetailAddress('');
      setGuestNameMessage('');
      setGuestPhoneMessage('');
      setGuestOrderError('');

      setSelectedProduct(null);
      setIsGuestOrderFormOpen(false);
    } catch (error) {
      console.error(
        '❌ 비회원 주문 오류:',
        error
      );

      setGuestOrderError(
        error.message ||
          '주문 처리 중 오류가 발생했습니다.'
      );
    } finally {
      setGuestOrderLoading(false);
    }
  };

  // =========================================================
  // 페이지 이동
  // =========================================================

  const handlePreviousPage = () => {
    if (currentPage > 0) {
      setCurrentPage(
        (prev) => prev - 1
      );
    }
  };

  const handleNextPage = () => {
    if (
      currentPage <
      totalPages - 1
    ) {
      setCurrentPage(
        (prev) => prev + 1
      );
    }
  };

  // =========================================================
  // 렌더링
  // =========================================================

  return (
    <div className="min-h-screen bg-gray-50 font-sans">
      {/* =====================================================
          상단 헤더
      ====================================================== */}

      <header className="sticky top-0 z-40 border-b border-gray-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4">
          {/* 로고 */}

          <div
            className="cursor-pointer"
            onClick={() => {
              window.location.href = '/';
            }}
          >
            <h1 className="text-xl font-extrabold tracking-tight text-gray-800">
              🛍️ VibeCommerce
            </h1>

            <p className="mt-0.5 text-[10px] text-gray-400">
              VibeCommerce Shopping
            </p>
          </div>

          {/* 우측 메뉴 */}

          <div className="flex items-center gap-2">
            {/* 로그인 상태 */}

            {userToken ? (
              <span className="hidden rounded-full bg-blue-50 px-3 py-2 text-xs font-bold text-blue-600 sm:block">
                👤 회원
              </span>
            ) : (
              <button
                type="button"
                onClick={() => {
                  window.location.href =
                    '/login';
                }}
                className="rounded-xl px-3 py-2 text-xs font-bold text-gray-500 transition hover:bg-gray-100 hover:text-blue-600"
              >
                로그인
              </button>
            )}

            {/* 장바구니 */}

            <button
              type="button"
              onClick={() =>
                setIsCartModalOpen(true)
              }
              className="relative rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-bold text-gray-700 shadow-sm transition hover:border-blue-300 hover:bg-blue-50"
            >
              🛒 장바구니

              {cartItemCount > 0 && (
                <span className="absolute -right-2 -top-2 flex h-6 min-w-6 items-center justify-center rounded-full bg-red-500 px-1.5 text-[11px] font-extrabold text-white shadow">
                  {cartItemCount}
                </span>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* =====================================================
          메인
      ====================================================== */}

      <main className="mx-auto max-w-7xl px-4 py-8">
        {/* 페이지 제목 */}

        <div className="mb-8">
          <p className="mb-1 text-xs font-bold uppercase tracking-widest text-blue-600">
            VibeCommerce
          </p>

          <h2 className="text-3xl font-extrabold tracking-tight text-gray-900">
            상품 목록
          </h2>

          <p className="mt-2 text-sm text-gray-500">
            원하는 상품을 장바구니에 담거나
            바로 구매할 수 있습니다.
          </p>
        </div>

        {/* ===================================================
            상품 목록
        ==================================================== */}

        {loading ? (
          <div className="flex min-h-[300px] items-center justify-center">
            <div className="text-center">
              <div className="mb-3 text-4xl">
                ⏳
              </div>

              <p className="text-sm font-semibold text-gray-500">
                상품을 불러오는 중입니다...
              </p>
            </div>
          </div>
        ) : products.length === 0 ? (
          <div className="rounded-2xl border border-gray-200 bg-white py-20 text-center shadow-sm">
            <div className="mb-3 text-5xl">
              📦
            </div>

            <h3 className="text-lg font-bold text-gray-700">
              등록된 상품이 없습니다.
            </h3>

            <p className="mt-2 text-sm text-gray-400">
              현재 판매 중인 상품을 찾을 수
              없습니다.
            </p>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {products.map((product) => (
                <div
                  key={product.id}
                  className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg"
                >
                  {/* 상품 이미지 */}

                  <div className="flex h-52 items-center justify-center bg-gray-100">
                    {product.imageUrl ? (
                      <img
                        src={product.imageUrl}
                        alt={product.name}
                        className="h-full w-full object-cover"
                      />
                    ) : product.image ? (
                      <img
                        src={product.image}
                        alt={product.name}
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <span className="text-6xl">
                        📦
                      </span>
                    )}
                  </div>

                  {/* 상품 정보 */}

                  <div className="p-5">
                    <h3 className="line-clamp-2 min-h-[48px] text-base font-bold text-gray-800">
                      {product.name}
                    </h3>

                    {product.description && (
                      <p className="mt-2 line-clamp-2 min-h-[40px] text-xs leading-5 text-gray-400">
                        {product.description}
                      </p>
                    )}

                    <div className="mt-4">
                      <span className="text-xl font-extrabold text-gray-900">
                        {formatPrice(
                          product.price
                        )}
                      </span>

                      <span className="ml-1 text-sm font-semibold text-gray-500">
                        원
                      </span>
                    </div>

                    {/* 버튼 */}

                    <div className="mt-5 grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() =>
                          handleAddToCart(
                            product
                          )
                        }
                        className="rounded-xl border border-gray-200 bg-white py-3 text-xs font-bold text-gray-700 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 active:scale-95"
                      >
                        🛒 담기
                      </button>

                      <button
                        type="button"
                        onClick={() =>
                          handleImmediateBuy(
                            product
                          )
                        }
                        className="rounded-xl bg-blue-600 py-3 text-xs font-bold text-white shadow-sm transition hover:bg-blue-700 active:scale-95"
                      >
                        ⚡ 바로구매
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* =================================================
                페이지네이션
            ================================================== */}

            {totalPages > 1 && (
              <div className="mt-10 flex items-center justify-center gap-2">
                <button
                  type="button"
                  onClick={
                    handlePreviousPage
                  }
                  disabled={
                    currentPage === 0
                  }
                  className="rounded-xl border border-gray-200 bg-white px-4 py-2 text-xs font-bold text-gray-600 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  ← 이전
                </button>

                <div className="rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white">
                  {currentPage + 1}

                  {totalPages > 0 &&
                    ` / ${totalPages}`}
                </div>

                <button
                  type="button"
                  onClick={
                    handleNextPage
                  }
                  disabled={
                    currentPage >=
                    totalPages - 1
                  }
                  className="rounded-xl border border-gray-200 bg-white px-4 py-2 text-xs font-bold text-gray-600 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  다음 →
                </button>
              </div>
            )}
          </>
        )}
      </main>

      {/* =====================================================
          회원 / 비회원 선택 모달
      ====================================================== */}

      {isChoiceModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-7 shadow-2xl">
            <div className="text-center">
              <div className="text-4xl">
                ⚡
              </div>

              <h3 className="mt-3 text-xl font-extrabold text-gray-800">
                바로구매
              </h3>

              {selectedProduct && (
                <p className="mt-2 text-sm text-gray-500">
                  {selectedProduct.name}
                </p>
              )}
            </div>

            <div className="mt-7 space-y-3">
              <button
                type="button"
                onClick={
                  handleSelectMemberOrder
                }
                className="w-full rounded-xl bg-blue-600 py-4 text-sm font-bold text-white transition hover:bg-blue-700 active:scale-95"
              >
                👤 회원으로 구매하기
              </button>

              {!userToken && (
                <button
                  type="button"
                  onClick={
                    handleSelectGuestOrder
                  }
                  className="w-full rounded-xl border border-gray-200 bg-white py-4 text-sm font-bold text-gray-700 transition hover:bg-gray-50 active:scale-95"
                >
                  🛍️ 비회원으로 구매하기
                </button>
              )}

              <button
                type="button"
                onClick={() =>
                  setIsChoiceModalOpen(
                    false
                  )
                }
                className="w-full py-3 text-xs font-semibold text-gray-400 hover:text-gray-600"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================
          비회원 주문 정보 입력 모달
      ====================================================== */}

      {isGuestOrderFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 px-4 py-8">
          <div className="w-full max-w-lg rounded-2xl bg-white p-7 shadow-2xl">
            {/* 제목 */}

            <div className="mb-6">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-xs font-bold uppercase tracking-widest text-blue-600">
                    Guest Order
                  </p>

                  <h3 className="mt-1 text-xl font-extrabold text-gray-800">
                    비회원 주문
                  </h3>
                </div>

                <button
                  type="button"
                  onClick={() =>
                    setIsGuestOrderFormOpen(
                      false
                    )
                  }
                  className="rounded-lg px-3 py-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
                >
                  ✕
                </button>
              </div>

              {selectedProduct && (
                <div className="mt-4 rounded-xl bg-gray-50 p-4">
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <p className="text-xs font-bold text-gray-400">
                        주문 상품
                      </p>

                      <p className="mt-1 text-sm font-bold text-gray-800">
                        {selectedProduct.name}
                      </p>
                    </div>

                    <p className="whitespace-nowrap text-base font-extrabold text-blue-600">
                      {formatPrice(
                        selectedProduct.price
                      )}
                      원
                    </p>
                  </div>
                </div>
              )}
            </div>

            <form
              onSubmit={
                handleGuestOrderSubmit
              }
              className="space-y-5"
            >
              {/* 이름 */}

              <div>
                <label className="mb-2 block text-xs font-bold text-gray-600">
                  주문자 이름
                </label>

                <input
                  type="text"
                  value={guestName}
                  onChange={
                    handleGuestNameChange
                  }
                  placeholder="홍길동"
                  maxLength={20}
                  className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white"
                />

                {guestNameMessage && (
                  <p
                    className={`mt-2 text-xs font-semibold ${
                      guestNameRegex.test(
                        guestName
                      )
                        ? 'text-green-600'
                        : 'text-red-500'
                    }`}
                  >
                    {guestNameMessage}
                  </p>
                )}
              </div>

              {/* 전화번호 */}

              <div>
                <label className="mb-2 block text-xs font-bold text-gray-600">
                  휴대폰 번호
                </label>

                <input
                  type="tel"
                  inputMode="numeric"
                  value={guestPhone}
                  onChange={
                    handleGuestPhoneChange
                  }
                  placeholder="01012345678"
                  maxLength={11}
                  className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white"
                />

                {guestPhoneMessage && (
                  <p
                    className={`mt-2 text-xs font-semibold ${
                      guestPhoneRegex.test(
                        guestPhone
                      )
                        ? 'text-green-600'
                        : 'text-red-500'
                    }`}
                  >
                    {guestPhoneMessage}
                  </p>
                )}
              </div>

              {/* 주소 */}

              <div>
                <label className="mb-2 block text-xs font-bold text-gray-600">
                  배송 주소
                </label>

                <div className="flex gap-2">
                  <input
                    type="text"
                    value={guestZipcode}
                    readOnly
                    placeholder="우편번호"
                    className="min-w-0 flex-1 rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm outline-none"
                  />

                  <button
                    type="button"
                    onClick={
                      handleAddressSearch
                    }
                    className="whitespace-nowrap rounded-xl bg-gray-800 px-4 py-3 text-xs font-bold text-white transition hover:bg-gray-900"
                  >
                    주소 검색
                  </button>
                </div>

                <input
                  type="text"
                  value={
                    guestRoadAddress
                  }
                  readOnly
                  placeholder="도로명 주소"
                  className="mt-2 w-full rounded-xl border border-gray-200 bg-gray-100 px-4 py-3 text-sm outline-none"
                />

                <input
                  id="guest-detail-address"
                  type="text"
                  value={
                    guestDetailAddress
                  }
                  onChange={(e) =>
                    setGuestDetailAddress(
                      e.target.value
                    )
                  }
                  placeholder="상세주소 입력"
                  maxLength={100}
                  className="mt-2 w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white"
                />
              </div>

              {/* 오류 */}

              {guestOrderError && (
                <div className="rounded-xl border border-red-100 bg-red-50 p-4 text-xs font-bold leading-5 text-red-500">
                  ⚠️ {guestOrderError}
                </div>
              )}

              {/* 버튼 */}

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() =>
                    setIsGuestOrderFormOpen(
                      false
                    )
                  }
                  disabled={
                    guestOrderLoading
                  }
                  className="flex-1 rounded-xl border border-gray-200 bg-white py-3.5 text-sm font-bold text-gray-600 transition hover:bg-gray-50 disabled:opacity-50"
                >
                  취소
                </button>

                <button
                  type="submit"
                  disabled={
                    guestOrderLoading
                  }
                  className="flex-1 rounded-xl bg-blue-600 py-3.5 text-sm font-bold text-white shadow-md transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {guestOrderLoading
                    ? '주문 처리 중...'
                    : '비회원 주문하기'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* =====================================================
          장바구니 모달
      ====================================================== */}

      {isCartModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 px-4 py-8">
          <div className="w-full max-w-2xl rounded-2xl bg-white shadow-2xl">
            {/* 장바구니 헤더 */}

            <div className="flex items-center justify-between border-b border-gray-100 p-6">
              <div>
                <h3 className="text-xl font-extrabold text-gray-800">
                  🛒 장바구니
                </h3>

                <p className="mt-1 text-xs text-gray-400">
                  {userToken
                    ? '회원님의 DB 장바구니입니다.'
                    : '현재 담겨있는 상품을 확인하세요.'}
                </p>
              </div>

              <button
                type="button"
                onClick={() =>
                  setIsCartModalOpen(
                    false
                  )
                }
                className="rounded-lg px-3 py-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
              >
                ✕
              </button>
            </div>

            {/* 장바구니 내용 */}

            <div className="max-h-[60vh] overflow-y-auto p-6">
              {guestCart.length === 0 ? (
                <div className="py-16 text-center">
                  <div className="text-5xl">
                    🛒
                  </div>

                  <h4 className="mt-4 text-base font-bold text-gray-700">
                    장바구니가 비어있습니다.
                  </h4>

                  <p className="mt-2 text-xs text-gray-400">
                    상품을 담아보세요.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  {guestCart.map(
                    (item) => (
                      <div
                        key={item.id}
                        className="rounded-xl border border-gray-200 p-4"
                      >
                        <div className="flex items-center justify-between gap-4">
                          {/* 상품명 / 가격 */}

                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-bold text-gray-800">
                              {item.name}
                            </p>

                            <p className="mt-1 text-xs text-gray-400">
                              {formatPrice(
                                item.price
                              )}
                              원
                            </p>
                          </div>

                          {/* 수량 */}

                          <div className="flex items-center rounded-lg border border-gray-200">
                            <button
                              type="button"
                              onClick={() =>
                                decreaseCartQuantity(
                                  item.id
                                )
                              }
                              className="h-9 w-9 text-gray-500 transition hover:bg-gray-100"
                            >
                              −
                            </button>

                            <span className="flex h-9 min-w-10 items-center justify-center border-x border-gray-200 text-sm font-bold text-gray-700">
                              {item.quantity}
                            </span>

                            <button
                              type="button"
                              onClick={() =>
                                increaseCartQuantity(
                                  item.id
                                )
                              }
                              className="h-9 w-9 text-gray-500 transition hover:bg-gray-100"
                            >
                              +
                            </button>
                          </div>

                          {/* 상품별 금액 */}

                          <div className="hidden text-right sm:block">
                            <p className="text-sm font-extrabold text-gray-800">
                              {formatPrice(
                                Number(
                                  item.price ||
                                    0
                                ) *
                                  Number(
                                    item.quantity ||
                                      0
                                  )
                              )}
                              원
                            </p>
                          </div>

                          {/* 삭제 */}

                          <button
                            type="button"
                            onClick={() =>
                              removeFromCart(
                                item.id
                              )
                            }
                            className="rounded-lg p-2 text-gray-300 transition hover:bg-red-50 hover:text-red-500"
                            title="삭제"
                          >
                            🗑️
                          </button>
                        </div>

                        {/* 모바일 상품 금액 */}

                        <div className="mt-3 text-right sm:hidden">
                          <span className="text-sm font-extrabold text-gray-800">
                            {formatPrice(
                              Number(
                                item.price ||
                                  0
                              ) *
                                Number(
                                  item.quantity ||
                                    0
                                )
                            )}
                            원
                          </span>
                        </div>
                      </div>
                    )
                  )}
                </div>
              )}
            </div>

            {/* 장바구니 하단 */}

            {guestCart.length > 0 && (
              <div className="border-t border-gray-100 p-6">
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <p className="text-xs text-gray-400">
                      총 상품 수
                    </p>

                    <p className="mt-1 text-sm font-bold text-gray-700">
                      {cartItemCount}개
                    </p>
                  </div>

                  <div className="text-right">
                    <p className="text-xs text-gray-400">
                      총 결제 금액
                    </p>

                    <p className="mt-1 text-xl font-extrabold text-blue-600">
                      {formatPrice(
                        cartTotalPrice
                      )}
                      원
                    </p>
                  </div>
                </div>

                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={clearCart}
                    className="rounded-xl border border-red-100 bg-white px-4 py-3 text-xs font-bold text-red-500 transition hover:bg-red-50"
                  >
                    전체 삭제
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      alert(
                        userToken
                          ? '회원 장바구니 주문 API 연결 단계입니다.'
                          : '비회원 장바구니 주문 API 연결 단계입니다.'
                      );
                    }}
                    className="flex-1 rounded-xl bg-blue-600 py-3 text-sm font-bold text-white transition hover:bg-blue-700"
                  >
                    장바구니 주문하기
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}