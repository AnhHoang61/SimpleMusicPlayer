package com.example.simplemusicplayer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * VinylWaveVisualizerView:
 * High-performance 60fps circular audio wave visualizer radiating around the central
 * Samurai Katana Tsuba vinyl disc.
 *
 * Features:
 * - 64 radial equalizer spectrum wave bars with smooth damping & rounded tips.
 * - Concentric expanding acoustic ripple shockwaves (vòng sóng âm lan tỏa).
 * - Continuous flowing sinusoidal wave ribbon (dải sóng nhạc uốn lượn liên tục).
 * - Musical spark embers on wave crests.
 * - Multi-harmonic organic synthesizer engine (works 100% reliably with or without RECORD_AUDIO).
 * - Real Visualizer FFT capture bridge with fail-safe fallback.
 */
public class VinylWaveVisualizerView extends View {

    public static final int STYLE_FULL_SPECTRUM = 0;   // Radial bars + Ripples + Wave ribbon
    public static final int STYLE_RIPPLE_WAVE = 1;      // Concentric ripples + Wave ribbon
    public static final int STYLE_NEON_BARS = 2;        // High-energy neon bars only

    private static final int NUM_BARS = 64;
    private static final int NUM_RIPPLES = 3;

    // Paints
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Preallocated Path & arrays for 60fps zero GC allocations
    private final Path ribbonPath = new Path();
    private final float[] barHeights = new float[NUM_BARS];
    private final float[] targetBarHeights = new float[NUM_BARS];
    private final float[] barAngles = new float[NUM_BARS];
    private final float[] cosAngles = new float[NUM_BARS];
    private final float[] sinAngles = new float[NUM_BARS];

    // Ripple state
    private static class Ripple {
        float offset;       // distance from disc edge in pixels
        float maxOffset;    // max expansion distance
        float alpha;        // 1.0 to 0.0
        float speed;
        boolean active;
    }
    private final Ripple[] ripples = new Ripple[NUM_RIPPLES];

    // Animation & Physics State
    private boolean isPlaying = false;
    private float amplitude = 0f;           // 0.0 (idle/paused) to 1.0 (full blast)
    private float targetAmplitude = 0f;
    private float time = 0f;
    private long lastFrameTime = 0;
    private float rotationOffset = 0f;
    private int currentStyle = STYLE_FULL_SPECTRUM;

    // Dimensions
    private float centerX = 0f;
    private float centerY = 0f;
    private float discRadius = 0f;          // Radius where bars start
    private float maxBarHeight = 0f;
    private float minBarHeight = 0f;

    // Colors (Samurai Theme: Katana Gold -> Samurai Red -> Cyan Blade)
    private final int colorGold = 0xFFD4AF37;
    private final int colorCrimson = 0xFFE63946;
    private final int colorCyan = 0xFF4DD0E1;
    private final int[] spectrumColors = new int[]{
            0xFFD4AF37, 0xFFE63946, 0xFFFF4D6D, 0xFF4DD0E1, 0xFFD4AF37
    };
    private final float[] spectrumPositions = new float[]{
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f
    };

    // Hardware Visualizer (optional, with safe fallback)
    @Nullable
    private Visualizer visualizer = null;
    private byte[] fftData = null;
    private boolean hasRealFft = false;

    public VinylWaveVisualizerView(Context context) {
        super(context);
        init();
    }

    public VinylWaveVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VinylWaveVisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Pre-calculate angular steps
        for (int i = 0; i < NUM_BARS; i++) {
            barAngles[i] = (float) (i * 2.0 * Math.PI / NUM_BARS);
            cosAngles[i] = (float) Math.cos(barAngles[i]);
            sinAngles[i] = (float) Math.sin(barAngles[i]);
            barHeights[i] = 4f;
            targetBarHeights[i] = 4f;
        }

        // Setup Ripple pool
        for (int i = 0; i < NUM_RIPPLES; i++) {
            ripples[i] = new Ripple();
            ripples[i].offset = i * 25f;
            ripples[i].maxOffset = 80f;
            ripples[i].alpha = 0f;
            ripples[i].speed = 1.2f + (i * 0.3f);
            ripples[i].active = false;
        }

        // Configure Paints
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        barPaint.setStrokeWidth(dpToPx(3.2f));

        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeCap(Paint.Cap.ROUND);
        ripplePaint.setStrokeWidth(dpToPx(2.0f));
        ripplePaint.setColor(colorGold);

        ribbonPaint.setStyle(Paint.Style.STROKE);
        ribbonPaint.setStrokeCap(Paint.Cap.ROUND);
        ribbonPaint.setStrokeJoin(Paint.Join.ROUND);
        ribbonPaint.setStrokeWidth(dpToPx(2.2f));
        ribbonPaint.setColor(colorCyan);

        emberPaint.setStyle(Paint.Style.FILL);
        emberPaint.setColor(colorGold);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;

        float minDim = Math.min(centerX, centerY);
        // Vinyl disc has diameter 240dp. Inside container of ~280-300dp, disc radius is ~115dp-120dp.
        discRadius = minDim * 0.74f;
        maxBarHeight = minDim * 0.22f;
        minBarHeight = dpToPx(3.5f);

        // Configure sweep gradient around the center for visualizer bars
        SweepGradient sweepGradient = new SweepGradient(centerX, centerY, spectrumColors, spectrumPositions);
        barPaint.setShader(sweepGradient);

        for (Ripple r : ripples) {
            r.maxOffset = minDim - discRadius - dpToPx(4f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (discRadius <= 0f) return;

        long now = SystemClock.uptimeMillis();
        float dt = (lastFrameTime == 0) ? 0.016f : Math.min(0.05f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        updateSimulation(dt);

        // 1. Draw Concentric Expanding Acoustic Ripples
        if (currentStyle == STYLE_FULL_SPECTRUM || currentStyle == STYLE_RIPPLE_WAVE) {
            drawRipples(canvas);
        }

        // 2. Draw Radial Equalizer Bars & Ribbon
        if (currentStyle == STYLE_FULL_SPECTRUM || currentStyle == STYLE_NEON_BARS) {
            drawRadialBars(canvas);
        }

        // 3. Draw Continuous Sinusoidal Wave Ribbon
        if (currentStyle == STYLE_FULL_SPECTRUM || currentStyle == STYLE_RIPPLE_WAVE) {
            drawWaveRibbon(canvas);
        }

        // Keep 60fps loop running while playing or while amplitude is decaying to 0
        if (isPlaying || amplitude > 0.005f) {
            postInvalidateOnAnimation();
        }
    }

    private void updateSimulation(float dt) {
        // Smoothly ramp amplitude to target
        amplitude += (targetAmplitude - amplitude) * Math.min(1.0f, dt * 8.0f);
        time += dt * (isPlaying ? 3.8f : 0.8f);
        rotationOffset += dt * (isPlaying ? 0.35f : 0.05f);

        // Check if we have real FFT data
        if (hasRealFft && fftData != null) {
            updateFromFft();
        } else {
            updateFromSynthesizer();
        }

        // Update ripple positions
        for (int i = 0; i < NUM_RIPPLES; i++) {
            Ripple r = ripples[i];
            if (isPlaying && amplitude > 0.15f) {
                r.offset += r.speed * dt * 60f;
                if (r.offset > r.maxOffset) {
                    r.offset = 0f;
                }
                float progress = Math.max(0f, Math.min(1f, r.offset / r.maxOffset));
                // Quadratic fade out as ripple travels outwards
                r.alpha = (1.0f - progress) * (1.0f - progress) * 0.7f * amplitude;
            } else {
                r.alpha = Math.max(0f, r.alpha - dt * 3f);
            }
        }
    }

    /**
     * Synthesizes rich multi-harmonic procedural audio spectrum dancing in real-time.
     */
    private void updateFromSynthesizer() {
        // Low-frequency bass drum pulse
        float bassKick = (float) Math.max(0, Math.sin(time * 3.4f)) * 0.5f;

        for (int i = 0; i < NUM_BARS; i++) {
            float frac = (float) i / NUM_BARS;
            float angle = barAngles[i];

            // 4 Harmonics with distinct musical frequencies
            float h1 = (float) Math.sin(time * 4.5f + frac * 4.0 * Math.PI);
            float h2 = (float) Math.sin(time * 8.2f - frac * 6.0 * Math.PI);
            float h3 = (float) Math.cos(time * 2.3f + frac * 2.0 * Math.PI);
            float h4 = (float) Math.sin(time * 12.0f + angle * 3.0);

            float combined = (h1 * 0.35f + h2 * 0.30f + h3 * 0.25f + h4 * 0.10f + 1.0f) * 0.5f;

            // Accentuate bass frequencies at 12 o'clock, 3 o'clock, 6 o'clock, 9 o'clock
            float sectorAccent = (float) Math.abs(Math.sin(angle * 2.0));
            combined += bassKick * sectorAccent * 0.45f;

            // Target height scaled by current amplitude
            float targetH = minBarHeight + (maxBarHeight - minBarHeight) * combined * amplitude;
            targetBarHeights[i] = targetH;

            // Damping: snappy rise (35%), smooth fall (15%)
            if (targetH > barHeights[i]) {
                barHeights[i] += (targetH - barHeights[i]) * 0.35f;
            } else {
                barHeights[i] += (targetH - barHeights[i]) * 0.15f;
            }
        }
    }

    private void updateFromFft() {
        if (fftData == null || fftData.length < NUM_BARS * 2) {
            updateFromSynthesizer();
            return;
        }

        for (int i = 0; i < NUM_BARS; i++) {
            int rfk = fftData[i * 2];
            int ifk = fftData[i * 2 + 1];
            float magnitude = (float) Math.hypot(rfk, ifk);
            float normalized = Math.max(0f, Math.min(1f, magnitude / 128f));

            float targetH = minBarHeight + (maxBarHeight - minBarHeight) * normalized * amplitude;
            if (targetH > barHeights[i]) {
                barHeights[i] += (targetH - barHeights[i]) * 0.40f;
            } else {
                barHeights[i] += (targetH - barHeights[i]) * 0.16f;
            }
        }
    }

    private void drawRipples(Canvas canvas) {
        for (Ripple r : ripples) {
            if (r.alpha <= 0.01f) continue;
            float radius = discRadius + r.offset;
            int alphaInt = (int) (r.alpha * 255);
            ripplePaint.setColor((alphaInt << 24) | (colorGold & 0x00FFFFFF));
            ripplePaint.setStrokeWidth(dpToPx(2.2f) * (1.0f - (r.offset / r.maxOffset) * 0.5f));
            canvas.drawCircle(centerX, centerY, radius, ripplePaint);
        }
    }

    private void drawRadialBars(Canvas canvas) {
        float innerR = discRadius + dpToPx(3f);

        for (int i = 0; i < NUM_BARS; i++) {
            float angle = barAngles[i] + rotationOffset;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float h = Math.max(minBarHeight, barHeights[i]);

            float startX = centerX + innerR * cos;
            float startY = centerY + innerR * sin;
            float endX = centerX + (innerR + h) * cos;
            float endY = centerY + (innerR + h) * sin;

            canvas.drawLine(startX, startY, endX, endY, barPaint);

            // Draw glowing spark ember at the peak of every 3rd bar
            if ((i % 3 == 0) && h > minBarHeight * 1.5f && amplitude > 0.3f) {
                emberPaint.setAlpha((int) (Math.min(1f, h / maxBarHeight) * 220));
                canvas.drawCircle(endX + cos * dpToPx(1.5f), endY + sin * dpToPx(1.5f), dpToPx(1.8f), emberPaint);
            }
        }
    }

    private void drawWaveRibbon(Canvas canvas) {
        if (amplitude < 0.05f) return;

        ribbonPath.reset();
        float innerR = discRadius + dpToPx(4f);
        boolean first = true;

        for (int i = 0; i <= NUM_BARS; i++) {
            int idx = i % NUM_BARS;
            float angle = barAngles[idx] + rotationOffset;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            // Ribbon crest sits smoothly at 75% bar height
            float r = innerR + (barHeights[idx] * 0.75f);
            float px = centerX + r * cos;
            float py = centerY + r * sin;

            if (first) {
                ribbonPath.moveTo(px, py);
                first = false;
            } else {
                ribbonPath.lineTo(px, py);
            }
        }

        ribbonPath.close();
        ribbonPaint.setAlpha((int) (amplitude * 160));
        canvas.drawPath(ribbonPath, ribbonPaint);
    }

    // ==========================================
    // Public Control API
    // ==========================================

    /**
     * Starts the audio visualizer wave animations (called when music starts playing).
     */
    public void start() {
        isPlaying = true;
        targetAmplitude = 1.0f;
        lastFrameTime = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    /**
     * Pauses the waves with a smooth damping decay to idle state.
     */
    public void pause() {
        isPlaying = false;
        targetAmplitude = 0.0f;
        postInvalidateOnAnimation();
    }

    /**
     * Triggers an immediate shockwave ripple burst (e.g. on user tap or beat drop).
     */
    public void triggerPulse() {
        for (Ripple r : ripples) {
            r.offset = 0f;
            r.alpha = 0.9f;
        }
        for (int i = 0; i < NUM_BARS; i++) {
            targetBarHeights[i] = maxBarHeight * 0.95f;
            barHeights[i] = Math.max(barHeights[i], maxBarHeight * 0.7f);
        }
        postInvalidateOnAnimation();
    }

    /**
     * Cycles between visualizer display styles:
     * 0: Full Spectrum (Bars + Ripples + Ribbon)
     * 1: Ripple Waves + Ribbon
     * 2: Neon Bars
     */
    public int cycleWaveStyle() {
        currentStyle = (currentStyle + 1) % 3;
        triggerPulse();
        return currentStyle;
    }

    public int getWaveStyle() {
        return currentStyle;
    }

    public void setWaveStyle(int style) {
        this.currentStyle = style % 3;
        postInvalidateOnAnimation();
    }

    /**
     * Connects an Android MediaPlayer AudioSessionId to the hardware Visualizer.
     * Safely falls back if permission is absent or hardware rejects session.
     */
    public void setAudioSessionId(int audioSessionId) {
        releaseVisualizer();
        if (audioSessionId <= 0) return;

        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[0]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {}

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    fftData = fft;
                    hasRealFft = (fft != null && fft.length > 0);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
        } catch (Throwable e) {
            // Permission or vendor restriction: fail safe to procedural synthesizer
            releaseVisualizer();
        }
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {}
            visualizer = null;
        }
        hasRealFft = false;
        fftData = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseVisualizer();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
