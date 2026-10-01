import React from 'react';
import AdminProductSalesPanel from './AdminProductSalesPanel';
import AdminInquiryOverview from './AdminInquiryOverview';

export default function AdminAnalyticsTab() {
  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
      <AdminProductSalesPanel />
      <AdminInquiryOverview />
    </div>
  );
}
