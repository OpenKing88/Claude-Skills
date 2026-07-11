[← 缓存机制](cache-mechanism.md) | [→ UI 组件](ui-components.md)

# 图片处理规范

KYC 流程中涉及图片上传的场景（身份证、人脸识别等）需要**统一图片处理策略**。

### 图片压缩（强制要求）

**所有用户上传的图片必须经过压缩处理**，禁止直接上传原图：

| 场景 | 建议压缩后大小  | 建议质量     | 建议分辨率 |
|------|----------|----------|-----------|
| 身份证照片 | ≤ 500KB  | 80%      | ≤ 1920px 长边 |
| 人脸照片 | ≤ 500KB  | 80%      | ≤ 1280px 长边 |
| 活体检测截图 | 按 SDK 要求 | 按 SDK 要求 | 按 SDK 要求 |

**压缩策略**：
1. 先按目标分辨率缩放（保持宽高比）
2. 再按目标质量压缩（JPEG）
3. 若仍超过目标大小，循环降低质量直到满足要求
4. 压缩失败时降级：优先保证尺寸限制，其次保证质量

**压缩失败处理**：
```kotlin
val bytes = ImageCompressUtil.compress(imageUri, context)
    ?: throw IllegalStateException("图片压缩失败")
// 压缩失败走错误恢复流程，绝不 fallback 原图字节（避免大图 readBytes 触发 OOM）
```

### 图片选择 API 统一

**选择图片和拍照必须统一调用方式**，项目中只应存在一套图片获取逻辑：

```
统一封装：ImagePicker

方法：
  - pickFromCamera()      — 调起相机拍照
  - pickFromGallery()     — 调起系统图库
  - pickWithChoice()      — 弹出选择（拍照 / 图库）

返回：
  - URI / FilePath / Bitmap（统一为项目约定的类型）
  - 错误信息（权限拒绝、取消选择、读取失败等）
```

**禁止做法**：
- ❌ 不同页面使用不同的图片选择库或 API
- ❌ 身份证页面用 CameraX，人脸识别页面用系统相机
- ❌ 图库选择在不同页面使用不同的 Intent 方式

### 图片处理流程（标准）

```
1. 用户触发上传（点击上传区域 / 开始验证）
   ↓
2. 调起统一 ImagePicker（拍照 / 图库）
   ↓
3. 获取原始图片 URI
   ↓
4. 读取图片元信息（宽高、大小、方向）
   ↓
5. 按场景策略压缩（缩放 + 质量压缩）
   ↓
6. 校正图片方向（处理手机旋转 EXIF）
   ↓
7. 生成压缩后的图片文件 / 字节数组
   ↓
8. 上传压缩后的图片到后端
```

### 图片缓存策略

- 压缩后的图片通常不需要本地长期缓存（后端存储后清理）
- 身份证页面的照片预览可以临时缓存，但退出页面后释放
- 人脸识别的照片不缓存，直接上传后丢弃

### 图片回显：使用三方框架加载 URI（强制建议）

KYC 流程中拍照或图库选择后，页面上通常需要**回显预览图片**。**强烈建议使用 Coil / Glide 等成熟的图片加载框架直接通过 URI 加载**，而不是自行解码 Bitmap。

**原因**：
- 自行 `BitmapFactory.decodeStream(uri)` 加载高分辨率原图极易触发 OOM
- 三方框架内置了采样缩放（`inSampleSize`）、内存缓存、Bitmap 复用池、生命周期感知取消等机制
- 即使已经用 `ImageCompressor` 压缩过，压缩后的文件也可能有几百 KB，直接 decode 为 Bitmap 仍可能占用大量内存（500KB JPEG 解码后可达 ~10MB ARGB8888）

**推荐用法**：

```kotlin
// Coil（推荐 — 轻量、Kotlin 优先、协程原生支持）
// 在 ImageView 中加载 URI 预览
imageView.load(uri) {
    crossfade(true)
    size(1024, 1024)  // 限制解码尺寸，避免全分辨率加载
    memoryCachePolicy(CachePolicy.DISABLED)  // 预览类图片不建议占内存缓存
}

// Glide（备选 — 功能更全、Java 生态兼容性好）
Glide.with(context)
    .load(uri)
    .override(1024, 1024)  // 限制尺寸
    .skipMemoryCache(true) // 预览图片不占内存
    .into(imageView)

// 添加依赖（任选其一）：
// implementation("io.coil-kt:coil:2.x")
// implementation("com.github.bumptech.glide:glide:4.x")
```

**禁止做法**：
```kotlin
// ❌ 自行 decode URI 加载原图 — 极易 OOM
val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
imageView.setImageBitmap(bitmap)

// ❌ 即使读了 bounds 手动采样，也容易出 bug（旋转、EXIF、内存计算误差等）
val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
val bitmap = BitmapFactory.decodeStream(inputStream, null, opts)
imageView.setImageBitmap(bitmap)
```

**特别注意**：压缩是给**上传**用的（需要 ≤500KB 的 JPEG 文件），回显是给**展示**用的（需要缩放后的 Bitmap 给 ImageView）。两者目的不同，不要混用 —— 压缩文件可以继续传给 Coil/Glide 加载，框架会自动做展示级别的缩放。

---

## 拍照/图库选择参考实现

以下代码来自实际项目，供实现时参考。**使用前必须对照项目实际情况检查内存风险和线程安全**。

### 自定义相机（CameraX + 后置摄像头）

```kotlin
/**
 * 自定义相机页面，用于身份证拍照。
 * 使用 CameraX 后置摄像头预览，蒙层抠孔取景框 + 拍照后通过 FileProvider 返回 URI。
 *
 * 【内存/ANR 检查要点】
 * 1. cameraExecutor 使用 newSingleThreadExecutor，拍照回调在子线程，不会阻塞主线程
 * 2. ProcessCameraProvider 通过 addListener 异步获取，主线程回调
 * 3. onDestroy 时必须 shutdown() executor，否则线程泄漏
 * 4. outputFile 在 cacheDir 创建，系统可自动清理；取消拍照时需手动 delete()
 * 5. ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY 会牺牲质量换速度，身份证场景可用
 * 6. 蒙层抠孔尺寸测量依赖 root.post {} 等布局完成，避免宽高为 0
 */
class CameraActivity : BaseActivity<ActivityCameraBinding, CameraViewModel>() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var outputFile: File
    private var photoTaken = false

    override fun onInit() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        outputFile = File(cacheDir, "id_photo_${System.currentTimeMillis()}.jpg")
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.viewFinder.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (_: Exception) {
                toast("相机不可用")
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(outputOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    photoTaken = true
                    val uri = FileProvider.getUriForFile(
                        this@CameraActivity, "${packageName}.fileprovider", outputFile
                    )
                    setResult(RESULT_OK, Intent().apply { data = uri; addFlags(FLAG_GRANT_READ_URI_PERMISSION) })
                    finish()
                }
                override fun onError(exception: ImageCaptureException) {
                    toast("拍照失败")
                    finish()
                }
            }
        )
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()  // 必须：防止线程泄漏
        if (!photoTaken && ::outputFile.isInitialized && outputFile.exists()) {
            outputFile.delete()    // 取消拍照时清理临时文件
        }
        super.onDestroy()
    }
}
```

### 拍照/图库选择弹窗

```kotlin
/**
 * 拍照 / 图库 二选一弹窗。
 * 内嵌在 IdentityInfoActivity 中，由 ViewModel 事件驱动显示/隐藏。
 *
 * 【注意事项】
 * - 不使用 Dialog，而是作为 View 嵌入页面布局（visibility 控制），避免 Dialog 生命周期问题
 * - 点击外部区域关闭通过遮罩层 onClick 实现
 */
// 在 XML 布局中：
// <LinearLayout android:id="@+id/photoChoiceDialog" android:visibility="gone">
//     <TextView android:text="Tomar foto" android:onClick="onTakePhoto" />
//     <TextView android:text="Seleccionar de galería" android:onClick="onPickGallery" />
// </LinearLayout>

// 在 Activity 中：
private fun showPhotoChoice() {
    binding.photoChoiceDialog.visibility = View.VISIBLE
}

private fun hidePhotoChoice() {
    binding.photoChoiceDialog.visibility = View.GONE
}

// 拍照：启动自定义 CameraActivity
private val cameraLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri -> viewModel.onImageReceived(uri) }
    }
}
```

### 图片压缩参考实现

```kotlin
/**
 * 图片压缩工具。
 * 两步解码（bounds 探测 → ByteArray 解码确保 inSampleSize 被遵守）避免 OOM，
 * JPEG 循环降质 + 分辨率兜底确保目标大小。
 *
 * 【内存/ANR 检查要点】
 * 1. 必须在 IO 线程调用（withContext(Dispatchers.IO)）—— 文件读写 + Bitmap 解码都是阻塞操作
 * 2. bounds 探测阶段用 inJustDecodeBounds = true，不分配像素内存（O(1) 内存）
 * 3. 内存预算校验：估计解码后占用内存 = w * h * 4 bytes，超出 MAX 则加倍 inSampleSize
 * 4. ByteArray 二次解码：避免某些 ROM 上 BitmapFactory.decodeStream 忽略 inSampleSize 的 bug
 * 5. FileOutputStream.channel.truncate(0) 复用文件避免重复创建
 * 6. 质量循环退到 MIN_QUALITY 仍超限 → 降分辨率兜底（最多 2 轮，每轮缩至 75%）
 * 7. 降分辨率产生的中间 Bitmap 必须 recycle()，最终 Bitmap 在 finally 中回收
 * 8. 整个方法 try-catch Throwable，任何异常返回 null（不崩溃）
 */
object ImageCompressor {
    private const val MAX_FILE_SIZE = 500 * 1024L       // 目标：≤500KB
    private const val MIN_QUALITY = 30                   // 最低 JPEG 质量
    private const val MAX_DECODE_MEMORY = 32 * 1024 * 1024L  // 单张解码内存上限 ~32MB

    fun compress(context: Context, uri: Uri, maxSide: Int = 1920, filePrefix: String = "img"): File? {
        return try {
            // 1. bounds 探测 → 只读尺寸，不分配内存
            val (rawW, rawH) = context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                opts.outWidth to opts.outHeight
            } ?: return null

            // 2. 计算 inSampleSize（2 的幂次）+ 内存预算
            var sampleSize = 1
            while (rawW / sampleSize > maxSide || rawH / sampleSize > maxSide) sampleSize *= 2
            var estBytes = (rawW / sampleSize).toLong() * (rawH / sampleSize) * 4
            while (estBytes > MAX_DECODE_MEMORY) { sampleSize *= 2; estBytes = (rawW / sampleSize).toLong() * (rawH / sampleSize) * 4 }

            // 3. ByteArray 解码（确保 inSampleSize 在所有 ROM 上被遵守）
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null

            // 4. 精确缩放 + 5. JPEG 质量循环 + 6. 降分辨率兜底
            // ...（详细实现见上方完整代码）
            file.takeIf { it.length() > 0 }
        } catch (_: Throwable) { null }
    }
}
```

### 使用示例（OCR 场景）

```kotlin
// 在 ViewModel 中：
fun onImageReceived(uri: Uri) {
    viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            ImageCompressor.compress(context, uri, maxSide = 1920, filePrefix = "ocr")
        }
        if (file == null) {
            _toast.emit("图片处理失败")
            return@launch
        }
        // 构造 Multipart 上传
        val part = MultipartBody.Part.createFormData(
            "image_field_name", file.name,
            file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        )
        apiService.ocrVerify(image = part, type = "1")
    }
}
```

### 风险和兜底策略总结

| 风险点 | 触发条件 | 兜底方案 |
|--------|---------|---------|
| OOM | 原图 > 4000px 且 inSampleSize 被 ROM 忽略 | ByteArray 二次解码 + 内存预算校验 |
| ANR | 主线程 readBytes / decodeBitmap / compress | 必须在 Dispatchers.IO 上执行 |
| 临时文件泄漏 | 拍照取消 / 压缩异常 | onDestroy 清理 + cacheDir 系统自动回收 |
| 压缩死循环 | 图片纹理复杂，质量降到最低仍超限 | 降分辨率兜底（最多 2 轮） |
| FileProvider 未配置 | 首次使用相机 | 检查 AndroidManifest.xml 中 FileProvider 声明 |
| Executor 泄漏 | onDestroy 未 shutdown | 必须 shutdown() + exit() |
| 图片回显 OOM | 自行 decode URI 原图到 Bitmap | 使用 Coil/Glide 加载 URI，框架内置采样和缓存 |
| 回显与压缩混淆 | 用压缩文件直接 decode 展示 | 压缩=给上传，回显=给展示，走 Coil/Glide |

---
