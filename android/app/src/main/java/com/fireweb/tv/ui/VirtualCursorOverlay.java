package com.fireweb.tv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

public class VirtualCursorOverlay extends FrameLayout {
    private static final String TAG = "VirtualCursor";

    private float cursorX = 640f;
    private float cursorY = 360f;
    private float cursorRadius = 14f;
    private float scaleFactor = 1.0f;

    private boolean isCursorEnabled = false;
    private WebView targetWebView;

    private Paint outerRingPaint;
    private Paint innerDotPaint;
    private Paint shadowPaint;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final float BASE_SPEED = 14f;
    private static final float MAX_SPEED = 42f;

    public VirtualCursorOverlay(Context context) {
        super(context);
        init();
    }

    public VirtualCursorOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VirtualCursorOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);

        outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingPaint.setColor(Color.parseColor("#38BDF8")); // Bright Cyan/Sky blue
        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(3.5f);

        innerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerDotPaint.setColor(Color.WHITE);
        innerDotPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#80000000")); // Drop shadow
        shadowPaint.setStyle(Paint.Style.FILL);
    }

    public void setTargetWebView(WebView webView) {
        this.targetWebView = webView;
    }

    public void setCursorEnabled(boolean enabled) {
        this.isCursorEnabled = enabled;
        setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) {
            requestFocus();
        }
        invalidate();
    }

    public boolean isCursorEnabled() {
        return isCursorEnabled;
    }

    public void toggleCursor() {
        setCursorEnabled(!isCursorEnabled);
        String status = isCursorEnabled ? "Virtual Mouse: ACTIVE (Use D-Pad to move, Center to click)" : "Scroll Mode: ACTIVE";
        Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0 && oldh == 0) {
            cursorX = w / 2f;
            cursorY = h / 2f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isCursorEnabled) return;

        // Draw shadow
        canvas.drawCircle(cursorX + 2, cursorY + 3, (cursorRadius + 2) * scaleFactor, shadowPaint);

        // Draw outer ring
        canvas.drawCircle(cursorX, cursorY, cursorRadius * scaleFactor, outerRingPaint);

        // Draw inner high-contrast dot
        canvas.drawCircle(cursorX, cursorY, (cursorRadius * 0.35f) * scaleFactor, innerDotPaint);
    }

    private boolean isDialogActive = false;

    public void setDialogActive(boolean active) {
        this.isDialogActive = active;
    }

    public boolean isDialogActive() {
        return isDialogActive;
    }

    public boolean handleDpadKey(int keyCode, KeyEvent event) {
        if (isDialogActive) return false;

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                toggleCursor();
            }
            return true;
        }

        if (!isCursorEnabled) {
            // In scroll mode, let D-pad scroll web view directly
            if (targetWebView != null) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    targetWebView.scrollBy(0, 150);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    targetWebView.scrollBy(0, -150);
                    return true;
                }
            }
            return false;
        }

        // In Cursor Mode:
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int repeatCount = event.getRepeatCount();
            float speed = Math.min(BASE_SPEED + (repeatCount * 2.5f), MAX_SPEED);

            float dx = 0;
            float dy = 0;

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    dy = -speed;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    dy = speed;
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    dx = -speed;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    dx = speed;
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    performVirtualClick();
                    return true;
                default:
                    return false;
            }

            moveCursor(dx, dy);
            return true;
        }

        return false;
    }

    private void moveCursor(float dx, float dy) {
        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        cursorX = Math.max(10, Math.min(width - 10, cursorX + dx));
        cursorY = Math.max(10, Math.min(height - 10, cursorY + dy));

        // Auto-scroll web page when cursor is near top or bottom edges
        if (targetWebView != null) {
            float edgeThreshold = height * 0.12f;
            if (cursorY < edgeThreshold && dy < 0) {
                targetWebView.scrollBy(0, -80);
            } else if (cursorY > (height - edgeThreshold) && dy > 0) {
                targetWebView.scrollBy(0, 80);
            }
        }

        invalidate();
    }

    private void performVirtualClick() {
        if (targetWebView == null) return;

        // Visual click animation feedback
        ValueAnimator anim = ValueAnimator.ofFloat(1.0f, 0.7f, 1.0f);
        anim.setDuration(180);
        anim.addUpdateListener(animation -> {
            scaleFactor = (float) animation.getAnimatedValue();
            invalidate();
        });
        anim.start();

        // Ensure web view receives focus for clicks and inputs
        targetWebView.requestFocus();

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent downEvent = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                cursorX,
                cursorY,
                0
        );

        targetWebView.dispatchTouchEvent(downEvent);
        downEvent.recycle();

        handler.postDelayed(() -> {
            long upTime = SystemClock.uptimeMillis();
            MotionEvent upEvent = MotionEvent.obtain(
                    downTime,
                    upTime,
                    MotionEvent.ACTION_UP,
                    cursorX,
                    cursorY,
                    0
            );
            targetWebView.dispatchTouchEvent(upEvent);
            upEvent.recycle();
        }, 80);
    }
}
