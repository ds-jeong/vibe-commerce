import React, { useEffect, useState } from 'react';
import { subscribeLoadingBar } from '../../utils/loadingBar';

export default function LoadingBar() {
  const [active, setActive] = useState(false);

  useEffect(() => {
    const onEvent = (event) => {
      setActive(Boolean(event.detail?.active));
    };
    window.addEventListener('vc-loading-bar', onEvent);
    const unsubscribe = subscribeLoadingBar(setActive);
    return () => {
      window.removeEventListener('vc-loading-bar', onEvent);
      unsubscribe();
    };
  }, []);

  if (!active) {
    return null;
  }

  return (
    <div className="vc-loading-bar" aria-hidden="true">
      <div className="vc-loading-bar-run" />
    </div>
  );
}
