## 客户端入口与当前开发状态

**Android 原生端（`app/`）是当前重构主线**：Jetpack Compose + CameraX + Kotlin 图像处理 + SQLite 本地图像库。Android 与 Web 为两个独立客户端，不是 WebView 套壳。已合并内容以 `main` 为准；新算法通过独立 PR 合并，构建状态以对应 GitHub Actions 为准。

`web/` 是另一套可运行的 HTML/CSS/JavaScript 浏览器端，保留已确认的视觉基础、自动处理、基础实时调节、右下角对比工具与本地记录。**本次新增的 Android CLAHE、双边滤波、专业曲线及图片库，尚未同步到 Web 图像算法内核**，两端功能暂不完全一致。

```bash
cd web
npm run dev
```

无需安装 npm 依赖。测试、构建、实际算法范围和真机验收说明见 [`web/README.md`](web/README.md)。本次未发布在线托管；CLAHE、同态和运动模糊反卷积仍未接入该 Web 前端。

以下保留仓库原有 Android 工程说明，它与 `web/` 分别运行。

---

<div align="center">

# 🌙 NightVision-DIP · 夜视大师

> 基于 Android 的低照度图像增强与复原课程项目，围绕传统数字图像处理算法构建可交互实验流程。


**基于传统数字图像处理的 Android 低照度图像增强与复原系统**  
*Low-Light Image Enhancement & Restoration on Android*

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Stage](https://img.shields.io/badge/Stage-S1%20%7C%20Native%20MVP-blue)
![License](https://img.shields.io/badge/License-TBD-lightgrey)

[项目简介](#-项目简介) · [功能规划](#-功能规划) · [技术架构](#-技术架构) · [快速开始](#-快速开始) · [算法与实验](#-算法与实验) · [团队协作](#-团队协作)

</div>

---

## 项目导航

- [📖 项目简介](#-项目简介)
- [✨ 功能规划](#-功能规划)
- [🧩 技术架构](#-技术架构)
- [🛠️ 快速开始](#-快速开始)
- [🔬 算法与实验](#-算法与实验)
- [👥 团队协作](#-团队协作)
- [🗺️ 开发里程碑](#-开发里程碑)
- [📄 开源与使用说明](#-开源与使用说明)

## 📖 项目简介

**NightVision-DIP（夜视大师）** 是《数字图像处理》课程设计项目，聚焦手机夜间拍照中常见的**亮度不足、噪声、运动模糊和色彩失真**问题。Android 原生端采用 **Kotlin + Jetpack Compose**。当前使用系统照片选择器和应用内 CameraX 拍摄，图像在设备本地由 Kotlin 像素处理核心计算；不通过 WebView 加载手机网页，也不上传照片。

项目重点不是一个简单的「提亮滤镜」，而是理解、实现并实验验证**空间域增强、频率域处理、退化建模、图像复原和彩色图像处理**等方法。

> **原生端当前状态（专业编辑器阶段）：** 已接入 Compose 页面、相册与 CameraX 拍摄、自动增强、专业影调及 HSL、RGB 曲线、直方图均衡化、CLAHE、明度均值/高斯/中值/双边滤波、色度降噪、原图对比、SQLite 图片库、处理记录与导出。Android 原生端已接入已知匀速直线运动 PSF 的 Wiener 频域复原，支持手动指定长度、方向、正则化 K 与复原强度；在受限工作分辨率估计亮度校正场并回写原分辨率，保留原图色彩与细节残差，但不是对每个原始高分辨率像素执行完整反卷积，极细小拖影恢复能力有限。混合/非线性/未知 PSF 尚不覆盖。**同态滤波、Retinex 和完整参考图 PSNR/SSIM 实验仍未接入。** 本次开发分支 Android CI 已通过单元测试与 Debug APK 构建；这不代表真机交互或真实夜景画质实测通过。

### 设计原则

- **经典算法优先**：课程项目不使用深度学习增强或复原。
- **先原理后实现**：每个算法尽量保留数学模型、参数含义、对照实验和局限性。
- **可复现性**：记录原图、退化方式、随机种子、滤波参数和评价结果。
- **端侧处理**：计划在 Android 手机本地计算，不必把用户照片上传到服务器。
- **按需组合**：并非无条件串行叠加所有滤波器，避免噪声放大与细节损失。

## ✨ 功能规划

| 模块 | 具体内容 | 状态 |
|---|---|---|
| Android 基础工程 | Kotlin、Jetpack Compose、Gradle | ✅ 已创建并通过本地 Debug 构建 |
| 摄像头与相册 | CameraX 应用内拍摄、系统照片选择器 | ✅ 已接入，待真机验收 |
| 自动退化分析 | 平均亮度、暗部比例、通道均值、平坦区域残差参考 | ✅ 基础分析已实现 |
| 空间域增强 | 曝光、亮度、对比度、分区高光/阴影、Gamma、全局直方图均衡、CLAHE、曲线 | ✅ 已接入算法与界面 |
| 彩色空间增强 | HSL 色相通道调整、YCbCr 色度降噪、灰度世界白平衡 | ✅ 基础路径已实现；完整颜色空间实验待扩充 |
| 噪声建模与去噪 | 明度 3×3 均值/高斯/中值、明度双边滤波、YCbCr 色度平滑 | ✅ 已实现；可复现噪声合成实验待接入 |
| 频率域增强 | FFT、低通 / 高通、同态滤波 | ⏳ 待实现 |
| 图像复原 | 已知匀速直线运动 PSF、Wiener 频域亮度复原、全分辨率细节残差回写 | ✅ 算法与手动参数已接入；复杂 PSF 与真机画质待验证 |
| 彩色恢复 | 灰度世界白平衡、饱和度、色温 | ✅ 基础处理已实现 |
| 自动处理 | 根据亮度统计匹配亮度、对比度和降噪参数 | ✅ 已实现 |
| 可视化与质量评价 | 固定原图预览、长按原图、分屏对比、亮度直方图、处理步骤及耗时 | ✅ 已实现基础展示；量化实验评价待接入 |
| 结果导出 | JPEG 导出、SQLite 图片库、收藏/筛选、处理记录和完整参数回填 | ✅ 已实现，待真机验证 |

### 课程设计要求映射

1. **退化原因分析**：分析低照度噪声类型、光照不足及运动模糊。
2. **颜色空间**：转换到 HSV / YCbCr，在亮度通道增强。
3. **空间域**：直方图均衡和自适应对比度增强。
4. **频率域**：同态滤波或低通 / 高通组合。
5. **图像复原**：建立运动模糊退化模型并进行复原。
6. **彩色处理**：颜色恢复和白平衡校正。
7. **效果评价**：目标可辨识度、噪声水平及视觉质量分析。

## 🧩 技术架构

| 层次 | 技术 / 规划 |
|---|---|
| Android 应用 | Kotlin、Jetpack Compose（已建立） |
| 图像输入 | CameraX / Android Photo Picker（已接入） |
| 图像处理 | Kotlin 像素处理核心（已接入）；当前未使用 OpenCV |
| 数据形式 | Bitmap / PixelImage / SQLite 元数据（已接入） |
| 部署 | Android 本地运行；不上传图片到云服务 |

```text
CameraX / Gallery
        |
        v
  Bitmap / Mat
        |
        v
  图像质量分析
        |
        v
  按需选择增强策略
   |-- 空间域亮度增强（Gamma / CLAHE）
   |-- 噪声抑制与频域增强
   |-- 运动模糊复原
   |-- 彩色空间处理与白平衡
        |
        v
  处理步骤 + 指标 + 原图对比
        |
        v
     保存结果
```

## 🛠️ 快速开始

### 开发环境

- Android Studio：能够打开并构建本仓库 Android Gradle 工程
- Android SDK：当前项目设置为 `minSdk = 29`、`compileSdk = 36`、`targetSdk = 36`
- Gradle JDK：使用 Android Studio 为当前 Gradle 工程配置的兼容 JDK

### 获取源码并构建

```bash
git clone https://github.com/Echo-cln/NightVision-DIP.git
cd NightVision-DIP
```

使用 Android Studio 打开仓库根目录，等待 Gradle Sync，然后执行：

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
```

成功构建后 Debug APK 通常位于 `app/build/outputs/apk/debug/`。**构建成功不等于真机、摄像头、图像算法已经通过测试。**


## Android 原生迁移进度

原生端独立运行于 `app/`，与 `web/` 是两个客户端。**Android 不使用 WebView，不把 HTML 页面套壳。**

| 已落地模块 | 实现 |
|---|---|
| 页面 | 首页、低照度增强、图像复原、图片库、处理记录、我的 |
| 图片输入 | Android Photo Picker、CameraX 应用内相机 |
| 图像处理 | 纯 Kotlin 像素缓冲区；在 `Dispatchers.Default` 后台执行 |
| 算法 | 曝光/阴影/高光、Gamma、直方图均衡、CLAHE、RGB 曲线、HSL、白平衡、高斯/中值/双边明度降噪及色度平滑 |
| 交互 | 图片固定预览、长按原图、拖动分屏比较、五类专业参数面板及曲线编辑 |
| 分析 | 平均亮度、暗部比例、亮度直方图、处理步骤和耗时 |
| 本地数据 | SQLite 图片库、原始图片独立副本、收藏与搜索；最近 20 条处理记录及参数 JSON |
| 导出 | 保存到系统相册 `Pictures/NightVision` |

### 尚未实现（不得在演示或报告中写成已完成）

- Retinex、同态滤波、独立低通/高通频域处理、非局部均值去噪。
- 未知/空间变化 PSF 估计、非均匀运动去模糊与多帧复原。
- 严格的 HSV/YCbCr 可切换色彩空间实现。
- 同场景配准参考图的 PSNR/SSIM、ROI 对比和频谱分析页面。

### 构建与验证

在 Android Studio 打开仓库根目录，执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions 在 `feat/android-native` 分支推送时执行单元测试和 Debug APK 构建，并保存 APK artifact。CI 通过不等于完成真机相机权限、不同厂商设备、长图内存和触摸交互验收。

## 🔬 算法与实验

课程设计将同时考虑两类实验：

**1. 受控退化（有参考真值）**

以清晰参考照片为基准，人工加入已知强度的高斯 / 椒盐噪声或已知参数的运动模糊，记录随机种子和退化参数；使用原图作为真值，计算处理前后相对于真值的 PSNR、SSIM，并比较不同算法与参数的优缺点。

**2. 真实夜景（无参考真值）**

使用手机拍摄的低照度图像，结合亮度分布、噪声估计、目标与边缘可辨识度、主观观察以及耗时进行评价。**不能仅计算低照度原图与增强结果之间的 PSNR / SSIM，就断言增强质量提高。**

计划保留算法中间结果、参数记录、对比图和实验报告。**目前尚未生成真实的实验数据与处理效果图。**

## 👥 团队协作

本课程项目由两名成员协作；**两人都独立承担数字图像处理算法，并共同参与 Android 集成与实验验证**。

| 成员 | 重点算法职责 | 共同工作 |
|---|---|---|
| A | 亮度与对比度分析、Gamma / CLAHE、HSV、同态滤波 | Android 集成、测试、报告与答辩 |
| B | 噪声分析与去噪、运动模糊与维纳复原、YCbCr 与白平衡、质量评价 | Android 集成、测试、报告与答辩 |

**分支：** `main` 保存稳定版本；成员 B 当前开发分支为 `b-denoise`，其他算法功能从 `main` 派生独立分支，经测试后通过 Pull Request 合并。

## 🗺️ 开发里程碑

- [x] **S0-1**：创建 Android 工程并通过 Debug 构建
- [x] **S0-2**：README、独立开发分支、GitHub Actions 构建流程
- [x] **S1-1**：原生首页/编辑/记录页面、系统照片选择器与相机入口
- [x] **S1-2**：Kotlin 像素处理、亮度/对比度/色彩调节、高斯/中值滤波
- [x] **S1-3**：原图切换、拖动对比、直方图、本地记录与相册导出
- [ ] **S1-4**：EXIF 方向适配、参考图 PSNR/SSIM、ROI 与频谱观察
- [x] **S2-1**：CLAHE、HSL/曲线与双边滤波原生算法及单元测试
- [x] **S2-2**：已知线性运动 PSF 的 Wiener 频域复原及水平/垂直合成退化测试
- [ ] **S2-3**：同态滤波、Retinex 与复杂/未知 PSF 估计
- [ ] **S3**：跨设备性能/鲁棒性测试、算法对照实验与质量评价
- [ ] **S4**：实验报告、演示视频与答辩材料

## 📄 开源与使用说明

仓库目前是公开可见的课程开发仓库，**尚未确定开源许可证**，后续需经团队讨论决定。仓库公开不等于默认授予复制、修改和分发许可。

本项目以课程学习和实验展示为目的，不承诺达到商业手机多帧夜景模式的成像质量，也不能恢复已经完全缺失的场景信息。

---

<div align="center"><sub>NightVision-DIP · Build it. Measure it. Understand it.</sub></div>


### Android 明度均值滤波
新增 3×3 明度均值滤波，复制边界，保留 alpha；沿用降噪强度和细节参数，方法名 MEAN 随参数 JSON 保存并回填。真机需验证：选择均值后强度 0/50/100 的差异、保存记录重启回填、边缘软化及彩色高光裁剪。均值会模糊边缘，不属于运动模糊复原。

### Android Wiener 运动模糊复原
使用纯 Kotlin 二维 FFT 与中心化均匀线段 PSF 频率响应，按 `H* / (|H|² + K)` 进行 Wiener 反卷积。支持用户指定原图模糊长度、方向角、正则化 K 与强度；工作分辨率上限 896 px，回写亮度校正场并保留原图尺寸。CI 合成数据测试不替代真机真实拖影测试；未知 PSF、空间变化模糊和极细小高分辨率拖影仍是限制。
