package com.trae.batchcrop.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 图片解码、方向校正与相册保存的公共方法。
 */
public final class ImageUtils {

    public static final String OUTPUT_FOLDER = "批量裁剪";

    private ImageUtils() {
    }

    public static InputStream open(Context context, Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("无法读取图片: " + uri);
        }
        return in;
    }

    /**
     * 按最大边长采样解码，并根据 EXIF 信息把图片旋正。
     */
    public static Bitmap decodeSampled(Context context, Uri uri, int maxDim) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(context, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("不支持的图片格式");
        }

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream in = open(context, uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) {
            throw new IOException("图片解码失败");
        }
        return applyOrientation(bitmap, readOrientation(context, uri));
    }

    /**
     * 读取校正方向之后的原始宽高。
     */
    public static int[] readRotatedSize(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(context, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("不支持的图片格式");
        }
        int orientation = readOrientation(context, uri);
        if (orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE
                || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return new int[]{bounds.outHeight, bounds.outWidth};
        }
        return new int[]{bounds.outWidth, bounds.outHeight};
    }

    private static int readOrientation(Context context, Uri uri) {
        try (InputStream in = open(context, uri)) {
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (OutOfMemoryError error) {
            return bitmap;
        }
    }

    public static String queryBaseName(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
            // 忽略查询失败，使用默认名称
        }
        if (name == null || name.isEmpty()) {
            name = "image";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return sanitize(name);
    }

    public static String queryMimeType(Context context, Uri uri) {
        String mime = null;
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {
            // 忽略
        }
        if (mime == null || !mime.startsWith("image/")) {
            mime = "image/jpeg";
        }
        return mime;
    }

    /**
     * 把裁剪结果保存到相册「批量裁剪」文件夹。
     */
    public static Uri saveToGallery(Context context, Bitmap bitmap, String baseName, String mimeType)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String extension = "image/png".equals(mimeType) ? ".png" : ".jpg";
        String outputMime = "image/png".equals(mimeType) ? "image/png" : "image/jpeg";
        Bitmap.CompressFormat format = "image/png".equals(outputMime)
                ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
        String safeName = sanitize(baseName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, safeName + extension);
            values.put(MediaStore.Images.Media.MIME_TYPE, outputMime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + OUTPUT_FOLDER);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("无法写入相册");
            }
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null || !bitmap.compress(format, 95, out)) {
                    throw new IOException("图片写入失败");
                }
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            return uri;
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                OUTPUT_FOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建保存目录");
        }
        File file = uniqueFile(dir, safeName, extension);
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(format, 95, out)) {
                throw new IOException("图片写入失败");
            }
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Images.Media.MIME_TYPE, outputMime);
        values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()},
                new String[]{outputMime}, null);
        return uri != null ? uri : Uri.fromFile(file);
    }

    private static File uniqueFile(File dir, String baseName, String extension) {
        File file = new File(dir, baseName + extension);
        int index = 1;
        while (file.exists()) {
            file = new File(dir, baseName + "_" + index + extension);
            index++;
        }
        return file;
    }

    private static String sanitize(String name) {
        String result = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (result.isEmpty()) {
            result = "image";
        }
        return result;
    }
}
