package com.angryball.boards;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.DrawFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.angryball.datas.FlyPoint;
import com.angryball.utils.DisplayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

/**
 * Created by weiguangmeng on 16/5/22.
 */
public class AngryBallSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "AngryBallSurfaceView";
    private Canvas mCanvas;
    private boolean isDrawing;
    private SurfaceHolder mHolder;
    private Paint mBallPaint;
    private Paint mBallLinePaint;
    private Paint ballScopePaint;
    private Path mBallLinePath;
    private PathEffect mBallLinePathEffect;

    private int ballCenterX;
    private int ballCenterY;
    private int ballRadius;
    private float ballRadiusScale = (1.0f / 70);

    private int ballScopeRadius;
    private int ballScopeCenterX;
    private int ballScopeCenterY;
    private float ballScopeRadiusScale = (1.0f / 15);

    private int boardWidth;
    private int boardHeight;

    private float cos = 1.0f;
    private float sin = 0f;
    private float initVelocity = 45f;
    private float velocityScale = 1f;
    private List<FlyPoint> flyPointList = new ArrayList<>();
    private boolean isActionUp = false;
    private int saveBallCenterX;
    private int saveBallCenterY;
    private int actionUpCount = 0;
    private int oldActionUpCount = 0;


    //水纹
    // 波纹颜色
    private static final int WAVE_PAINT_COLOR = 0x880000aa;
    // y = Asin(wx+b)+h
    private static final int STRETCH_FACTOR_A = 20;
    private static final int OFFSET_Y = 0;
    // 第一条水波移动速度
    private static final int TRANSLATE_X_SPEED_ONE = 7;
    // 第二条水波移动速度
    private static final int TRANSLATE_X_SPEED_TWO = 5;
    private float mCycleFactorW;

    private int mTotalWidth, mTotalHeight;
    private float[] mYPositions;
    private float[] mResetOneYPositions;
    private float[] mResetTwoYPositions;


    private int mXOffsetSpeedOne;
    private int mXOffsetSpeedTwo;
    private int mXOneOffset;
    private int mXTwoOffset;

    private Paint mWavePaint;
    private int mYoffset = 0;

    //hit rect
    private Paint mHitPaint;
    private int hitWidth;
    private int hitHeight;
    private int hitPosX;
    private int hitPosY;

    private void onSizeChangedHit(int w, int h, int oldw, int oldh) {
        hitWidth = w / 20;
        hitHeight = w / 20;
        hitPosX = getWidth() - hitWidth;
        hitPosY = getHeight() / 2;

    }

    public AngryBallSurfaceView(Context context) {
        this(context, null);
    }

    public AngryBallSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AngryBallSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mHolder = getHolder();
        mHolder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);

        initBall();
        initWave();
        initHitRect();
    }

    private void initBall() {
        mBallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBallLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBallLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mBallLinePaint.setStrokeJoin(Paint.Join.ROUND);
        mBallLinePaint.setStyle(Paint.Style.STROKE);  //画Path必须先设置为Stroke
        mBallLinePath = new Path();
        mBallLinePathEffect = new DashPathEffect(new float[]{10, 10}, 0);
        mBallLinePaint.setPathEffect(mBallLinePathEffect);
        ballScopePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballScopePaint.setStyle(Paint.Style.STROKE);
    }

    private void initWave() {
        // 将dp转化为px，用于控制不同分辨率上移动速度基本一致
        mXOffsetSpeedOne = DisplayUtils.dp2px(getContext(), TRANSLATE_X_SPEED_ONE);
        mXOffsetSpeedTwo = DisplayUtils.dp2px(getContext(), TRANSLATE_X_SPEED_TWO);

        // 初始绘制波纹的画笔
        mWavePaint = new Paint();
        // 去除画笔锯齿
        mWavePaint.setAntiAlias(true);
        // 设置风格为实线
        mWavePaint.setStyle(Paint.Style.FILL);
        // 设置画笔颜色
        mWavePaint.setColor(WAVE_PAINT_COLOR);

    }

    private void initHitRect() {
        mHitPaint = new Paint();
        mHitPaint.setAntiAlias(true);
        mHitPaint.setStyle(Paint.Style.STROKE);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.d(TAG, "onSizeChanged");
        boardWidth = getMeasuredWidth();
        boardHeight = getMeasuredHeight();
        ballRadius = (int) (ballRadiusScale * boardWidth);
        ballScopeRadius = (int) (ballScopeRadiusScale * boardWidth);
        ballScopeCenterX = (boardWidth / 10);
        ballScopeCenterY = boardHeight / 2;
        ballCenterX = ballScopeCenterX;
        ballCenterY = ballScopeCenterY;
        saveBallCenterX = ballCenterX;
        saveBallCenterY = ballCenterY;
        Log.d(TAG, "width is " + boardWidth + "height is " + boardHeight + ",dd");

        onSizeChangedWave(w, h, oldw, oldh);

        onSizeChangedHit(w, h, oldw, oldh);
    }

    private void onSizeChangedWave(int w, int h, int oldw, int oldh) {

        mTotalWidth = w;
        mTotalHeight = h;
        // 用于保存原始波纹的y值
        mYPositions = new float[mTotalWidth];
        // 用于保存波纹一的y值
        mResetOneYPositions = new float[mTotalWidth];
        // 用于保存波纹二的y值
        mResetTwoYPositions = new float[mTotalWidth];

        // 将周期定为view总宽度
        mCycleFactorW = (float) (2 * Math.PI / mTotalWidth);

        // 根据view总宽度得出所有对应的y值
        for (int i = 0; i < mTotalWidth; i++) {
            mYPositions[i] = (float) (STRETCH_FACTOR_A * Math.sin(mCycleFactorW * i) + OFFSET_Y);
        }

        mYoffset = getHeight() - 20;
    }

    private void resetPositonY() {
        // mXOneOffset代表当前第一条水波纹要移动的距离
       int yOneInterval = mYPositions.length - mXOneOffset;
        // 使用System.arraycopy方式重新填充第一条波纹的数据
        System.arraycopy(mYPositions, mXOneOffset, mResetOneYPositions, 0, yOneInterval);
        System.arraycopy(mYPositions, 0, mResetOneYPositions, yOneInterval, mXOneOffset);

        int yTwoInterval = mYPositions.length - mXTwoOffset;
        System.arraycopy(mYPositions, mXTwoOffset, mResetTwoYPositions, 0,
                yTwoInterval);
        System.arraycopy(mYPositions, 0, mResetTwoYPositions, yTwoInterval, mXTwoOffset);

    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isDrawing = true;
        new Thread(this).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isDrawing) {
                    mYoffset += 1;
                    if (mYoffset > ballScopeCenterY) {
                        mYoffset = 0;
                    }
                    //   Log.d(TAG, "offset y is " + mYoffset);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isDrawing = false;
    }

    @Override
    public void run() {
        while (isDrawing) {
            draw();

            if (mYoffset > getHeight() - ballScopeCenterY - ballRadius * 2) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        isDrawing = false;
                        Toast.makeText(getContext(), "Game Over", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void draw() {
        try {
            mCanvas = mHolder.lockCanvas();
            mCanvas.drawColor(Color.WHITE);
            mCanvas.drawCircle(ballCenterX, ballCenterY, ballRadius, mBallPaint);
            if (!isActionUp) {
                mCanvas.drawLine(ballScopeCenterX, ballScopeCenterY, ballCenterX, ballCenterY, ballScopePaint);
            }

            if (Math.abs(ballCenterX - ballScopeCenterX) > 10 && (ballCenterX < ballScopeCenterX)) {
                mBallLinePath.reset();
                if (!isActionUp) {
                    flyPointList.clear();
                }
                mBallLinePath.moveTo(ballCenterX, ballCenterY);

                if (!isActionUp) {
                    for (int t = 0; ; t++) {
                        int x = (int) (initVelocity * velocityScale * cos * t) + saveBallCenterX;
                        int y = (int) (initVelocity * velocityScale * sin * t + t * t / 2) + saveBallCenterY;
                        if (x > getWidth() - ballRadius || y > getHeight() + ballRadius)
                            break;
                        y = Math.min(Math.max(0 - ballRadius, y), getHeight() + ballRadius);
                        flyPointList.add(new FlyPoint(x, y));
                        mBallLinePath.lineTo(x, y);
                    }

                    if (getActionUpCount() == oldActionUpCount) {
                        mCanvas.drawPath(mBallLinePath, mBallLinePaint);
                    }
                    oldActionUpCount = getActionUpCount();
                }
            } else {
                //  Log.d(TAG, "center x:" + ballCenterX + ", ballScope center x: " + ballScopeCenterX);
            }

            resetPositonY();
            for (int i = 0; i < mTotalWidth; i++) {

                // 减400只是为了控制波纹绘制的y的在屏幕的位置，大家可以改成一个变量，然后动态改变这个变量，从而形成波纹上升下降效果
                // 绘制第一条水波纹
                mCanvas.drawLine(i, mTotalHeight - mResetOneYPositions[i] - mYoffset, i,
                        mTotalHeight,
                        mWavePaint);

                // 绘制第二条水波纹
                mCanvas.drawLine(i, mTotalHeight - mResetTwoYPositions[i] - mYoffset, i,
                        mTotalHeight,
                        mWavePaint);
            }

            // 改变两条波纹的移动点
            mXOneOffset += mXOffsetSpeedOne;
            mXTwoOffset += mXOffsetSpeedTwo;

            // 如果已经移动到结尾处，则重头记录
            if (mXOneOffset >= mTotalWidth) {
                mXOneOffset = 0;
            }
            if (mXTwoOffset > mTotalWidth) {
                mXTwoOffset = 0;
            }

            //重画hit区域
            // mCanvas.drawCircle(hitPosX + hitWidth / 2, hitPosY + hitHeight / 2, hitWidth /2 , mHitPaint);
            mCanvas.drawRect(hitPosX, hitPosY, hitPosX + hitWidth, hitPosY + hitHeight, mHitPaint);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (mCanvas != null) {
                mHolder.unlockCanvasAndPost(mCanvas);
            }
        }
    }

    private int mLastEventX;
    private int mLastEventY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        int eventX = (int) event.getX();
        int eventY = (int) event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastEventX = eventX;
                mLastEventY = eventY;
                if (ballCenterX != ballScopeCenterX)
                    return false;
                break;
            case MotionEvent.ACTION_MOVE:
                int offsetX = eventX - mLastEventX;
                int offsetY = eventY - mLastEventY;
                if (isInScopeLeft(offsetX + ballCenterX, offsetY + ballCenterY)) {
                    ballCenterX += offsetX;
                    ballCenterY += offsetY;
                    saveBallCenterX = ballCenterX;
                    saveBallCenterY = ballCenterY;
                }

                int distanX = (ballScopeCenterX - ballCenterX);
                int distanY = (ballScopeCenterY - ballCenterY);
                int radius = (int) Math.sqrt(distanX * distanX + distanY * distanY);
                velocityScale = radius * 1f / ballScopeRadius;
                cos = distanX * 1f / radius;
                sin = distanY * 1f / radius;
                mLastEventX = eventX;
                mLastEventY = eventY;
                break;
            case MotionEvent.ACTION_UP:
                setActionUpCount(0);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        isActionUp = true;
                        setActionUpCount(actionUpCount++);
                        for (int i = 0; i < flyPointList.size(); i++) {
                            try {
                                Thread.sleep(40);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            ballCenterX = flyPointList.get(i).getX();
                            ballCenterY = flyPointList.get(i).getY();
                        }

                        for (int j = flyPointList.size() - 1; j > 0; j--) {
                            int lastFlyY = flyPointList.get(j).getY();
                            int lastFlyX = flyPointList.get(j).getX();
                            if (lastFlyY > hitPosY && lastFlyY < hitPosY + hitHeight && lastFlyX > hitPosX && lastFlyX < hitPosX + hitWidth) {
                                post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(), "hit", Toast.LENGTH_SHORT).show();
                                        mYoffset -= 40;
                                        hitPosY = (int) (Math.random() * (getHeight() - Math.max(STRETCH_FACTOR_A, mYoffset + STRETCH_FACTOR_A)));
                                        ballScopeCenterY = Math.max(STRETCH_FACTOR_A * 2, STRETCH_FACTOR_A * 2 + (int) (Math.random() * (getHeight() - Math.max(STRETCH_FACTOR_A * 2, mYoffset + STRETCH_FACTOR_A * 2))));
                                        ballCenterY = ballScopeCenterY;
                                    }
                                });
                                break;
                            }
                        }
                        ballCenterY = ballScopeCenterY;
                        ballCenterX = ballScopeCenterX;
                        isActionUp = false;

                    }
                }).start();
                break;
        }

        return true;
    }

    private boolean isInScopeLeft(float x, float y) {
        float disX = x - ballScopeCenterX;
        float disY = y - ballScopeCenterY;
        float distance = (float) Math.sqrt(disX * disX + disY * disY);
        if (distance <= ballScopeRadius) {
            return true;
        }

        return false;
    }

    public synchronized int getActionUpCount() {
        return actionUpCount;
    }

    public synchronized void setActionUpCount(int actionUpCount) {
        this.actionUpCount = actionUpCount;
    }
}
