/*
 * Copyright (C) 2012 Rayworks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spzone.android.ctrl;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ScrollButton extends View {
    private static final int LEFT = 0;

    private static final int RIGHT = 1;

    /***
     * The interface to notify other observers the state changed
     */
    public interface OnButtonSwitched {
        /**
         * @param sideId 0 for LEFT, 1 for RIGHT
         */
        public void onSwitch(int sideId);
    }

    private RectF mBound, mActiveBound;

    private Paint mBkgPaint;

    private float mRoundRadio = 0.0f;

    private float mRoundInnerRadio = 0.0f;

    // the horizontal max distance which tells us whether the active block
    // needs to move to the other side finally
    private float mMaxDistToSwitch = 0.0f;

    private Paint mSlideBoardPaint;

    private Paint mTextPaint;

    private int mTextBkgColor = 0xFFFF0000;

    private int mTextColor = 0xFF000000;

    private int mTextSize = 26;

    /***
     * The left text content
     */
    private String mLeftText = "Tline";

    /***
     * The right text content
     */
    private String mRightText = "Kline";

    // We can be in one of these states
    static final int NONE = 0;

    static final int PRESSED = 1;

    static final int MOVE = 2;

    int mode = NONE;

    private boolean leftSidePressed = false;

    private boolean textSwitched = false;

    private float mStartX = 0;

    /***
     * init selected side (0 for left side, 1 for right)
     */
    private int mInitSidePosition = 0;

    /***
     * vertical space between the inner board and the outside one
     */
    private final int verticalBoundInterval = 2;

    public OnButtonSwitched mListener;

    public ScrollButton(Context context) {
        super(context);
        initComponents();
    }

    public ScrollButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ScrollButton);
        CharSequence s = a.getString(R.styleable.ScrollButton_leftText);
        if (s != null) {
            mLeftText = s.toString();
        }
        CharSequence t = a.getString(R.styleable.ScrollButton_rightText);
        if (t != null) {
            mRightText = t.toString();
        }

        // retrieve the color(s) to be used for this view and apply them.
        mTextColor = a.getColor(R.styleable.ScrollButton_textColor, 0xFF000000);
        mTextBkgColor = a.getColor(R.styleable.ScrollButton_selectedBkgColor, 0xFFFF0000);
        mInitSidePosition = a.getInt(R.styleable.ScrollButton_initSidePosition, 0);
        int textSize = a.getDimensionPixelOffset(R.styleable.ScrollButton_textSize, 0);
        if (textSize > 0) {
            mTextSize = textSize;
        }
        a.recycle();

        initComponents();
    }

    private void initComponents() {
        mBound = new RectF();
        mActiveBound = new RectF();
        mBkgPaint = new Paint();
        mBkgPaint.setStyle(Paint.Style.STROKE);
        mBkgPaint.setAntiAlias(true);
        mBkgPaint.setColor(Color.LTGRAY);

        mSlideBoardPaint = new Paint();
        mSlideBoardPaint.setColor(mTextBkgColor);
        mSlideBoardPaint.setAntiAlias(true);
        mSlideBoardPaint.setStyle(Style.FILL_AND_STROKE);

        mTextPaint = new Paint();
        mTextPaint.setColor(mTextColor);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(mTextSize);

        mRoundRadio = 15.0f;
        mRoundInnerRadio = 10.0f;
    }

    /** property methods **/

    public void setLeftText(String leftText) {
        mLeftText = leftText;
        requestLayout();
        invalidate();
    }

    public void setRightText(String rightText) {
        mRightText = rightText;
        requestLayout();
        invalidate();
    }

    public void setFontSize(int txtSize) {
        mTextSize = txtSize;
        requestLayout();
        invalidate();
    }

    public void setTextColor(int color) {
        mTextColor = color;
        if (mTextPaint != null) {
            mTextPaint.setColor(mTextColor);
        }
        invalidate();
    }

    /***
     * set initial selected text side
     * 
     * @param sideIndex 0 for left; 1 for right
     */
    public void setInitialSelectedSide(int sideIndex) {
        mInitSidePosition = sideIndex;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
    }

    protected int measureHeight(int heightMeasureSpec) {
        int result = 0;
        int specMode = MeasureSpec.getMode(heightMeasureSpec);
        int specSize = MeasureSpec.getSize(heightMeasureSpec);
        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            int txtHeight = (int) (- mTextPaint.ascent() + mTextPaint.descent());
            Log.i("ScrollButton", "text height " + txtHeight);
            result = (int) (getPaddingTop() + getPaddingBottom() + txtHeight);
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    protected int measureWidth(int widthMeasureSpec) {
        int result = getSuggestedMinimumWidth();

        int specMode = MeasureSpec.getMode(widthMeasureSpec);
        int specSize = MeasureSpec.getSize(widthMeasureSpec);
        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            // keep the text-bound padding 0.5x totalTextWidth
            result = (int) ((mTextPaint.measureText(mLeftText) + mTextPaint.measureText(mRightText)) * 1.5f)
                    + getPaddingLeft() + getPaddingRight();
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }

        return result;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mBound.set(0, 0, w, h);
        mMaxDistToSwitch = Math.max(w / 4, 2);
        setSliderPosition(w, h, mInitSidePosition == 0);
        invalidate();
    }

    /***
     * set inner rect position
     * 
     * @param w the width of outside rect
     * @param h the height of outside rect
     * @param onLeftSide true, if the Slider is on the left side; else false.
     */
    protected void setSliderPosition(float w, float h, boolean onLeftSide) {
        if (!onLeftSide)
            mActiveBound.set(w / 2, verticalBoundInterval, w - 2, h - verticalBoundInterval); // right
        else
            mActiveBound.set(2, verticalBoundInterval, w / 2, h - verticalBoundInterval);
    }

    public void setButtonSwitchListener(OnButtonSwitched listener) {
        mListener = listener;
    }

    private void notifyChange(int sideId) {
        if (mListener != null) {
            mListener.onSwitch(sideId);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float currX = event.getX();
        float currY = event.getY();

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (mBound.contains(currX, currY) && !mActiveBound.contains(currX, currY)) {
                    // jump immediately
                    if (currX < mBound.width() / 2) {
                        setSliderPosition(mBound.width(), mBound.height(), true);
                        notifyChange(LEFT);
                    } else {
                        setSliderPosition(mBound.width(), mBound.height(), false);
                        notifyChange(RIGHT);
                    }
                    invalidate();
                } else {
                    if (!mBound.contains(currX, currY)) {
                        mode = NONE;
                        return false;
                    }
                    mode = PRESSED;
                    mStartX = currX;
                    leftSidePressed = mStartX < mBound.width() / 2;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mode == MOVE) {
                    mode = NONE;
                    float dlt = Math.abs(currX - mStartX);
                    if (dlt >= mMaxDistToSwitch) {
                        // move to the other side
                        if (leftSidePressed)
                            jumpToRightSide();
                        else
                            jumpToLeftSide();

                    } else {
                        // move back
                        if (leftSidePressed)
                            jumpToLeftSide();
                        else
                            jumpToRightSide();
                    }

                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == PRESSED || mode == MOVE) {
                    float dlt = 0.0f;
                    dlt = Math.abs(currX - mStartX);
                    if (leftSidePressed && currX > mStartX) {
                        if (dlt > 0.5 && dlt <= mBound.width() / 2) {
                            mActiveBound.set(2 + dlt, 2, mBound.width() / 2 + dlt,
                                    mBound.height() - 2);
                            textSwitched = dlt >= mMaxDistToSwitch;
                            invalidate();
                            mode = MOVE;
                        }
                    } else if (!leftSidePressed && currX < mStartX) {
                        if (dlt > 0.5 && dlt <= mBound.width() / 2) {
                            mActiveBound.set(mBound.width() / 2 - dlt, 2, mBound.width() - 2 - dlt,
                                    mBound.height() - 2);
                            textSwitched = dlt >= mMaxDistToSwitch;
                            invalidate();
                            mode = MOVE;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_OUTSIDE:
                mode = NONE;
                break;
        }

        return true;
    }

    private void jumpToLeftSide() {
        setSliderPosition(mBound.width(), mBound.height(), true);
        notifyChange(LEFT);
        invalidate();
    }

    private void jumpToRightSide() {
        setSliderPosition(mBound.width(), mBound.height(), false);
        notifyChange(RIGHT);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // respect customized background 
        if(getBackground() == null){
	        // draw the full bkg
	        canvas.drawRoundRect(mBound, mRoundRadio, mRoundRadio, mBkgPaint);
        }
        canvas.drawRoundRect(mActiveBound, mRoundInnerRadio, mRoundInnerRadio, mSlideBoardPaint);

        // draw texts
        mTextPaint.setTextAlign(Paint.Align.LEFT);

        float rightTextWidth = mTextPaint.measureText(mRightText);
        float rightTextXOrg = mBound.width() / 2 + (mBound.width() / 2 - rightTextWidth) / 2;
        float leftTextWidth = mTextPaint.measureText(mLeftText);
        float leftTextXOrg = (mBound.width() / 2 - leftTextWidth) / 2;

        float textYPos = getPaddingTop() - mTextPaint.ascent();

        //Log.i("ScrollButton", "{txtYPos:Top:Bottom }" + textYPos + ":" + getTop() + ":" + getBottom());
        if (mode == MOVE) {
            float newTextXOrg = 0.0f;
            if (leftSidePressed) {
                if (textSwitched) {
                    canvas.drawText(mLeftText, leftTextXOrg, textYPos, mTextPaint);

                    newTextXOrg = mActiveBound.left + (mActiveBound.width() - rightTextWidth) / 2;
                    canvas.drawText(mRightText, newTextXOrg, textYPos, mTextPaint);
                } else {
                    canvas.drawText(mRightText, rightTextXOrg, textYPos, mTextPaint);

                    newTextXOrg = mActiveBound.left + (mActiveBound.width() - leftTextWidth) / 2;
                    canvas.drawText(mLeftText, newTextXOrg, textYPos, mTextPaint);
                }
            } else {
                if (textSwitched) {
                    canvas.drawText(mRightText, rightTextXOrg, textYPos, mTextPaint);

                    newTextXOrg = mActiveBound.left + (mActiveBound.width() - leftTextWidth) / 2;
                    canvas.drawText(mLeftText, newTextXOrg, textYPos, mTextPaint);
                } else {
                    canvas.drawText(mLeftText, leftTextXOrg, textYPos, mTextPaint);

                    newTextXOrg = mActiveBound.left + (mActiveBound.width() - rightTextWidth) / 2;
                    canvas.drawText(mRightText, newTextXOrg, textYPos, mTextPaint);
                }
            }
        } else {
            canvas.drawText(mLeftText, leftTextXOrg, textYPos, mTextPaint);
            canvas.drawText(mRightText, rightTextXOrg, textYPos, mTextPaint);
        }
    }

}
