FROM nginx:alpine

# nginx.conf ve Dockerfile imaj icine kopyalanmaz: yalnizca public/ servis edilir.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY public/ /usr/share/nginx/html/

EXPOSE 80
