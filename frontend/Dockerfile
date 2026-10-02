FROM node:20.15.0-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install

COPY . .
ARG VITE_API_BASE_URL=
ARG VITE_PORTONE_STORE_ID=
ARG VITE_PORTONE_CHANNEL_KEY=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_PORTONE_STORE_ID=$VITE_PORTONE_STORE_ID
ENV VITE_PORTONE_CHANNEL_KEY=$VITE_PORTONE_CHANNEL_KEY

RUN npm run build

FROM nginx:1.27-alpine
COPY nginx/default.conf.template /opt/nginx/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.d/99-backend-proxy.sh
COPY --from=builder /app/dist /usr/share/nginx/html

RUN chmod +x /docker-entrypoint.d/99-backend-proxy.sh

ENV BACKEND_URL=
ENV PORT=80

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
