#Gomoku

一个为gomoku-vue开发的联机五子棋Java后端。

## 运行

```shell
wget https://github.com/JLUJava-S22A/gomoku-backend/releases/download/0.2-alpha/gomoku-backend.jar
java -jar gomoku-backend.jar
```

配置nginx的HTTPS本地反向代理转发请求至`localhost:16384`，并开放防火墙8443端口。

```shell
cat <<EOF > gomoku.conf
server {
        listen [::]:8443 ssl;
        listen 8443 ssl;
        server_name gomoku-server.merlyn.cc;
        ssl on;
        ssl_certificate /path/to/certificate.crt;
        ssl_certificate_key /path/to/private.key;
        location / {
                proxy_pass http://localhost:16384/;
                proxy_http_version 1.1;
                proxy_set_header Upgrade $http_upgrade;
                proxy_set_header Connection "Upgrade";
                proxy_set_header Host $http_host;
        }
}
EOF
```

启动nginx，gomoku-server开始运行。

