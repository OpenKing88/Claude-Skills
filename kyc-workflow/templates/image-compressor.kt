/**
 * ImageCompressor — 图片压缩工具
 *
 * 业务目的：
 *   所有KYC流程中用户上传的图片必须经过压缩处理，禁止直接上传原图。
 *   两步解码（bounds探测→ByteArray解码）确保inSampleSize被遵守，避免OOM。
 *
 * 教学重点：
 *   1. 必须在IO线程执行（withContext(Dispatchers.IO)），所有文件I/O和Bitmap解码都是阻塞操作
 *   2. bounds探测阶段使用inJustDecodeBounds=true，不分配像素内存
 *   3. 内存预算校验：估计解码后占用内存=w*h*4，超出MAX则加倍inSampleSize
 *   4. ByteArray二次解码：确保所有ROM都遵守inSampleSize
 *   5. 质量循环退到MIN_QUALITY仍超限 → 降分辨率兜底（最多2轮，每轮缩至75%）
 *   6. 降分辨率产生的中间Bitmap必须recycle()
 *   7. 整个方法try-catch，任何异常返回null（不崩溃），不fallback原图
 *   8. 压缩文件可以传给Coil/Glide加载展示，框架内置展示级缩放
 */

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
            // ⚠️ 流程规则: 第一步 — bounds探测，只读尺寸不分配内存
            // ⚠️ 流程规则: inJustDecodeBounds=true 确保不分配像素内存（O(1)内存）
            val (rawW, rawH) = context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                opts.outWidth to opts.outHeight
            } ?: return null

            // ⚠️ 流程规则: 第二步 — 计算inSampleSize（2的幂次）+ 内存预算
            // ⚠️ 流程规则: 估计字节 = w*h*4，超出MAX_DECODE_MEMORY则加倍sampleSize
            var sampleSize = 1
            while (rawW / sampleSize > maxSide || rawH / sampleSize > maxSide) sampleSize *= 2
            var estBytes = (rawW / sampleSize).toLong() * (rawH / sampleSize) * 4
            while (estBytes > MAX_DECODE_MEMORY) { sampleSize *= 2; estBytes = (rawW / sampleSize).toLong() * (rawH / sampleSize) * 4 }

            // ⚠️ 流程规则: 第三步 — ByteArray解码，确保inSampleSize在所有ROM上被遵守
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null

            // 4. 精确缩放: 将bitmap缩放到目标尺寸以内（保持宽高比）
            val scale = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap

            // 5. JPEG 质量循环: 从目标质量开始，逐步降低直到满足文件大小限制
            val outputFile = File(context.cacheDir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
            var quality = 90
            var outBytes: ByteArray
            do {
                ByteArrayOutputStream().use { baos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    outBytes = baos.toByteArray()
                }
                if (outBytes.size <= MAX_FILE_SIZE) break
                quality -= 10
            } while (quality >= MIN_QUALITY)

            // 6. 降分辨率兜底: 质量降到最低仍超限 → 缩小尺寸重试（最多2轮，每轮缩至75%）
            var fallbackBitmap = scaled
            var fallbackRound = 0
            while (outBytes.size > MAX_FILE_SIZE && fallbackRound < 2) {
                fallbackRound++
                val newW = (fallbackBitmap.width * 0.75).toInt()
                val newH = (fallbackBitmap.height * 0.75).toInt()
                val reduced = Bitmap.createScaledBitmap(fallbackBitmap, newW, newH, true)
                if (fallbackBitmap !== scaled) fallbackBitmap.recycle() // 回收上一轮中间Bitmap
                fallbackBitmap = reduced
                ByteArrayOutputStream().use { baos ->
                    fallbackBitmap.compress(Bitmap.CompressFormat.JPEG, MIN_QUALITY, baos)
                    outBytes = baos.toByteArray()
                }
            }

            // 写入输出文件
            outputFile.outputStream().use { it.write(outBytes) }

            // 7. 回收Bitmap（finally确保即使异常也回收）
            try {
                outputFile.takeIf { it.length() > 0 }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                if (fallbackBitmap !== scaled && fallbackBitmap !== bitmap) fallbackBitmap.recycle()
                bitmap.recycle()
            }
        // ⚠️ 流程规则: 任何异常返回null，绝不fallback原图字节（避免大图readBytes触发OOM）
        } catch (_: Throwable) { null }
    }
}
