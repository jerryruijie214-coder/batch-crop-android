# 批量裁剪 Android

一款轻量的安卓批量图片裁剪工具。一次选择多张同规格图片，只需在参考图上框选一套裁剪区域，即可把所有图片一次性裁好，结果自动保存到相册。

## 功能

- **批量选图**：使用系统照片选择器，无需存储权限（Android 10+），可反复追加选择
- **自定义份数**：每张图片可裁成 1–9 份，每份单独框选区域
- **仿系统相册裁剪**：拖动四角/四边调整范围，双指缩放图片精细定位，实时显示区域像素尺寸
- **一键套用**：裁剪区域按比例归一化，自动适配所有图片的尺寸
- **自动旋正**：读取 EXIF 方向信息，竖拍照片不会歪
- **批量保存**：结果写入相册 `Pictures/批量裁剪` 文件夹，并在结果页预览
- **Material 3**：支持深色模式与全面屏（Edge-to-Edge）适配

## 使用流程

1. 首页点击「选择图片」，可多选（可多次追加）
2. 设置每张图片要裁成几份
3. 进入裁剪页，逐份拖动框选区域（底部 `1 2 3…` 切换）
4. 点击「开始裁剪」，等待进度完成后在结果页查看
5. 裁剪结果已保存到相册「批量裁剪」文件夹

## 技术栈

- Java 17
- Android Gradle Plugin 8.9.2 / Gradle 8.14.6
- minSdk 26 / targetSdk 35 / compileSdk 35
- AndroidX AppCompat + Material Components 1.12（Material 3）
- 自定义 View 实现裁剪取景器（[CropEditorView.java](app/src/main/java/com/trae/batchcrop/view/CropEditorView.java)）
- MediaStore 写入相册，兼容 Android 8/9 的旧存储模型

## 构建

```bash
git clone https://github.com/<your-name>/batch-crop-android.git
cd batch-crop-android
./gradlew assembleDebug
```

或将项目直接用 Android Studio 打开运行。首次构建前 Android Studio 会自动生成 `local.properties`。

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 测试

包含 3 个 Robolectric 启动冒烟测试，覆盖 Application 与三个界面的创建和布局解析：

```bash
./gradlew testDebugUnitTest
```

## 目录结构

```
app/src/main/java/com/trae/batchcrop/
├── MainActivity.java          # 选图、设置份数
├── CropActivity.java          # 逐份定义裁剪区域、触发批量处理
├── ResultActivity.java        # 结果预览
├── adapter/                   # 列表适配器
├── util/
│   ├── BatchCropper.java      # 后台批量裁剪
│   ├── ImageUtils.java        # 解码、EXIF 校正、相册保存
│   ├── ThumbnailLoader.java   # 缩略图异步加载
│   ├── GridSpacingItemDecoration.java
│   └── UiUtils.java           # 全面屏适配
└── view/
    └── CropEditorView.java    # 裁剪取景器
```

## 说明

- 系统照片选择器有单次选择数量上限（通常 150 张，取决于系统版本），可分多次选择累加
- 超过 4096px 的超大图片会先降采样再裁剪，以控制内存占用

## License

[MIT](LICENSE)
