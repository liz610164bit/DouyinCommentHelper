# 评论筛选助手（Android 原型）

面向 OPPO / Android 14。当前版本是**截图导入 + 中文 OCR + 关键词筛选**原型，不会登录或控制抖音，也不会自动关注、私信、抓取评论列表或绕过平台权限。

## 当前能力
- 从系统图片选择器导入评论区截图
- 使用 ML Kit 中文文字识别
- 匹配：下载、怎么下载、安装、怎么安装、注册、在哪下载
- 仅把文字中明确出现“刚刚 / N分钟前 / 0小时前”的行列为一小时内候选
- 本次运行中去重

## 重要限制
- OCR 不能保证把评论正文、作者名、时间准确分栏；截图裁切、字体、遮挡会影响识别。
- 当前时间判断是保守的文字启发式：例如“1小时前”不算一小时内；“昨天”“日期”不会纳入。
- 不能仅凭截图可靠确认作者主页、账号身份或私信状态，需人工核对。
- 不提供自动关注或批量私信功能。请遵守抖音平台规则及用户意愿。

## 用 Android Studio 构建 APK
1. 在电脑安装 Android Studio（建议稳定版）及 Android SDK 35。
2. Android Studio 选择 **Open**，打开本目录 `DouyinCommentHelper`。
3. 等待 Gradle 同步完成。
4. 选择 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
5. 生成的 APK 通常位于 `app/build/outputs/apk/debug/app-debug.apk`。
6. 将 APK 传到 OPPO 手机安装。若系统提示，允许本次安装来源安装应用。

项目使用 Android Gradle Plugin 8.7.3、Kotlin 2.0.21、compileSdk 35、minSdk 26。
