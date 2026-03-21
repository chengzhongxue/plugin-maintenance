# maintenance

开启维护模式 未登录的用户将看到维护页面

- 维护模式类型 始终，定时
- 定时模式下维护时间
- 白名单模式 登录，选择的用户

## 维护页面

![Snipaste_2026-03-21_21-39-35.webp](https://api.minio.yyds.pink/moony/files/2026/03/Snipaste_2026-03-21_21-39-35.webp)
## 主题适配

目前此插件为主题端提供了 /maintenance 路由，模板为 maintenance.html。

### 模板变量

路由信息

- 模板路径：/templates/maintenance.html
- 访问路径：/maintenance

变量

- title 标题
- description html内容