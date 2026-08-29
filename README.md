# MyPackage 我的快递

一款本地化的快递驿站管理 Android 应用：把每个驿站（取件点）的取件码链接、柜号范围、驿站照片集中管理，一键直达对应 App 的取件码页面，并支持通过二维码在设备间迁移全部配置。

## 功能

- **驿站卡片**：图片铺满卡片、名称叠印左上角、下方平台徽章 + 柜号范围；单列/双列视图可切换并记忆
- **一键取件码**：按平台 scheme / App Links 依次尝试直达拼多多、淘宝、菜鸟取件码页，失败自动降级（浏览器 → 复制链接）
- **二维码图片识别**：上传含取件码二维码的图片（相册/拍摄）自动提取原始链接
- **管理**：增删改、上移/下移、长按宫格菜单（改名 / 换图 / 移动 / 复制链接 / 浏览器打开）；编辑保存不改变原位置
- **动态二维码分享**：驿站配置（含 base64 压缩图片）序列化为 JSON，单码固定控制在 800 字节内、超出自动分片；多片以动态轮播方式循环展示（可加减速），对端「扫码导入」持续扫描、实时显示进度百分比，集齐后自动合并还原
- **备份与迁移**：JSON 导出 / 导入（图片同样以 base64 内嵌）
- **检查更新**：基于 GitHub Releases
- **体验**：灵动岛风格状态胶囊、页面转场、卡片错峰入场等动效；适配高刷新率、小窗与折叠屏（宽屏自动双列）

数据全部保存在应用私有目录（Room + 本地图片），无 Root、不上传服务器；唯一的联网场景是你手动点击「检查更新」访问 GitHub API。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Room · Navigation Compose · Coil · ZXing（生成 + 图片识别 + 扫码）· kotlinx.serialization

## 构建

- Android Studio Ladybug+ 或命令行
- JDK 17+，Android SDK 36

```bash
# 在项目根目录创建 local.properties 指向你的 SDK
# sdk.dir=<你的 Android SDK 路径>
./gradlew assembleRelease   # 或使用本地 Gradle 8.10+：gradle assembleRelease
```

Release 构建使用 debug keystore 签名，可直接安装体验。

## 下载

前往 [Releases](https://github.com/Thatgfsj/MyPackage/releases) 获取最新 APK。

## License

[MIT](LICENSE)
