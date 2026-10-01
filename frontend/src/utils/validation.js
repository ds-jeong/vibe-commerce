import { resolveImageUrl } from './media';

export const EMAIL_REGEX =
  /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
export const NAME_REGEX = /^[가-힣a-zA-Z\s]{2,20}$/;
export const PHONE_REGEX = /^010\d{7,8}$/;
export const ZIP_REGEX = /^\d{5}$/;
export const PW_REGEX =
  /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,16}$/;

export const ORDER_STATUS_LABEL = {
  ORDERED: '결제완료',
  PAID: '결제완료',
  PREPARING: '배송준비중',
  SHIPPING: '배송중',
  DELIVERING: '배송중',
  DELIVERED: '배송완료',
  CANCELLED: '취소완료',
  RETURN_REQUESTED: '반품신청',
  RETURNED: '반품완료',
  REFUND_REQUESTED: '반품신청',
  REFUNDED: '반품완료',
};

export const INQUIRY_STATUS_LABEL = {
  PENDING: '대기',
  COMPLETED: '완료',
  ANSWERED: '답변완료',
};

export const canUserCancel = (status) =>
  status === 'ORDERED' || status === 'PAID' || !status;

export const isPreparingOrLater = (status) =>
  [
    'PREPARING',
    'SHIPPING',
    'DELIVERING',
    'DELIVERED',
    'RETURN_REQUESTED',
    'RETURNED',
  ].includes(status);

export const canRequestReturn = (status) => status === 'DELIVERED';

export const canTrackOrder = (status) =>
  status === 'SHIPPING' || status === 'DELIVERED' || status === 'DELIVERING';

export const productImageSrc = (product) =>
  resolveImageUrl(product?.imageUrl || product?.image);
