export const PLACEHOLDER_IMAGE = '/images/default-product.svg';

export function resolveImageUrl(imageUrl) {
  if (!imageUrl) {
    return PLACEHOLDER_IMAGE;
  }
  if (
    imageUrl.startsWith('http') ||
    imageUrl.startsWith('/uploads/') ||
    imageUrl.startsWith('/images/')
  ) {
    return imageUrl;
  }
  return `/uploads/${imageUrl}`;
}

export function handleProductImageError(event) {
  const image = event?.currentTarget;
  if (!image || image.dataset.placeholder === '1') {
    return;
  }
  image.dataset.placeholder = '1';
  image.src = PLACEHOLDER_IMAGE;
}

export function trackingUrl(trackingNumber) {
  if (!trackingNumber) {
    return null;
  }
  const invoice = String(trackingNumber).trim();
  if (!invoice) {
    return null;
  }
  return `https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=${encodeURIComponent(
    invoice
  )}`;
}

export function parsePriceInput(value) {
  if (value == null) {
    return 0;
  }
  const numeric = String(value).replace(/[^\d.]/g, '');
  return numeric ? Number(numeric) : 0;
}

export function formatPriceInput(value) {
  const num = parsePriceInput(value);
  if (!num) {
    return '';
  }
  return num.toLocaleString('ko-KR');
}
