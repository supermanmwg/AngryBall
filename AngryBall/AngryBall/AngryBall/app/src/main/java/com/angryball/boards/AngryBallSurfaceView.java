package com.angryball.boards;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.angryball.datas.FlyPoint;

import java.util.ArrayList;
import java.util.List;
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

    public AngryBallSurfaceView(Context context) {
        this(context, null);
    }

    public AngryBallSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AngryBallSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
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
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isDrawing = true;
        new Thread(this).start();
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
                        if (x > getWidth() + ballRadius || y > getHeight() + ballRadius)
                            break;
                        y = Math.min(Math.max(0 - ballRadius, y), getHeight() + ballRadius);
                        flyPointList.add(new FlyPoint(x, y));
                      /*  if (x < getWidth() / 2 + ballRadius) {
                            mBallLinePath.lineTo(x, y);
                        }*/
                        mBallLinePath.lineTo(x, y);
                    }

                    if(getActionUpCount() == oldActionUpCount) {
                        mCanvas.drawPath(mBallLinePath, mBallLinePaint);
                    }
                    oldActionUpCount = getActionUpCount();
                }
            } else {
                //  Log.d(TAG, "center x:" + ballCenterX + ", ballScope center x: " + ballScopeCenterX);
            }
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
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                setActionUpCount(0);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        isActionUp = true;
                        setActionUpCount(actionUpCount ++);
                        for (int i = 0; i < flyPointList.size(); i++) {
                            try {
                                Thread.sleep(30);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            ballCenterX = flyPointList.get(i).getX();
                            ballCenterY = flyPointList.get(i).getY();
                            postInvalidate();
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
