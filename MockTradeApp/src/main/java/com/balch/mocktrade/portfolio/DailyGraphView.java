package com.balch.mocktrade.portfolio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.balch.android.app.framework.types.Money;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Render the performance items on a daily graph.
 *
 * This component will graph absolute daily change over time. The
 * vertical center of the graph will be 0.0 and lines drawn above the
 * center will be green and below the center will render as red.
 *
 * The x Axis will represent the entire day, starting 30 mins before
 * market open and ending 30 mins after market close (unless the last
 * quote time is after market close, in which case the the end of the graph
 * will be after the last quote time.
 *
 */
public class DailyGraphView extends View {
    private static final String TAG = DailyGraphView.class.getSimpleName();

    private static final int ANIMATION_DURATION_MS = 700;

    private static final int GRAPH_PADDING_VERTICAL = 30;
    private static final int GRAPH_PADDING_HORIZONTAL = 30;

    private static final int[] LINEAR_GRADIENT_COLORS = new int[] {
            Color.argb(255, 0, 255, 0),
            Color.argb(255, 0, 156, 0),
            Color.argb(255, 156, 156, 156),
            Color.argb(255, 156, 0, 0),
            Color.argb(255, 255, 0, 0)
    };

    private static final float[] LINEAR_GRADIENT_POSITIONS = new float[] {
            0f, .475f, .5f, .525f, 1
    };

    private static final int EXAMINER_WIDTH = 5;

    private Paint mPathPaint;
    private Paint mMarketTimesPaint;
    private Path mPath;
    private float mPathLength;
    private Paint mExaminerPaint;
    private RectF mExaminerRect;
    private Paint mExaminerTimePaint;
    private String mExaminerTime;
    private Rect mExaminerTimeTextBounds = new Rect();
    private Paint mExaminerValuePaint;
    private String mExaminerValue;
    private Rect mExaminerValueTextBounds = new Rect();


    private List<PerformanceItem> mPerformanceItems;

    private int mWidth;
    private int mHeight;
    private float mScaleX = 1.0f;
    private float mScaleY = 1.0f;
    private int mOffsetY;
    private long mOffsetX;

    private int mMaxYIndex = -1;
    private int mMinYIndex = -1;
    private long mMarketStartTime;
    private long mMarketEndTime;

    public DailyGraphView(Context context) {
        super(context);
        initialize();
    }

    public DailyGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mMarketStartTime != 0) {
            float marketStartX = scaleX(mMarketStartTime);
            canvas.drawLine(marketStartX, GRAPH_PADDING_VERTICAL,
                    marketStartX, mHeight - GRAPH_PADDING_VERTICAL, mMarketTimesPaint);
        }

        if (mMarketEndTime != 0) {
            float marketEndX = scaleX(mMarketEndTime);
            canvas.drawLine(marketEndX, GRAPH_PADDING_VERTICAL,
                    marketEndX, mHeight - GRAPH_PADDING_VERTICAL, mMarketTimesPaint);
        }

        if (mMarketEndTime != 0) {
            float centerY = scaleY(00.0f);
            canvas.drawLine(GRAPH_PADDING_HORIZONTAL, centerY,
                    mWidth - GRAPH_PADDING_HORIZONTAL, centerY, mMarketTimesPaint);
        }

        canvas.drawPath(mPath, mPathPaint);

        if (mExaminerRect != null) {
            canvas.drawRect(mExaminerRect, mExaminerPaint);

            canvas.drawText(mExaminerTime,
                    mExaminerRect.left - mExaminerTimeTextBounds.centerX(), mHeight - 2,
                    mExaminerTimePaint);

            canvas.drawText(mExaminerValue,
                    mExaminerRect.left - mExaminerValueTextBounds.centerX(),
                    mExaminerValueTextBounds.height() + 2,
                    mExaminerValuePaint);
        }
    }

    private void initialize() {

        mMarketTimesPaint = new Paint();
        mMarketTimesPaint.setAntiAlias(true);
        mMarketTimesPaint.setStyle(Paint.Style.STROKE);
        mMarketTimesPaint.setColor(Color.LTGRAY);
        mMarketTimesPaint.setAlpha(128);
        mMarketTimesPaint.setStrokeWidth(2);
        mMarketTimesPaint.setPathEffect(new DashPathEffect(new float[]{4, 4}, 0));

        mExaminerPaint = new Paint();
        mExaminerPaint.setAntiAlias(true);
        mExaminerPaint.setColor(Color.argb(255, 168, 168, 168));
        mExaminerPaint.setStyle(Paint.Style.FILL);
        mExaminerPaint.setMaskFilter(new BlurMaskFilter(EXAMINER_WIDTH, BlurMaskFilter.Blur.NORMAL));
        mExaminerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));

        mExaminerTimePaint = new Paint();
        mExaminerTimePaint.setAntiAlias(true);
        mExaminerTimePaint.setColor(Color.WHITE);
        mExaminerTimePaint.setStyle(Paint.Style.FILL);
        mExaminerTimePaint.setTextSize(34);

        mExaminerValuePaint = new Paint();
        mExaminerValuePaint.setAntiAlias(true);
        mExaminerValuePaint.setColor(Color.WHITE);
        mExaminerValuePaint.setStyle(Paint.Style.FILL);
        mExaminerValuePaint.setTextSize(34);

        mPathPaint = new Paint();
        mPathPaint.setAntiAlias(true);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setColor(Color.WHITE);
        mPathPaint.setStrokeWidth(4);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setShadowLayer(7, 0f, 0f, Color.LTGRAY);

        if (!isInEditMode() && Build.VERSION.SDK_INT >= 11) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        mPath = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if ((w != 0) && (h!=0)) {
            mWidth = w;
            mHeight = h;

            calculatePath();
        }
    }

    private void calculatePath() {
        mPath.rewind();

        if ((mPerformanceItems != null) && (mPerformanceItems.size() >= 2)) {
            calculateScale();

            List<PointF> points = new ArrayList<>(mPerformanceItems.size());

            for (PerformanceItem performanceItem : mPerformanceItems) {
                float xValue = (float)performanceItem.getTimestamp().getTime();
                float yValue = (float)getPerformanceItemValue(performanceItem).getMicroCents();

                float xScaleValue = scaleX(xValue);
                float yScaleValue = scaleY(yValue);

                points.add(new PointF(xScaleValue, yScaleValue));
            }

            mPath.moveTo(points.get(0).x, points.get(0).y);
            for (int x = 1; x < points.size() - 1; x++) {
                mPath.quadTo(points.get(x).x, points.get(x).y, points.get(x + 1).x, points.get(x + 1).y);
            }

            int lastPos = points.size() - 1;
            mPath.lineTo(points.get(lastPos).x, points.get(lastPos).y);

            PathMeasure measure = new PathMeasure(mPath, false);
            mPathLength = measure.getLength();

            Shader shader = new LinearGradient(0, GRAPH_PADDING_VERTICAL,
                    0, mHeight - GRAPH_PADDING_VERTICAL,
                    LINEAR_GRADIENT_COLORS,
                    LINEAR_GRADIENT_POSITIONS,
                    Shader.TileMode.CLAMP);

            mPathPaint.setShader(shader);
        }
    }

    private void calculateScale() {

        if ( (mWidth != 0) && (mHeight != 0)) {

            // set the Y range so 0 is at the center, with room
            // to accommodate the max gain or loss
            long absMaxY = Math.abs(getPerformanceItemValue(mPerformanceItems.get(mMaxYIndex)).getMicroCents());
            long absMinY = Math.abs(getPerformanceItemValue(mPerformanceItems.get(mMinYIndex)).getMicroCents());
            long deltaY = (absMaxY > absMinY) ? 2 * absMaxY : 2 * absMinY;

            // set the min scale to 1% of current value.
            long minDeltaY = (long) (.01f * mPerformanceItems.get(0).getValue().getMicroCents());
            if (deltaY < minDeltaY) {
                deltaY = minDeltaY;
            }

            mScaleY =  (float)(mHeight - (2 * GRAPH_PADDING_HORIZONTAL)) / deltaY ;
            mOffsetY = mHeight / 2; // 0.0 will be the vertical center

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));

            // get market start time on same day of first x
            long firstX = mPerformanceItems.get(0).getTimestamp().getTime();
            cal.setTimeInMillis(firstX);
            cal.set(Calendar.HOUR_OF_DAY, 6);
            cal.set(Calendar.MINUTE, 30);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            mMarketStartTime = cal.getTimeInMillis();

            // set the start to 30 mins b4 market close
            cal.set(Calendar.MINUTE, 0);
            long startScaleX = cal.getTimeInMillis();

            // get the market end tme
            cal.set(Calendar.HOUR_OF_DAY, 13);
            cal.set(Calendar.MINUTE, 0);
            mMarketEndTime = cal.getTimeInMillis();

            // set the end scale to 30 mins after market close
            cal.set(Calendar.MINUTE, 30);
            long endScaleX = cal.getTimeInMillis();

            // see if there is a sample after the market close and extend the end if so
            long lastX = mPerformanceItems.get(mPerformanceItems.size() - 1).getTimestamp().getTime();
            if (endScaleX < lastX) {
                endScaleX = lastX;
            }

            mScaleX =  (mWidth - (2 * GRAPH_PADDING_HORIZONTAL)) / (float) (endScaleX - startScaleX);
            mOffsetX = startScaleX;

        }
    }

    public void animateGraph() {
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.setDuration(ANIMATION_DURATION_MS);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                Float percentage = (Float) animation.getAnimatedValue();

                // change the path effect to determine how much of path to render
                float visibleLength = mPathLength * percentage;
                mPathPaint.setPathEffect(new DashPathEffect(new float[]{visibleLength, mPathLength - visibleLength}, 0));
                invalidate();
            }
        });
        va.start();
    }

    public void bind(List<PerformanceItem> performanceItems) {
        mPerformanceItems = performanceItems;

        long maxY = Long.MIN_VALUE;
        long minY = Long.MAX_VALUE;
        for (int idx = 0; idx < mPerformanceItems.size(); idx++) {
            PerformanceItem performanceItem = mPerformanceItems.get(idx);
            long y = getPerformanceItemValue(performanceItem).getMicroCents();
            if (y > maxY) {
                maxY = y;
                mMaxYIndex = idx;
            }

            if (y < minY) {
                minY = y;
                mMinYIndex = idx;
            }
        }

        calculatePath();
        animateGraph();
    }

    private float scaleX(float x) {
        return  ((x - mOffsetX) * mScaleX);
    }

    private float scaleY(float y) {
        return  mOffsetY - (y * mScaleY);
    }

    /**
     * This function will be used to abstract out which value to graph
     */
    private Money getPerformanceItemValue(PerformanceItem performanceItem) {
        return performanceItem.getTodayChange();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = false;

        float eventX = event.getX();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                long timestamp = (long) (eventX / mScaleX) + mOffsetX;

                if (mExaminerRect == null) {
                    mExaminerRect = new RectF();
                }

                mExaminerRect.set(eventX, GRAPH_PADDING_VERTICAL,
                        eventX + EXAMINER_WIDTH, mHeight - GRAPH_PADDING_VERTICAL);

                mExaminerTime = DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(new Date(timestamp));
                mExaminerTimePaint.getTextBounds(mExaminerTime, 0,
                        mExaminerTime.length(), mExaminerTimeTextBounds);


                mExaminerValue = "";
                Money extrapolated = extrapolateValue(timestamp);
                if (extrapolated != null) {
                    mExaminerValue = extrapolated.getFormatted();
                    mExaminerValuePaint.setColor((extrapolated.getMicroCents() >= 0) ?
                            Color.GREEN : Color.RED);
                }
                mExaminerValuePaint.getTextBounds(mExaminerValue, 0,
                        mExaminerValue.length(), mExaminerValueTextBounds);

                handled = true;
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mExaminerRect = null;
                handled = true;
                break;
        }

        if (handled) {
            invalidate();
        }

        return handled;
    }

    private Money extrapolateValue(long timestamp) {
        Money money = null;

        if (mPerformanceItems.size() > 2) {
            for (int x = 0; x < mPerformanceItems.size(); x++) {
                PerformanceItem performanceItem = mPerformanceItems.get(x);

                if (timestamp <= performanceItem.getTimestamp().getTime()) {
                    if ((timestamp == performanceItem.getTimestamp().getTime()) || (x == 0)) {
                        money = getPerformanceItemValue(performanceItem);
                    } else {

                        PerformanceItem prevPerformanceItem = mPerformanceItems.get(x - 1);


                        long deltaY = getPerformanceItemValue(performanceItem).getMicroCents() -
                                getPerformanceItemValue(prevPerformanceItem).getMicroCents();

                        long deltaX = performanceItem.getTimestamp().getTime() -
                                prevPerformanceItem.getTimestamp().getTime();

                        money = new Money((deltaY *  (timestamp - prevPerformanceItem.getTimestamp().getTime())) / deltaX +
                                getPerformanceItemValue(prevPerformanceItem).getMicroCents());
                    }
                    break;
                }
            }

            if (money == null) {
                money = getPerformanceItemValue(mPerformanceItems.get(mPerformanceItems.size() - 1));
            }
        }

        return money;
    }

}