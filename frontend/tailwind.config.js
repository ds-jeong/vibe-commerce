/** @type {import('tailwindcss').Config} */
export default {
    // ✨ 중요: 수동으로 만든 모든 리액트 파일(JSX)들의 디자인 명령어를 샅잡아 컴파일하라는 핵심 추적 명세
    content: [
      "./index.html",
      "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
      extend: {},
    },
    plugins: [],
  }
  