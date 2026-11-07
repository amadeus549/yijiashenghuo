# 逸家生活 · 前端（H5）

Vue 2 + Element UI 的移动端静态站点，无需构建，直接由 Nginx 托管。

## 页面

| 文件 | 说明 |
|---|---|
| `index.html` | 首页：分类入口 + 服务笔记瀑布流 |
| `provider-list.html` | 服务商列表：GEO 附近、按距离排序、分页 |
| `provider-detail.html` | 服务商详情：套餐、限时抢购、服务评价 |
| `note-detail.html` | 服务笔记详情：点赞、评价（可发布 / 回复 / 删除） |
| `note-edit.html` | 发布服务笔记（含图片上传） |
| `order.html` | 我的订单：待支付 / 已支付 / 已取消，支持支付与取消 |
| `info.html` / `info-edit.html` | 个人主页：我的笔记、我的评价、我的订单入口 |
| `login.html` | 验证码登录 |

## 运行

把本目录放到 Nginx 的 `html/yijia` 下，参考 `nginx.conf`：

```nginx
server {
    listen 8088;
    location / { root html/yijia; index index.html; }
    location /api {
        rewrite /api(/.*) $1 break;
        proxy_pass http://127.0.0.1:8083;
    }
}
```

所有接口请求走 `/api` 前缀，由 `js/common.js` 统一配置；登录 token 存在 `sessionStorage`。

## 图片

`imgs/notes/**` 是演示数据的图片资源，后端种子数据（`../backend/src/main/resources/db/yijia-seed.sql`）按相对路径引用。
如果用后台上传图片，把后端的 `yijia.upload-dir` 指向本目录的 `imgs/` 即可。
