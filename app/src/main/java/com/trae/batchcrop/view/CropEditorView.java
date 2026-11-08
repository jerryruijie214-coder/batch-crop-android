package com.trae.batchcrop.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 仿系统相册的裁剪取景器：可拖动/缩放图片，并拖动裁剪框的四角或边调整范围。
 * 对外以「归一化坐标（0~1）」描述裁剪区域，方便批量套用到不同尺寸的图片。
 */
public class CropEditorView extends View {

    public interface OnCropChangeListener {
        void onCropChanged(RectF normalized);
    }

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_TL = 2;
    private static final int MODE_TR = 3;
    private static final int MODE_BL = 4;
    private static final int MODE_BR = 5;
    private static final int MODE_LEFT = 6;
    private static final int MODE_TOP = 7;
    private static final int MODE_RIGHT = 8;
    private static final int MODE_BOTTOM = 9;
    private static final int MODE_CREATE = 10;

    private static final int HANDLE_TOUCH_DP = 26;
    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 10f;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix matrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();
    private final RectF cropRect = new RectF();
    private final RectF cropViewRect = new RectF();
    private final RectF startRect = new RectF();
    private final float[] pointBuffer = new float[2];

    private final float density;

    private Bitmap bitmap;
    private float fitScale = 1f;
    private float userScale = 1f;
    private float offsetX;
    private float offsetY;

    private boolean hasCrop;
    private RectF pendingRegion;
    private int mode = MODE_NONE;
    private float lastBmpX;
    private float lastBmpY;
    private float startBmpX;
    private float startBmpY;
    private float lastSpan;
    private float lastFocusX;
    private float lastFocusY;

    private String label = "";
    private OnCropChangeListener listener;

    public CropEditorView(Context context) {
        this(context, null);
    }

    public CropEditorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        dimPaint.setColor(0xAA000000);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.5f));
        borderPaint.setColor(0xFFFFFFFF);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(0x4DFFFFFF);

        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(dp(3.5f));
        handlePaint.setStrokeCap(Paint.Cap.ROUND);
        handlePaint.setColor(0xFFFFFFFF);

        labelBgPaint.setColor(0xE64C5FD5);
        labelTextPaint.setColor(0xFFFFFFFF);
        labelTextPaint.setTextSize(sp(12));
        labelTextPaint.setFakeBoldText(true);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setOnCropChangeListener(OnCropChangeListener listener) {
        this.listener = listener;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        invalidate();
    }

    /**
     * @param bitmap         用于显示的图片（可能已降采样）
     * @param normalizedRect 需要回显的区域，传 null 使用默认区域
     */
    public void setBitmap(Bitmap bitmap, @Nullable RectF normalizedRect) {
        this.bitmap = bitmap;
        this.pendingRegion = normalizedRect;
        this.hasCrop = false;
        this.userScale = 1f;
        this.offsetX = 0f;
        this.offsetY = 0f;
        if (getWidth() > 0 && getHeight() > 0) {
            initGeometry();
        }
        invalidate();
    }

    public void setNormalizedRegion(@Nullable RectF normalizedRect) {
        if (bitmap == null) {
            pendingRegion = normalizedRect;
            return;
        }
        if (normalizedRect == null) {
            setDefaultCrop();
        } else {
            float width = bitmap.getWidth();
            float height = bitmap.getHeight();
            cropRect.set(normalizedRect.left * width, normalizedRect.top * height,
                    normalizedRect.right * width, normalizedRect.bottom * height);
        }
        hasCrop = true;
        clampRectInsideBitmap();
        invalidate();
        notifyCrop();
    }

    @Nullable
    public RectF getNormalizedRegion() {
        if (bitmap == null || !hasCrop) {
            return null;
        }
        float width = bitmap.getWidth();
        float height = bitmap.getHeight();
        return new RectF(cropRect.left / width, cropRect.top / height,
                cropRect.right / width, cropRect.bottom / height);
    }

    public void resetViewTransform() {
        userScale = 1f;
        offsetX = 0f;
        offsetY = 0f;
        applyMatrix();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (bitmap != null) {
            initGeometry();
        }
    }

    private void initGeometry() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float availableWidth = getWidth() - dp(16);
        float availableHeight = getHeight() - dp(16);
        fitScale = Math.min(availableWidth / bitmap.getWidth(), availableHeight / bitmap.getHeight());
        if (fitScale <= 0f) {
            fitScale = 1f;
        }
        applyMatrix();
        if (!hasCrop) {
            if (pendingRegion != null) {
                float width = bitmap.getWidth();
                float height = bitmap.getHeight();
                cropRect.set(pendingRegion.left * width, pendingRegion.top * height,
                        pendingRegion.right * width, pendingRegion.bottom * height);
                clampRectInsideBitmap();
            } else {
                setDefaultCrop();
            }
            hasCrop = true;
            notifyCrop();
        }
    }

    private void setDefaultCrop() {
        float width = bitmap.getWidth();
        float height = bitmap.getHeight();
        cropRect.set(width * 0.1f, height * 0.1f, width * 0.9f, height * 0.9f);
    }

    private float currentScale() {
        return fitScale * userScale;
    }

    private void applyMatrix() {
        if (bitmap == null || getWidth() == 0) {
            return;
        }
        float scale = currentScale();
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        float left = getWidth() / 2f - drawWidth / 2f + offsetX;
        float top = getHeight() / 2f - drawHeight / 2f + offsetY;
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, top);
        matrix.invert(inverseMatrix);
    }

    private void clampOffsets() {
        if (bitmap == null) {
            return;
        }
        if (userScale <= MIN_ZOOM + 0.001f) {
            offsetX = 0f;
            offsetY = 0f;
            return;
        }
        float scale = currentScale();
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        float margin = dp(48);
        float minX = margin - getWidth() / 2f - drawWidth / 2f;
        float maxX = getWidth() / 2f + drawWidth / 2f - margin;
        float minY = margin - getHeight() / 2f - drawHeight / 2f;
        float maxY = getHeight() / 2f + drawHeight / 2f - margin;
        offsetX = Math.max(minX, Math.min(maxX, offsetX));
        offsetY = Math.max(minY, Math.min(maxY, offsetY));
    }

    private void mapPointToBitmap(float viewX, float viewY) {
        pointBuffer[0] = viewX;
        pointBuffer[1] = viewY;
        inverseMatrix.mapPoints(pointBuffer);
    }

    private float[] viewToBitmap(float viewX, float viewY) {
        mapPointToBitmap(viewX, viewY);
        return new float[]{pointBuffer[0], pointBuffer[1]};
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mode = hitTest(event.getX(), event.getY());
                float[] bmpPoint = viewToBitmap(event.getX(), event.getY());
                lastBmpX = bmpPoint[0];
                lastBmpY = bmpPoint[1];
                startBmpX = bmpPoint[0];
                startBmpY = bmpPoint[1];
                startRect.set(cropRect);
                if (mode == MODE_CREATE) {
                    cropRect.set(bmpPoint[0], bmpPoint[1], bmpPoint[0], bmpPoint[1]);
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (event.getPointerCount() == 2) {
                    mode = MODE_NONE;
                    lastSpan = spacing(event);
                    lastFocusX = (event.getX(0) + event.getX(1)) / 2f;
                    lastFocusY = (event.getY(0) + event.getY(1)) / 2f;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (event.getPointerCount() >= 2) {
                    handleZoom(event);
                    return true;
                }
                if (mode == MODE_NONE) {
                    return true;
                }
                float[] bmpPoint = viewToBitmap(event.getX(), event.getY());
                float dx = bmpPoint[0] - lastBmpX;
                float dy = bmpPoint[1] - lastBmpY;
                lastBmpX = bmpPoint[0];
                lastBmpY = bmpPoint[1];
                handleResize(dx, dy, bmpPoint[0], bmpPoint[1]);
                clampRectInsideBitmap();
                invalidate();
                notifyCrop();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (event.getPointerCount() == 2) {
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    float[] bmpPoint = viewToBitmap(event.getX(remaining), event.getY(remaining));
                    lastBmpX = bmpPoint[0];
                    lastBmpY = bmpPoint[1];
                    mode = MODE_NONE;
                    lastSpan = 0f;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mode == MODE_CREATE) {
                    float minSize = dp(48) / Math.max(currentScale(), 0.0001f);
                    if (cropRect.width() < minSize || cropRect.height() < minSize) {
                        cropRect.set(startRect);
                    }
                }
                mode = MODE_NONE;
                lastSpan = 0f;
                clampRectInsideBitmap();
                invalidate();
                notifyCrop();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleZoom(MotionEvent event) {
        float focusX = (event.getX(0) + event.getX(1)) / 2f;
        float focusY = (event.getY(0) + event.getY(1)) / 2f;
        float span = spacing(event);
        if (lastSpan > 0f && span > 0f) {
            float factor = span / lastSpan;
            float newScale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, userScale * factor));
            if (Math.abs(newScale - userScale) > 0.0001f) {
                float[] anchor = viewToBitmap(focusX, focusY);
                userScale = newScale;
                recomputeOffset(anchor[0], anchor[1], focusX, focusY);
            }
        }
        offsetX += focusX - lastFocusX;
        offsetY += focusY - lastFocusY;
        clampOffsets();
        lastSpan = span;
        lastFocusX = focusX;
        lastFocusY = focusY;
        applyMatrix();
        invalidate();
    }

    private void recomputeOffset(float bmpX, float bmpY, float viewX, float viewY) {
        if (bitmap == null) {
            return;
        }
        float scale = currentScale();
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        offsetX = viewX - (getWidth() / 2f - drawWidth / 2f) - bmpX * scale;
        offsetY = viewY - (getHeight() / 2f - drawHeight / 2f) - bmpY * scale;
    }

    private float spacing(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void handleResize(float dx, float dy, float bmpX, float bmpY) {
        float width = bitmap.getWidth();
        float height = bitmap.getHeight();
        float minSize = dp(40) / Math.max(currentScale(), 0.0001f);

        switch (mode) {
            case MODE_MOVE:
                cropRect.offset(dx, dy);
                if (cropRect.left < 0) {
                    cropRect.offset(-cropRect.left, 0);
                }
                if (cropRect.right > width) {
                    cropRect.offset(width - cropRect.right, 0);
                }
                if (cropRect.top < 0) {
                    cropRect.offset(0, -cropRect.top);
                }
                if (cropRect.bottom > height) {
                    cropRect.offset(0, height - cropRect.bottom);
                }
                break;
            case MODE_TL:
                cropRect.left = clamp(bmpX, 0, cropRect.right - minSize);
                cropRect.top = clamp(bmpY, 0, cropRect.bottom - minSize);
                break;
            case MODE_TR:
                cropRect.right = clamp(bmpX, cropRect.left + minSize, width);
                cropRect.top = clamp(bmpY, 0, cropRect.bottom - minSize);
                break;
            case MODE_BL:
                cropRect.left = clamp(bmpX, 0, cropRect.right - minSize);
                cropRect.bottom = clamp(bmpY, cropRect.top + minSize, height);
                break;
            case MODE_BR:
                cropRect.right = clamp(bmpX, cropRect.left + minSize, width);
                cropRect.bottom = clamp(bmpY, cropRect.top + minSize, height);
                break;
            case MODE_LEFT:
                cropRect.left = clamp(bmpX, 0, cropRect.right - minSize);
                break;
            case MODE_TOP:
                cropRect.top = clamp(bmpY, 0, cropRect.bottom - minSize);
                break;
            case MODE_RIGHT:
                cropRect.right = clamp(bmpX, cropRect.left + minSize, width);
                break;
            case MODE_BOTTOM:
                cropRect.bottom = clamp(bmpY, cropRect.top + minSize, height);
                break;
            case MODE_CREATE:
                cropRect.set(Math.min(startBmpX, bmpX), Math.min(startBmpY, bmpY),
                        Math.max(startBmpX, bmpX), Math.max(startBmpY, bmpY));
                break;
            default:
                break;
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void clampRectInsideBitmap() {
        if (bitmap == null) {
            return;
        }
        float width = bitmap.getWidth();
        float height = bitmap.getHeight();
        float minSize = dp(16) / Math.max(currentScale(), 0.0001f);
        if (cropRect.left < 0) cropRect.left = 0;
        if (cropRect.top < 0) cropRect.top = 0;
        if (cropRect.right > width) cropRect.right = width;
        if (cropRect.bottom > height) cropRect.bottom = height;
        if (cropRect.width() < minSize) cropRect.right = Math.min(width, cropRect.left + minSize);
        if (cropRect.height() < minSize) cropRect.bottom = Math.min(height, cropRect.top + minSize);
    }

    private int hitTest(float viewX, float viewY) {
        float tolerance = dp(HANDLE_TOUCH_DP);
        matrix.mapRect(cropViewRect, cropRect);
        float left = cropViewRect.left;
        float top = cropViewRect.top;
        float right = cropViewRect.right;
        float bottom = cropViewRect.bottom;

        boolean nearLeft = Math.abs(viewX - left) <= tolerance;
        boolean nearRight = Math.abs(viewX - right) <= tolerance;
        boolean nearTop = Math.abs(viewY - top) <= tolerance;
        boolean nearBottom = Math.abs(viewY - bottom) <= tolerance;
        boolean insideX = viewX >= left - tolerance && viewX <= right + tolerance;
        boolean insideY = viewY >= top - tolerance && viewY <= bottom + tolerance;

        if (nearLeft && nearTop) return MODE_TL;
        if (nearRight && nearTop) return MODE_TR;
        if (nearLeft && nearBottom) return MODE_BL;
        if (nearRight && nearBottom) return MODE_BR;
        if (nearLeft && insideY) return MODE_LEFT;
        if (nearRight && insideY) return MODE_RIGHT;
        if (nearTop && insideX) return MODE_TOP;
        if (nearBottom && insideX) return MODE_BOTTOM;
        if (viewX > left && viewX < right && viewY > top && viewY < bottom) return MODE_MOVE;
        return MODE_CREATE;
    }

    private void notifyCrop() {
        if (listener == null || bitmap == null) {
            return;
        }
        RectF normalized = getNormalizedRegion();
        if (normalized != null) {
            listener.onCropChanged(normalized);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            return;
        }
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        if (!hasCrop) {
            return;
        }

        matrix.mapRect(cropViewRect, cropRect);
        float left = cropViewRect.left;
        float top = cropViewRect.top;
        float right = cropViewRect.right;
        float bottom = cropViewRect.bottom;

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        canvas.drawRect(0, 0, viewWidth, Math.max(0, top), dimPaint);
        canvas.drawRect(0, Math.min(viewHeight, bottom), viewWidth, viewHeight, dimPaint);
        float midTop = Math.max(0, top);
        float midBottom = Math.min(viewHeight, bottom);
        canvas.drawRect(0, midTop, Math.max(0, left), midBottom, dimPaint);
        canvas.drawRect(Math.min(viewWidth, right), midTop, viewWidth, midBottom, dimPaint);

        canvas.drawRect(cropViewRect, borderPaint);

        for (int i = 1; i <= 2; i++) {
            float x = left + (right - left) * i / 3f;
            float y = top + (bottom - top) * i / 3f;
            canvas.drawLine(x, top, x, bottom, gridPaint);
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        drawHandles(canvas, left, top, right, bottom);
        drawLabel(canvas, left, top);
    }

    private void drawHandles(Canvas canvas, float left, float top, float right, float bottom) {
        float length = Math.min(dp(22), Math.min(right - left, bottom - top) / 3f);
        if (length <= dp(6)) {
            return;
        }
        canvas.drawLine(left, top, left + length, top, handlePaint);
        canvas.drawLine(left, top, left, top + length, handlePaint);

        canvas.drawLine(right, top, right - length, top, handlePaint);
        canvas.drawLine(right, top, right, top + length, handlePaint);

        canvas.drawLine(left, bottom, left + length, bottom, handlePaint);
        canvas.drawLine(left, bottom, left, bottom - length, handlePaint);

        canvas.drawLine(right, bottom, right - length, bottom, handlePaint);
        canvas.drawLine(right, bottom, right, bottom - length, handlePaint);
    }

    private void drawLabel(Canvas canvas, float left, float top) {
        if (label.isEmpty()) {
            return;
        }
        Paint.FontMetrics metrics = labelTextPaint.getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;
        float paddingH = dp(9);
        float paddingV = dp(4);
        float textWidth = labelTextPaint.measureText(label);
        float boxLeft = left + dp(10);
        float boxTop = top + dp(10);
        float boxRight = boxLeft + textWidth + paddingH * 2;
        float boxBottom = boxTop + textHeight + paddingV * 2;
        float radius = (boxBottom - boxTop) / 2f;
        canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, radius, radius, labelBgPaint);
        float baseline = boxTop + paddingV - metrics.ascent;
        canvas.drawText(label, boxLeft + paddingH, baseline, labelTextPaint);
    }
}
