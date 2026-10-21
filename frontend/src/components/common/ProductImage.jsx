import React, { useState } from 'react';
import { resolveImageUrl } from '../../utils/media';

export default function ProductImage({ src, alt = '상품', className = '' }) {
  const [broken, setBroken] = useState(false);
  const resolved = src ? resolveImageUrl(src) : '';

  if (broken || !resolved) {
    return (
      <div
        className={`flex items-center justify-center bg-slate-200 text-slate-500 ${className}`}
        role="img"
        aria-label="NO IMAGE"
      >
        <span className="px-1 text-center text-sm font-semibold leading-tight">
          NO IMAGE
        </span>
      </div>
    );
  }

  return (
    <img
      src={resolved}
      alt={alt}
      className={className}
      onError={() => setBroken(true)}
    />
  );
}
