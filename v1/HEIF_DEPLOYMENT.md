# HEIC/HEIF 上传部署说明

后端保持 Java 17，通过 `libheif` 提供的 `heif-convert` 解码 HEIC/HEIF，
然后沿用现有图片处理流程生成 WebP 主图和缩略图。

## 生产部署前置条件

部署新版 JAR 前，必须先在 Linux 宿主机安装解码器：

```bash
# Debian / Ubuntu
sudo apt-get update
sudo apt-get install libheif-examples

# Ubuntu 24.04 将 HEVC 解码器拆分成独立插件；如果软件源中存在该包，必须一并安装
if apt-cache show libheif-plugin-libde265 >/dev/null 2>&1; then
  sudo apt-get install libheif-plugin-libde265
fi

heif-convert --version
```

其他 Linux 发行版应安装包含 `heif-convert` 的 `libheif` 工具包。建议使用
仍接受安全更新的 `libheif` 版本，并确认同时提供 HEVC/H.265 解码插件。仅执行
`heif-convert --version` 不能证明 HEIC 解码可用，部署后仍须用真实 HEIC 文件验证。

如果命令不在 `PATH` 中，可配置：

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `HEIF_CONVERTER_COMMAND` | `heif-convert` | 可执行文件名或绝对路径 |
| `HEIF_CONVERTER_TIMEOUT_SECONDS` | `30` | 单张图片转换超时秒数 |
| `MAX_CONCURRENT_HEIF_CONVERSIONS` | `1` | 同时进行的转换数；2 核 2 GB 服务器建议保持 1 |
| `MAX_HEIF_DECODED_BYTES` | `268435456` | 解码后临时 PNG 的最大字节数 |

JPEG、PNG、WebP 仍使用 ImageIO，不依赖 `heif-convert`。未安装或配置错误时，
这些格式不受影响，HEIC/HEIF 上传会返回服务器配置错误并清理暂存记录。

## 上线验证

1. 在生产宿主机执行 `heif-convert --version`。
2. 先上传一张 JPEG 和一张 PNG，确认原有流程正常。
3. 分别上传真实手机生成的 `.heic` 和 `.heif` 文件。
4. 确认接口返回 `.webp` 文件名，商品图同时生成 `_thumb.webp`。
5. 确认 MinIO 中没有残留 `.heic`、`.heif` 或临时 PNG。

回滚时只需恢复旧 JAR；安装的 `libheif` 工具不会影响旧版本运行。
