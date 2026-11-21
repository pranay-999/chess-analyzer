import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // During development the React app runs on http://localhost:5173 (npm run dev)
    // and Spring on :8080. The proxy forwards every /api/... request to Spring,
    // so the browser thinks both live on the same server (no CORS setup needed).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // `npm run build` writes the finished site straight into Spring Boot's static
    // folder, so `mvn spring-boot:run` alone serves the whole app on :8080.
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
