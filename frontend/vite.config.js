import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react' // ✨ 명칭을 @vitejs/plugin-react 로 변경해 주세요!

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000, // Vite 개발 서버 포트를 3000번으로 고정
    proxy: {
      // 프론트엔드에서 /api로 시작하는 요청을 백엔드 스프링부트(8080)로 리다이렉트
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    }
  }
})
