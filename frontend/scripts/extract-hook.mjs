import fs from 'fs';

const src = 'c:/projects/vibe-commerce/frontend/src/pages/ProductListPage.jsx';
const text = fs.readFileSync(src, 'utf8');
const lines = text.split(/\n/);
let hook = lines.slice(0, 1670).join('\n');

hook = hook.replace(
  'export default function ProductListPage() {',
  'export default function useProductListPage() {'
);

hook = hook.replace(
  `const PORTONE_STORE_ID =
    'store-22346845-e8d2-4963-bc8a-c5057af09053';`,
  `const PORTONE_STORE_ID =
    import.meta.env.VITE_PORTONE_STORE_ID ||
    'store-22346845-e8d2-4963-bc8a-c5057af09053';`
);

hook = hook.replace(
  `const PORTONE_CHANNEL_KEY =
    'channel-key-6c90ad46-b43b-4b03-9b77-2e875b45d99c';`,
  `const PORTONE_CHANNEL_KEY =
    import.meta.env.VITE_PORTONE_CHANNEL_KEY ||
    'channel-key-6c90ad46-b43b-4b03-9b77-2e875b45d99c';`
);

hook += `

  const [searchKeyword, setSearchKeyword] = useState('');

  const handleSearchProducts = (e) => {
    if (e && e.preventDefault) {
      e.preventDefault();
    }
    setCurrentPage(0);
    fetchProducts();
  };

  return {
    products,
    currentPage,
    totalPages,
    loading,
    isChoiceModalOpen,
    setIsChoiceModalOpen,
    isGuestOrderFormOpen,
    setIsGuestOrderFormOpen,
    selectedProduct,
    guestName,
    guestPhone,
    guestZipcode,
    guestRoadAddress,
    guestDetailAddress,
    setGuestDetailAddress,
    guestNameMessage,
    guestPhoneMessage,
    guestOrderError,
    guestOrderLoading,
    guestCart,
    isCartModalOpen,
    setIsCartModalOpen,
    selectedCartIds,
    setSelectedCartIds,
    checkoutItems,
    checkoutMode,
    memberOrderLoading,
    guestNameRegex,
    guestPhoneRegex,
    userToken,
    cartItemCount,
    checkedCartItems,
    checkedCartTotalPrice,
    formatPrice,
    handleLogout,
    handleAddToCart,
    increaseCartQuantity,
    decreaseCartQuantity,
    removeFromCart,
    clearCart,
    toCheckoutItem,
    buildOrderName,
    handleImmediateBuy,
    handleSelectMemberOrder,
    handleSelectGuestOrder,
    handleCartOrder,
    handleGuestNameChange,
    handleGuestPhoneChange,
    handleAddressSearch,
    handleGuestOrderSubmit,
    handlePreviousPage,
    handleNextPage,
    searchKeyword,
    setSearchKeyword,
    handleSearchProducts,
    setCurrentPage,
  };
}
`;

fs.mkdirSync('c:/projects/vibe-commerce/frontend/src/hooks', { recursive: true });
fs.writeFileSync(
  'c:/projects/vibe-commerce/frontend/src/hooks/useProductListPage.js',
  hook
);
console.log('written', hook.split('\n').length);
