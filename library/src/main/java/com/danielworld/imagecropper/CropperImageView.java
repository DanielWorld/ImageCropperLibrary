/*
 * Copyright (c) 2016 DanielWorld.
 * @Author Namgyu Park
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
package com.danielworld.imagecropper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.danielworld.imagecropper.listener.OnThumbnailChangeListener;
import com.danielworld.imagecropper.listener.OnUndoRedoStateChangeListener;
import com.danielworld.imagecropper.model.CropSetting;
import com.danielworld.imagecropper.model.DrawInfo;
import com.danielworld.imagecropper.util.BitmapUtil;
import com.danielworld.imagecropper.util.CalculationUtil;
import com.danielworld.imagecropper.util.ConvertUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


/**
 * Created by Namgyu Park on 2016-06-21.
 */
@SuppressLint("AppCompatCustomView")
public class CropperImageView extends ImageView implements CropperInterface{

    Paint mPaint = new Paint();
    Path mRectanglePath = new Path();
    Path mCirclePath = new Path();      // Daniel (2016-12-22 11:25:27): CIRCLE mRectanglePath
    DashPathEffect mDashPathEffect = new DashPathEffect(new float[]{10, 10, 10, 10}, 0);     // Daniel (2017-01-12 11:44:28): dash path effect

    int controlBtnSize = 50; // Daniel (2016-06-21 16:40:26): Radius of Control button
    int controlStrokeSize = 50; // Daniel (2016-06-24 14:32:04): width of Control stroke

    boolean isTouch = false;

    private Drawable[] cropButton = new Drawable[4];    // Daniel (2016-06-21 16:51:49): The drawable to represent Control icon

    /**
     * Daniel (2016-06-21 15:35:22): 4 coordinate spot (Start from right-top to clockwise)
     */
    private Point[] coordinatePoints = new Point[4];

    /**
     * Daniel (2017-01-13 11:18:16): the latest touched coordinate spot index (Start from right-top to clockwise)
     */
    private Set<Integer> mTouchedCoordinatePointIndex = new HashSet<>();

    /**
     * the standard point
     */
    private Point centerPoint = new Point();

    private RectF mCropRect = new RectF();    // Daniel (2016-06-22 14:08:55): Current cropped shape's rectangle scope

    private int mDrawWidth, mDrawHeight;    // Daniel (2016-06-22 14:26:01): Current visible ImageView's width, height

    private CropMode mCropMode = CropMode.CROP_STRETCH;
    private ShapeMode mShapeMode = ShapeMode.RECTANGLE;
    private ControlMode mControlMode = ControlMode.FREE;
    private UtilMode mUtilMode = UtilMode.NONE;
    private CropExtension mCropExtension = CropExtension.jpg;

    private Path drawPath;
    private Paint drawPaint;
    private ArrayList<DrawInfo> arrayDrawInfo = new ArrayList<>();
    private ArrayList<DrawInfo> arrayUndoneDrawInfo = new ArrayList<>();

    private OnUndoRedoStateChangeListener onUndoRedoStateChangeListener;
    private OnThumbnailChangeListener onThumbnailChangeListener;

    private File dstFile;   // Daniel (2016-06-24 11:47:43): if user set dstFile, Cropped Image will be set to this file!
    private boolean isNewFile = true;  // Daniel (2016-11-10 21:26:20): if user crop image, the file should be created new one?

	private int imageDegree = 0; // Daniel (2016-07-25 15:10:14): Get degree when image was set!

	private float insetRatio = 0.2f;	// Daniel (2016-08-31 14:07:18): margin between outside border of Bitmap and 4 Crop rectangle border
    private float thumbnailSizeRatio = 30f;     // Daniel (2017-01-19 17:12:38): the thumbnail size ratio which compares to original size

    private final float limitSizeFactor = 1.5f; // Daniel (2016-10-24 17:02:30): the least size of 4 points area factor

    public CropperImageView(Context context) {
        this (context, null);
    }

    public CropperImageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Daniel (2016-07-15 18:09:16): below 4.0.4 there is issue with clip mRectanglePath java.lang.UnsupportedOperationException
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        setScaleType(ScaleType.MATRIX);

        for (int i = 0; i < coordinatePoints.length; i++) {
            coordinatePoints[i] = new Point();
        }

        for (int i = 0; i < cropButton.length; i++) {
            cropButton[i] = getResources().getDrawable(R.drawable.crop_ic_indicator);
        }

        controlBtnSize = ConvertUtil.convertDpToPixel(20);  // Daniel (2016-06-22 16:26:13): set Control button size
        controlStrokeSize = ConvertUtil.convertDpToPixel(2);    // Daniel (2016-06-24 14:32:31): set Control stroke size

        setOnTouchListener(mTouchListener);
    }

    @Override
    public void setCropSetting(CropSetting cropSetting) {
        if (cropSetting != null) {
            this.mCropMode = cropSetting.getCropMode();
            this.mShapeMode = cropSetting.getShapeMode();
            this.mControlMode = cropSetting.getControlMode();
            this.mUtilMode = cropSetting.getUtilMode();
            this.mCropExtension = cropSetting.getCropExtension();

            this.insetRatio = cropSetting.getCropInsetRatio() / 200f;
            this.thumbnailSizeRatio = cropSetting.getThumbnailSizeRatio();

            isTouch = false;

            invalidate();
        }
    }

    @Override
    public void setShapeMode(ShapeMode mode) {
        if (mode != null) {
            this.mShapeMode = mode;

            invalidate();
        }
    }

    @Override
    public void setControlMode(ControlMode mode) {
        if (mode != null) {
            this.mControlMode = mode;

            invalidate();
        }
    }

    @Override
    public void setCropExtension(CropExtension mode) {
        if (mode != null) {
            this.mCropExtension = mode;
        }
    }

    @Override
    public void setCropMode(CropMode mode) {
        if (mode != null) {
            this.mCropMode = mode;

            isTouch = false;

            invalidate();
        }
    }

    @Override
    public void setUtilMode(UtilMode mode) {
        if (mode != null) {
            this.mUtilMode = mode;

            if (mUtilMode == UtilMode.PENCIL) {
                setPenPaint(Color.BLUE, 5);
            } else {
                setEraserPaint(Color.WHITE, 10);
            }
        }
    }

    private float mX, mY;

    private void pathInitialize() {
        drawPath = new Path();
    }

    private void setPenPaint(int color, int width) {
        drawPaint = new Paint(Paint.DITHER_FLAG);  // smoothly
        drawPaint.setAntiAlias(true);
        drawPaint.setDither(true); // decrease color of Image when device isn't good.
        drawPaint.setColor(color);
        drawPaint.setStyle(Paint.Style.STROKE);  // border
        drawPaint.setStrokeJoin(Paint.Join.ROUND);  // the shape that end of line
        drawPaint.setStrokeCap(Paint.Cap.ROUND);  // the end of line's decoration
        drawPaint.setStrokeWidth(ConvertUtil.convertDpToPixel(width));  // line's width
        pathInitialize();
    }

    private void setEraserPaint(int color, int width) {
        drawPaint = new Paint(Paint.DITHER_FLAG);
        drawPaint.setColor(color);
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Paint.Style.STROKE);  // border
        drawPaint.setStrokeJoin(Paint.Join.ROUND);  // the shape that end of line
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        drawPaint.setStrokeWidth(ConvertUtil.convertDpToPixel(width));
        pathInitialize();
    }

    private void drawActionDown(float x, float y) {
        drawPath = new Path();
        arrayDrawInfo.add(new DrawInfo(drawPath, drawPaint));
        drawPath.moveTo(x, y);
        mX = x;
        mY = y;

        invalidate();
    }

    private void drawActionMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);

        if (dx >= 4 || dy >= 4) {
            drawPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);  // draw arc.
            mX = x;
            mY = y;
        }
//
        invalidate();
    }

    private void drawActionUp() {
        drawPath.lineTo(mX, mY);

        invalidate();

        if (arrayDrawInfo.size() > 0 && onUndoRedoStateChangeListener != null) {
            onUndoRedoStateChangeListener.onUndoAvailable(true);
        }
    }

	@Override
	public void setCropInsetRatio(float percent) {
		if (percent < 10f || percent > 90f)
			return;

		insetRatio = percent / 200f;
	}

	@Override
    public void setCustomImageBitmap(final Bitmap bitmap) {
        setCustomImageBitmap(bitmap, 0, true);
    }

    @Override
    public void setCustomImageBitmap(final Bitmap bitmap, final int degree) {
        setCustomImageBitmap(bitmap, degree, true);
    }

    private void setCustomImageBitmap(final Bitmap bitmap, final int degree, boolean isNewFile) {
        this.isNewFile = isNewFile;

        imageDegree = (degree + 360) % 360;	// get degree parameter

        initializeDrawSetting();

        setImageBitmap(bitmap);

        try {
			post(new Runnable() {
				@Override
				public void run() {
					// 1. Update base Matrix to fit ImageView
					updateBaseMatrix(getDrawable());
					// 2. Rotate ImageView with degree
					mSuppMatrix.setRotate(degree % 360);
					checkAndDisplayMatrix();    // applied
					// 3. Resize Bitmap to fit ImageView Screen
					resizeImageToFitScreen();

					isTouch = false;
					invalidate();
				}
			});
        } catch (Exception e) {
            isTouch = false;
        }
    }

    @Override
    public void setCustomImageFile(File file) {
        try {
            dstFile = file;

            setCustomImageBitmap(BitmapUtil.getBitmap(getContext(), file), 0, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setCustomImageFile(File file, int degree) {
        try {
            dstFile = file;

            setCustomImageBitmap(BitmapUtil.getBitmap(getContext(), file), degree, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setCustomImageFile(File file, int degree, boolean isNewFile) {
        try {
            if (!isNewFile) dstFile = file;

            setCustomImageBitmap(BitmapUtil.getBitmap(getContext(), file), degree, isNewFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize all draw settings
     */
    private void initializeDrawSetting() {
        try {
            arrayDrawInfo.clear();
            arrayUndoneDrawInfo.clear();
        } catch (Exception ignored){}
    }

    @Override
    public synchronized void setRotationTo(float degrees) {
        setPreviousScale();

		// Daniel (2016-07-27 19:12:21): update current image degree
		imageDegree = (int) ((degrees + 360) % 360);

        mSuppMatrix.setRotate((degrees + 360) % 360);
		checkAndDisplayMatrix(); // applied

        resizeImageToFitScreen();

        setCurrentDegree(degrees, false);

        isTouch = false;
        invalidate();
    }

    @Override
    public synchronized void setRotationBy(float degrees) {
        setPreviousScale();

		// Daniel (2016-07-27 18:09:28): update current image degree
		imageDegree = (int) (imageDegree + degrees + 360) % 360;

        mSuppMatrix.postRotate((degrees + 360) % 360);
		checkAndDisplayMatrix(); // applied

        resizeImageToFitScreen();

        setCurrentDegree(degrees, true);

        isTouch = false;
        invalidate();
    }

    float mPreScale = 0.0f;     // Daniel (2016-06-29 14:35:07): This scale is for previous mRectanglePath state
    private void setPreviousScale(){
        mPreScale = mMinScale;
    }

    private void setCurrentDegree(float degree, boolean rotateBy) {
        // Daniel (2016-06-29 14:00:11): Rotate draw mRectanglePath
        for (DrawInfo v : arrayDrawInfo) {
            RectF rectF = getDisplayRect();

            Matrix mMatrix = new Matrix();
            mMatrix.postScale(getScale() / mPreScale, getScale() / mPreScale, rectF.centerX(), rectF.centerY());

            if (rotateBy)
                mMatrix.postRotate(degree, rectF.centerX(), rectF.centerY());
            else
                mMatrix.setRotate(degree, rectF.centerX(), rectF.centerY());

            v.getPath().transform(mMatrix);
        }

        // Daniel (2016-06-29 14:00:11): Rotate unDraw mRectanglePath
        for (DrawInfo v : arrayUndoneDrawInfo) {
            RectF rectF = getDisplayRect();

            Matrix mMatrix = new Matrix();
            mMatrix.postScale(getScale() / mPreScale, getScale() / mPreScale, rectF.centerX(), rectF.centerY());

            if (rotateBy)
                mMatrix.postRotate(degree, rectF.centerX(), rectF.centerY());
            else
                mMatrix.setRotate(degree, rectF.centerX(), rectF.centerY());

            v.getPath().transform(mMatrix);
        }
    }


    @Override
    public synchronized void setReverseUpsideDown() {

        mSuppMatrix.preScale(1, -1);
        checkAndDisplayMatrix();

        isTouch = false;
        invalidate();
    }

    @Override
    public synchronized void setReverseRightToLeft() {

        mSuppMatrix.preScale(-1, 1);
        checkAndDisplayMatrix();

        isTouch = false;
        invalidate();
    }

    @Override
    public synchronized void setUndo() {
        if (arrayDrawInfo.size() > 0){
            arrayUndoneDrawInfo.add(arrayDrawInfo.remove(arrayDrawInfo.size() - 1));

            invalidate();
        }

        if (arrayDrawInfo.size() > 0) {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onUndoAvailable(true);
        }
        else {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onUndoAvailable(false);
        }

        if (arrayUndoneDrawInfo.size() > 0) {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onRedoAvailable(true);
        }
        else {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onRedoAvailable(false);
        }
    }

    @Override
    public synchronized void setRedo() {
        if (arrayUndoneDrawInfo.size() > 0){
            arrayDrawInfo.add(arrayUndoneDrawInfo.remove(arrayUndoneDrawInfo.size() - 1));

            invalidate();
        }

        if (arrayDrawInfo.size() > 0) {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onUndoAvailable(true);
        }
        else {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onUndoAvailable(false);
        }

        if (arrayUndoneDrawInfo.size() > 0) {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onRedoAvailable(true);
        }
        else {
            if (onUndoRedoStateChangeListener != null)
                onUndoRedoStateChangeListener.onRedoAvailable(false);
        }
    }

    @Override
    public void setUndoRedoListener(OnUndoRedoStateChangeListener listener) {
        onUndoRedoStateChangeListener = listener;
    }

    @Override
    public void setThumbnailChangeListener(OnThumbnailChangeListener listener) {
        onThumbnailChangeListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas == null) return;

        mDrawWidth = canvas.getWidth();
        mDrawHeight = canvas.getHeight();

        if (mCropMode == CropMode.NONE && mUtilMode != UtilMode.NONE) {

            for (DrawInfo v : arrayDrawInfo) {
                canvas.drawPath(v.getPath(), v.getPaint());
            }
        }
        else if (mCropMode != CropMode.NONE) {
            canvas.save();
            if (!isTouch) {

                // Daniel (2016-06-22 16:29:33): Crop size should maintain the (ImageView / 2) size
                RectF f = getDisplayRect();
                if (f != null && f.width() != 0 && f.height() != 0) {
					float width = f.width();
					float height = f.height();

                    float marginWidth = (f.width() * insetRatio);
                    float marginHeight = (f.height() * insetRatio);

                    if (mShapeMode == ShapeMode.CIRCLE) {
                        // Daniel (2016-12-22 11:41:12): if width is smaller than height
                        // 1. marginHeight should be larger to get same size as width
                        // vice versa
                        if (width < height) {
                            float currentHalfWidth = (width / 2) - marginWidth;
                            marginHeight = (height / 2) - currentHalfWidth;
                        }
                        else if (width > height) {
                            float currentHalfHeight = (height / 2) - marginHeight;
                            marginWidth = (width / 2) - currentHalfHeight;
                        }
                    }

                    centerPoint.set((int) (marginWidth + f.left), (int) (marginHeight + f.top));
                    coordinatePoints[0].set((int) (width - marginWidth + f.left), centerPoint.y);
                    coordinatePoints[1].set(coordinatePoints[0].x, (int) (height - marginHeight + f.top));
                    coordinatePoints[2].set(centerPoint.x, coordinatePoints[1].y);
                    coordinatePoints[3].set(centerPoint.x, centerPoint.y);
                } else {
                    int width = canvas.getWidth() / 2;
                    int height = canvas.getHeight() / 2;

                    if (mShapeMode == ShapeMode.CIRCLE) {
                        // Daniel (2016-12-22 11:41:12): if width is smaller than height
                        // 1. marginHeight should be larger to get same size as width
                        // vice versa
                        if (width < height) {
                            height = width;
                        }
                        else if (width > height) {
                            width = height;
                        }
                    }

                    centerPoint.set(width - (width / 2), height - (height / 2));
                    coordinatePoints[0].set(width + (width / 2), centerPoint.y);
                    coordinatePoints[1].set(coordinatePoints[0].x, height + (height / 2));
                    coordinatePoints[2].set(centerPoint.x, coordinatePoints[1].y);
                    coordinatePoints[3].set(centerPoint.x, centerPoint.y);
                }
                mRectanglePath.reset();
                mRectanglePath.moveTo(centerPoint.x, centerPoint.y);
                for (Point p : coordinatePoints) {
                    mRectanglePath.lineTo(p.x, p.y);
                }

                // Daniel (2016-06-22 14:10:05): initialize current mCropRect
                mCropRect.set(getCropLeft(), getCropTop(), getCropRight(), getCropBottom());
            } else {
                mRectanglePath.reset();

                // Daniel (2016-06-21 16:55:26): centerPoint.x, centerPoint.y is standard of coordinatePoints[3]
                centerPoint.x = coordinatePoints[3].x;
                centerPoint.y = coordinatePoints[3].y;

                mRectanglePath.moveTo(centerPoint.x, centerPoint.y);
                mRectanglePath.lineTo(coordinatePoints[0].x, coordinatePoints[0].y);

                mRectanglePath.lineTo(coordinatePoints[1].x, coordinatePoints[1].y);
                mRectanglePath.lineTo(coordinatePoints[2].x, coordinatePoints[2].y);
                mRectanglePath.lineTo(coordinatePoints[3].x, coordinatePoints[3].y);

                // Daniel (2016-06-22 14:10:05): initialize current mCropRect
                mCropRect.set(getCropLeft(), getCropTop(), getCropRight(), getCropBottom());
            }

            if (mShapeMode == ShapeMode.RECTANGLE) {
                canvas.clipPath(mRectanglePath, Region.Op.DIFFERENCE);
                canvas.drawColor(getResources().getColor(R.color.crop_image_cover));

                mPaint.setColor(Color.WHITE);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(controlStrokeSize);
                canvas.drawPath(mRectanglePath, mPaint);

                canvas.restore();

                // Daniel (2016-06-21 16:34:28): draw control button
                for (int i = 0; i < coordinatePoints.length; i++) {
                    cropButton[i].setBounds(coordinatePoints[i].x - controlBtnSize, coordinatePoints[i].y - controlBtnSize, coordinatePoints[i].x + controlBtnSize, coordinatePoints[i].y + controlBtnSize);
                    cropButton[i].draw(canvas);
                }
            }
            else if (mShapeMode == ShapeMode.CIRCLE) {
                mCirclePath.reset();

                // Daniel (2016-12-21 18:28:38): get information from mRectangleRect
                mCirclePath.moveTo(mCropRect.centerX(), mCropRect.top);
                mCirclePath.addCircle(mCropRect.centerX(), mCropRect.centerY(), Math.min(mCropRect.width() / 2, mCropRect.height() / 2), Path.Direction.CW);

                canvas.clipPath(mCirclePath, Region.Op.DIFFERENCE);
                canvas.drawColor(getResources().getColor(R.color.crop_image_cover));

                mPaint.setColor(Color.WHITE);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(controlStrokeSize);

                // Daniel (2017-01-12 11:42:27): draw circle
                canvas.drawCircle(mCropRect.centerX(), mCropRect.centerY(), Math.min(mCropRect.width() / 2, mCropRect.height() / 2), mPaint);

                mPaint.setPathEffect(mDashPathEffect);      // Daniel (2017-01-12 11:45:36): dash effect
                mPaint.setStrokeWidth(5.0f);
                mPaint.setColor(Color.GRAY);

                // Daniel (2017-01-12 11:41:15): draw outer rectangle line
                canvas.drawPath(mRectanglePath, mPaint);

                mPaint.reset();

                canvas.restore();

                // Daniel (2016-06-21 16:34:28): draw control button
                for (int i = 0; i < coordinatePoints.length; i++) {
                    cropButton[i].setBounds(coordinatePoints[i].x - controlBtnSize, coordinatePoints[i].y - controlBtnSize, coordinatePoints[i].x + controlBtnSize, coordinatePoints[i].y + controlBtnSize);
                    cropButton[i].draw(canvas);
                }
            }
        }
    }

    OnTouchListener mTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            isTouch = true;

            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    if (mCropMode == CropMode.NONE && mUtilMode != UtilMode.NONE) {
                        float X = event.getX();
                        float Y = event.getY();

                        if (isCorrectCoordinates(X, Y))
                            drawActionDown(X, Y);

                    } else if (mCropMode != CropMode.NONE) {
                        float X = event.getX();
                        float Y = event.getY();
                        controlTouchInCropDown(X, Y);
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN: {
                    // Action pointer down
                }
                break;
                case MotionEvent.ACTION_MOVE:

                    if (mCropMode == CropMode.NONE && mUtilMode != UtilMode.NONE) {
                        float X = event.getX();
                        float Y = event.getY();

                        Pair<Float, Float> pair = correctCoordinates(X, Y);
                        X = pair.first;
                        Y = pair.second;

                        drawActionMove(X, Y);
                    }
                    else if (mCropMode != CropMode.NONE) {
                        for (int index = 0; index < event.getPointerCount(); index++) {
                            float X = event.getX(index);
                            float Y = event.getY(index);

                            controlTouchInCropMove(X, Y);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP: {
                    // Action pointer up
                }
                break;
                case MotionEvent.ACTION_UP:
                    if (mCropMode == CropMode.NONE && mUtilMode != UtilMode.NONE){
                        drawActionUp();
                    } else if (mCropMode != CropMode.NONE) {

                        // Daniel (2017-01-13 14:37:40): clear touched point index
                        mTouchedCoordinatePointIndex.clear();

                        if (onThumbnailChangeListener != null)
                            onThumbnailChangeListener.onThumbnailChanged(getCropStretchThumbnailBitmap());
                    }
                    break;
            }
            return true;
        }
    };

    /**
     * Check if target X, Y is correct coordinates
     * @param targetX
     * @param targetY
     * @return
     */
    private boolean isCorrectCoordinates(float targetX, float targetY){
        int borderSize = controlStrokeSize;

        // Daniel (2016-06-21 19:03:45): touch event should not go outside of screen
        if (targetX <= borderSize)
            return false;
        if (targetY <= borderSize)
            return false;

        // Daniel (2016-06-22 14:26:45): touch Event should not right or bottom outside of screen
        if (targetX >= mDrawWidth - borderSize)
            return false;
        if (targetY >= mDrawHeight - borderSize)
            return false;

        RectF displayRect = getDisplayRect();

        // Daniel (2016-06-22 16:19:05): touch event should not go outside of visible image
        if (displayRect != null) {
            if (targetX >= displayRect.right - borderSize)
                return false;
            if (targetX <= displayRect.left + borderSize)
                return false;
            if (targetY >= displayRect.bottom - borderSize)
                return false;
            if (targetY <= displayRect.top + borderSize)
                return false;
        }

        return true;
    }

    /**
     * Correct unspecified X, Y coordinates
     * @param X
     * @param Y
     * @return
     */
    private Pair<Float, Float> correctCoordinates(float X, float Y) {
        Integer borderSize = controlStrokeSize;

        // Daniel (2016-06-21 19:03:45): touch event should not go outside of screen
        if (X <= borderSize)
            X = borderSize;
        if (Y <= borderSize)
            Y = borderSize;

        // Daniel (2016-06-22 14:26:45): touch Event should not right or bottom outside of screen
        if (X >= mDrawWidth - borderSize)
            X = mDrawWidth - borderSize;
        if (Y >= mDrawHeight - borderSize)
            Y = mDrawHeight - borderSize;

        RectF displayRect = getDisplayRect();

        // Daniel (2016-06-22 16:19:05): touch event should not go outside of visible image
        if (displayRect != null) {
            if (X >= displayRect.right - borderSize)
                X = displayRect.right - borderSize;
            if (X <= displayRect.left + borderSize)
                X = displayRect.left + borderSize;
            if (Y >= displayRect.bottom - borderSize)
                Y = displayRect.bottom - borderSize;
            if (Y <= displayRect.top + borderSize)
                Y = displayRect.top + borderSize;
        }

        return new Pair<>(X, Y);
    }

    float cropDownX, cropDownY;
    /**
     * It only works in Crop Mode for touch event!!
     * @param X
     * @param Y
     */
    private void controlTouchInCropDown(float X, float Y) {
        cropDownX = -1; cropDownY = -1;

        if (isCorrectCoordinates(X, Y)) {
            cropDownX = X;
            cropDownY = Y;

            saveTouchedCoordinatePointIndex(X, Y);
        }
    }

    /**
     * Save the latest touched coordinate point index
     */
    private void saveTouchedCoordinatePointIndex(float X, float Y) {
        if (Math.sqrt(Math.pow(X - coordinatePoints[0].x, 2) + Math.pow(Y - coordinatePoints[0].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(0);
        } else if (Math.sqrt(Math.pow(X - coordinatePoints[1].x, 2) + Math.pow(Y - coordinatePoints[1].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(1);
        } else if (Math.sqrt(Math.pow(X - coordinatePoints[2].x, 2) + Math.pow(Y - coordinatePoints[2].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(2);
        } else if (Math.sqrt(Math.pow(X - coordinatePoints[3].x, 2) + Math.pow(Y - coordinatePoints[3].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(3);
        }
    }

    /**
     * Calculate the latest touched coordinate point index
     */
    private int calculateTouchedCoordinatePointIndex(float X, float Y) {
        if (Math.sqrt(Math.pow(X - coordinatePoints[0].x, 2) + Math.pow(Y - coordinatePoints[0].y, 2)) <= controlBtnSize) {
            return 0;
        } else if (Math.sqrt(Math.pow(X - coordinatePoints[1].x, 2) + Math.pow(Y - coordinatePoints[1].y, 2)) <= controlBtnSize) {
            return 1;
        } else if (Math.sqrt(Math.pow(X - coordinatePoints[2].x, 2) + Math.pow(Y - coordinatePoints[2].y, 2)) <= controlBtnSize) {
            return 2;
        } else if (Math.sqrt(Math.pow(X - coordinatePoints[3].x, 2) + Math.pow(Y - coordinatePoints[3].y, 2)) <= controlBtnSize) {
            return 3;
        } else {
            return -1;
        }
    }

    /**
     * It only works in Crop Mode for touch event!!
     * @param X
     * @param Y
     */
    private void controlTouchInCropMove(float X, float Y) {

        Pair<Float, Float> pair = correctCoordinates(X, Y);
        X = pair.first;
        Y = pair.second;

        if (Math.sqrt(Math.pow(X - coordinatePoints[0].x, 2) + Math.pow(Y - coordinatePoints[0].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(0);
            controlTouchInCropMoveIndex(0, (int) X, (int) Y);

        } else if (Math.sqrt(Math.pow(X - coordinatePoints[1].x, 2) + Math.pow(Y - coordinatePoints[1].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(1);
            controlTouchInCropMoveIndex(1, (int) X, (int) Y);

        } else if (Math.sqrt(Math.pow(X - coordinatePoints[2].x, 2) + Math.pow(Y - coordinatePoints[2].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(2);
            controlTouchInCropMoveIndex(2, (int) X, (int) Y);

        } else if (Math.sqrt(Math.pow(X - coordinatePoints[3].x, 2) + Math.pow(Y - coordinatePoints[3].y, 2)) <= controlBtnSize) {
            mTouchedCoordinatePointIndex.add(3);
            controlTouchInCropMoveIndex(3, (int) X, (int) Y);

        }
        // Daniel (2017-01-13 11:05:49): check if touch down coordinates were inside of control button
        else if (!mTouchedCoordinatePointIndex.isEmpty()){
            for (Integer i : mTouchedCoordinatePointIndex) {
                controlTouchInCropMoveIndex(i, (int) X, (int) Y);
            }
        }
        else if (isTouchInCropRect((int) X, (int) Y)) {
            invalidate();
        }
    }

    private void controlTouchInCropMoveIndex(int index, int X, int Y) {
        switch (index) {
            case 0: {
                if (mControlMode == ControlMode.FIXED) {

                    // Daniel (2017-01-12 14:42:53): in Circle mode, rectangle should maintain square.
                    if (mShapeMode == ShapeMode.CIRCLE) {
                        X = (int) CalculationUtil.rectifyOnProportionalLineX(
                                coordinatePoints[0].x, coordinatePoints[0].y,
                                coordinatePoints[2].x, coordinatePoints[2].y,
                                X, Y
                        );
                        Y = (int) CalculationUtil.rectifyOnProportionalLineY(
                                coordinatePoints[0].x, coordinatePoints[0].y,
                                coordinatePoints[2].x, coordinatePoints[2].y,
                                X, Y
                        );

                        // Daniel (2017-07-14 11:40:44): it could be outside coordinates!
                        if (!isCorrectCoordinates(X, Y)) break;
                    }

                    // RECTANGLE position
                    // moveX = the distance last point X - previous point X
                    // moveY = the distance last point Y - previous point Y
                    int moveX = X - coordinatePoints[0].x;
                    int moveY = Y - coordinatePoints[0].y;

                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[3].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[1].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[0].x;
                        moveX = 0;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[0].y;
                        moveY = 0;
                    }
                    coordinatePoints[1].x += moveX;
                    coordinatePoints[3].y += moveY;
                }
                else if (mControlMode == ControlMode.FREE) {
                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[3].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[1].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[0].x;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[0].y;
                    }
                    int distanceAcross = (int) Math.sqrt(Math.pow(X - coordinatePoints[2].x, 2) + Math.pow(Y - coordinatePoints[2].y, 2));
                    if (distanceAcross < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[0].x;
                        Y = coordinatePoints[0].y;
                    }
                }

                coordinatePoints[0].x = X;
                coordinatePoints[0].y = Y;

                invalidate();
            }
            break;
            case 1: {
                if (mControlMode == ControlMode.FIXED) {

                    // Daniel (2017-01-12 14:42:53): in Circle mode, rectangle should maintain square.
                    if (mShapeMode == ShapeMode.CIRCLE) {
                        X = (int) CalculationUtil.rectifyOnProportionalLineX(
                                coordinatePoints[1].x, coordinatePoints[1].y,
                                coordinatePoints[3].x, coordinatePoints[3].y,
                                X, Y
                        );
                        Y = (int) CalculationUtil.rectifyOnProportionalLineY(
                                coordinatePoints[1].x, coordinatePoints[1].y,
                                coordinatePoints[3].x, coordinatePoints[3].y,
                                X, Y
                        );

                        // Daniel (2017-07-14 11:40:44): it could be outside coordinates!
                        if (!isCorrectCoordinates(X, Y)) break;
                    }

                    // RECTANGLE position
                    // moveX = the distance last point X - previous point X
                    // moveY = the distance last point Y - previous point Y
                    int moveX = X - coordinatePoints[1].x;
                    int moveY = Y - coordinatePoints[1].y;

                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[2].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[0].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[1].x;
                        moveX = 0;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[1].y;
                        moveY = 0;
                    }
                    coordinatePoints[0].x += moveX;
                    coordinatePoints[2].y += moveY;
                }
                else if (mControlMode == ControlMode.FREE) {
                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[2].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[0].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[1].x;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[1].y;
                    }
                    int distanceAcross = (int) Math.sqrt(Math.pow(X - coordinatePoints[3].x, 2) + Math.pow(Y - coordinatePoints[3].y, 2));
                    if (distanceAcross < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[1].x;
                        Y = coordinatePoints[1].y;
                    }
                }

                coordinatePoints[1].x = X;
                coordinatePoints[1].y = Y;

                invalidate();
            }
            break;
            case 2: {
                if (mControlMode == ControlMode.FIXED) {

                    // Daniel (2017-01-12 14:42:53): in Circle mode, rectangle should maintain square.
                    if (mShapeMode == ShapeMode.CIRCLE) {
                        X = (int) CalculationUtil.rectifyOnProportionalLineX(
                                coordinatePoints[2].x, coordinatePoints[2].y,
                                coordinatePoints[0].x, coordinatePoints[0].y,
                                X, Y
                        );
                        Y = (int) CalculationUtil.rectifyOnProportionalLineY(
                                coordinatePoints[2].x, coordinatePoints[2].y,
                                coordinatePoints[0].x, coordinatePoints[0].y,
                                X, Y
                        );

                        // Daniel (2017-07-14 11:40:44): it could be outside coordinates!
                        if (!isCorrectCoordinates(X, Y)) break;
                    }

                    // RECTANGLE position
                    // moveX = the distance last point X - previous point X
                    // moveY = the distance last point Y - previous point Y
                    int moveX = X - coordinatePoints[2].x;
                    int moveY = Y - coordinatePoints[2].y;

                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[1].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[3].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[2].x;
                        moveX = 0;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[2].y;
                        moveY = 0;
                    }
                    coordinatePoints[3].x += moveX;
                    coordinatePoints[1].y += moveY;
                }
                else if (mControlMode == ControlMode.FREE) {
                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[1].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[3].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[2].x;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[2].y;
                    }
                    int distanceAcross = (int) Math.sqrt(Math.pow(X - coordinatePoints[0].x, 2) + Math.pow(Y - coordinatePoints[0].y, 2));
                    if (distanceAcross < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[2].x;
                        Y = coordinatePoints[2].y;
                    }
                }

                coordinatePoints[2].x = X;
                coordinatePoints[2].y = Y;

                invalidate();
            }
            break;
            case 3: {
                if (mControlMode == ControlMode.FIXED) {

                    // Daniel (2017-01-12 14:42:53): in Circle mode, rectangle should maintain square.
                    if (mShapeMode == ShapeMode.CIRCLE) {
                        X = (int) CalculationUtil.rectifyOnProportionalLineX(
                                coordinatePoints[3].x, coordinatePoints[3].y,
                                coordinatePoints[1].x, coordinatePoints[1].y,
                                X, Y
                        );
                        Y = (int) CalculationUtil.rectifyOnProportionalLineY(
                                coordinatePoints[3].x, coordinatePoints[3].y,
                                coordinatePoints[1].x, coordinatePoints[1].y,
                                X, Y
                        );

                        // Daniel (2017-07-14 11:40:44): it could be outside coordinates!
                        if (!isCorrectCoordinates(X, Y)) break;
                    }

                    // RECTANGLE position
                    // moveX = the distance last point X - previous point X
                    // moveY = the distance last point Y - previous point Y
                    int moveX = X - coordinatePoints[3].x;
                    int moveY = Y - coordinatePoints[3].y;

                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[0].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[2].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[3].x;
                        moveX = 0;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[3].y;
                        moveY = 0;
                    }
                    coordinatePoints[2].x += moveX;
                    coordinatePoints[0].y += moveY;
                }
                else if (mControlMode == ControlMode.FREE) {
                    // Daniel (2016-10-08 23:09:36): Each point should not interfere with other points
                    int distanceWidth = Math.abs(X - coordinatePoints[0].x);
                    int distanceHeight = Math.abs(Y - coordinatePoints[2].y);
                    if (distanceWidth < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[3].x;
                    }
                    if (distanceHeight < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        Y = coordinatePoints[3].y;
                    }
                    int distanceAcross = (int) Math.sqrt(Math.pow(X - coordinatePoints[1].x, 2) + Math.pow(Y - coordinatePoints[1].y, 2));
                    if (distanceAcross < (controlBtnSize + controlStrokeSize) * limitSizeFactor) {
                        X = coordinatePoints[3].x;
                        Y = coordinatePoints[3].y;
                    }
                }

                coordinatePoints[3].x = X;
                coordinatePoints[3].y = Y;

                invalidate();
            }
            break;
        }
    }

    private boolean isTouchInCropRect(int X, int Y) {
        if (cropDownX < 0 || cropDownY < 0)
            return false;

        int xMove = (int) (X - cropDownX);
        int yMove = (int) (Y - cropDownY);

        boolean invalidX = false;
        boolean invalidY = false;

        // Daniel (2016-07-11 15:22:50): Check if point is valid!
        for (Point p : coordinatePoints) {
            int pX = p.x;
            int pY = p.y;

            switch (isValidPoint(pX + xMove, pY + yMove)){
                case 0:
                    invalidX = true;
                    break;
                case 1:
                    invalidY = true;
                    break;
                case 2:
                    invalidX = true;
                    invalidY = true;
                    break;
                case 3:
                    break;
            }

        }


        if (invalidX && invalidY)
            return false;

        for (Point p : coordinatePoints) {
            if (!invalidX)
                p.x = p.x + xMove;
            if (!invalidY)
                p.y = p.y + yMove;
        }

        cropDownX = X;
        cropDownY = Y;

        return true;
    }

    /**
     * Check if point is valid <br>
     * <code>0</code> X is invalid<br> <code>1</code> Y is invalid<br> <code>2</code> all invalid<br> <code>3</code> Nothing is invalid
     * @param X
     * @param Y
     * @return <code>0</code> X is invalid<br> <code>1</code> Y is invalid<br> <code>2</code> all invalid<br> <code>3</code> Nothing is invalid
     */
    private int isValidPoint(float X, float Y) {

        boolean xInvalid = false;
        boolean yInvalid = false;

        // Daniel (2016-06-21 19:03:45): touch event should not go outside of screen
        if (X <= controlStrokeSize)
            xInvalid = true;
        if (Y <= controlStrokeSize)
            yInvalid = true;

        // Daniel (2016-06-22 14:26:45): touch Event should not right or bottom outside of screen
        if (X >= mDrawWidth - controlStrokeSize)
            xInvalid = true;
        if (Y >= mDrawHeight - controlStrokeSize)
            yInvalid = true;

        RectF displayRect = getDisplayRect();

        // Daniel (2016-06-22 16:19:05): touch event should not go outside of visible image
        if (displayRect != null) {
            if (X >= displayRect.right - controlStrokeSize)
                xInvalid = true;
            if (X <= displayRect.left + controlStrokeSize)
                xInvalid = true;
            if (Y >= displayRect.bottom - controlStrokeSize)
                yInvalid = true;
            if (Y <= displayRect.top + controlStrokeSize)
                yInvalid = true;
        }

        if (xInvalid && yInvalid)
            return 2;
        else if (xInvalid)
            return 0;
        else if (yInvalid)
            return 1;
        else
            return 3;
    }

	/**
	 * {@link CropMode#CROP_STRETCH} mode
	 * @return
	 */
	private File getCropStretch() {
		Bitmap originalBitmap = getOriginalBitmap();
		int oriWidth = 0;
		int oriHeight = 0;
		if (originalBitmap != null) {
			oriWidth = originalBitmap.getWidth();
			oriHeight = originalBitmap.getHeight();
        } else {
            // Daniel (2016-09-03 23:49:15): If Original Image is null then just leave it!
            return null;
        }

		if (oriWidth == 0 || oriHeight == 0) {
			oriWidth = mDrawWidth;
			oriHeight = mDrawHeight;
		}

//        Log.d("OKAY2", "oriWidth : " + oriWidth);
//        Log.d("OKAY2", "oriHeight : " + oriHeight);

		RectF displayRect = getDisplayRect();

		float X_Factor = (float) oriWidth / displayRect.width();
		float Y_Factor = (float) oriHeight / displayRect.height();

		// if there is rotation issue than you must recalculate Factor
		if (imageDegree % 360 == 90 || imageDegree % 360 == 270) {
			X_Factor = (float) oriHeight / displayRect.width();
			Y_Factor = (float) oriWidth / displayRect.height();
		}

		Matrix mMatrix = new Matrix();
		mMatrix.setRotate(imageDegree, oriWidth * 0.5f, oriHeight * 0.5f);

		Bitmap matrixBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, oriWidth, oriHeight, mMatrix, true);

		float widthGap = Math.abs(mDrawWidth - displayRect.width());
		float heightGap = Math.abs(mDrawHeight - displayRect.height());

		float removeX = displayRect.left;
		float removeY = displayRect.top;

		if (widthGap > heightGap)
			removeY = 0;
		else
			removeX = 0;

		float[] src = new float[]{
				(centerPoint.x - removeX) * X_Factor, (centerPoint.y - removeY) * Y_Factor,
				(coordinatePoints[0].x - removeX) * X_Factor, (coordinatePoints[0].y - removeY) * Y_Factor,
				(coordinatePoints[1].x - removeX) * X_Factor, (coordinatePoints[1].y - removeY) * Y_Factor,
				(coordinatePoints[2].x - removeX) * X_Factor, (coordinatePoints[2].y - removeY) * Y_Factor
		};

		// Daniel (2016-07-01 18:21:54): Find perfect ratio of IMAGE
		double L1 = Math.sqrt(Math.pow(src[0] - src[2], 2) + Math.pow(src[1] - src[3], 2));
		double L2 = Math.sqrt(Math.pow(src[6] - src[4], 2) + Math.pow(src[7] - src[5], 2));

		double M1 = Math.sqrt(Math.pow(src[0] - src[6], 2) + Math.pow(src[1] - src[7], 2));
		double M2 = Math.sqrt(Math.pow(src[2] - src[4], 2) + Math.pow(src[3] - src[5], 2));

		double h = (M1 + M2) / 2;
		double w = (L1 + L2) / 2;

		double diff = Math.abs(L1 - L2) / 2;

		float X2 = src[0];
		float Y2 = src[1];
		float X1 = src[4];
		float Y1 = src[5];
		float CX = src[6];
		float CY = src[7];

		double leftTop = Math.atan((Y2 - CY) / (X2 - CX));
		double leftBottom = Math.atan((Y1 - CY) / (X1 - CX));

		double radian = leftTop - leftBottom;

//                double angle = Math.abs(radian * 180 / Math.PI);
//                Log.d("OKAY2", "angle : " + angle);

		double factor = Math.abs(90 / (radian * 180 / Math.PI));
		double diffFactor = (1 + diff * 1.5 / w);

//                Log.d("OKAY2", "factor : " + factor);
//                Log.d("OKAY2", "diffFactor : " + diffFactor);
//                Log.d("OKAY2", " f / 2 : " +  ((factor + diffFactor) / 2));

		h = h * ((factor + diffFactor) / 2);

		// Daniel (2016-08-06 18:18:14): If h is bigger than original image, then it should be fixed
		// It might happened to be higher than original picture...
        // Daniel (2016-08-08 17:11:30): consider Image's degree!
        if (imageDegree % 360 == 90 || imageDegree % 360 == 270) {
            double wRatio = oriHeight / w;
            double hRatio = oriWidth / h;

            if (h > oriWidth) {
                w = w * hRatio;
                h = oriWidth;    // h = h * (oriWidth / h);
            } else if (w > oriHeight) {
                w = oriHeight;  // w = w * (oriHeight / w);
                h = h * wRatio;
            }
        } else {
            double wRatio = oriWidth / w;
            double hRatio = oriHeight / h;

            if (h > oriHeight) {
                w = w * hRatio;
                h = oriHeight;    // h = h * (oriHeight / h);
            } else if (w > oriWidth) {
                w = oriWidth;      // w = w * (oriWidth / w);
                h = h * wRatio;
            }
        }

		float[] dsc = new float[]{
				0, 0,
				(float) w, 0,
				(float) w, (float) h,
				0, (float) h
		};

        // Daniel (2016-10-24 17:09:54): w and h should be equal or larger than 10
        if (w < 10) w = 10;
        if (h < 10) h = 10;

		Bitmap perfectBitmap = Bitmap.createBitmap((int) w, (int) h, Bitmap.Config.ARGB_8888);

		Matrix matrix = new Matrix();
		matrix.setPolyToPoly(src, 0, dsc, 0, 4);

		Canvas canvas = new Canvas(perfectBitmap);

        if (mShapeMode == ShapeMode.CIRCLE) {
            final int color = 0xff424242;
            final Paint paint = new Paint();

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(color);
            canvas.drawCircle(perfectBitmap.getWidth() / 2, perfectBitmap.getHeight() / 2,
                    perfectBitmap.getWidth() / 2, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(matrixBitmap, matrix, paint);
        }
        else if (mShapeMode == ShapeMode.RECTANGLE) {
            canvas.drawBitmap(matrixBitmap, matrix, null);
        }

		if (originalBitmap != matrixBitmap && matrixBitmap != perfectBitmap && matrixBitmap != null && !matrixBitmap.isRecycled()) {
			matrixBitmap.recycle();
			matrixBitmap = null;
		}

//        Log.d("OKAY2", "bitmap width : " + perfectBitmap.getWidth());
//        Log.d("OKAY2", "bitmap height : " + perfectBitmap.getHeight());

		if (originalBitmap != perfectBitmap) {
			return saveFile(perfectBitmap, true);
		} else {
			return saveFile(perfectBitmap, false);
		}
	}

	private File getNoCrop() {
		Bitmap originalBitmap = getOriginalBitmap();
		int oriWidth = 0;
		int oriHeight = 0;
		if (originalBitmap != null) {
			oriWidth = originalBitmap.getWidth();
			oriHeight = originalBitmap.getHeight();
        } else {
            // Daniel (2016-09-03 23:49:15): If Original Image is null then just leave it!
            return null;
        }

		if (oriWidth == 0 || oriHeight == 0) {
			oriWidth = mDrawWidth;
			oriHeight = mDrawHeight;
		}

		// Daniel (2016-07-27 18:54:07): check if there are any draw & eraser
		if (arrayDrawInfo != null && arrayDrawInfo.size() > 0) {

			Bitmap matrixBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, oriWidth, oriHeight, getDisplayMatrix(), true);
			Bitmap templateBitmap = Bitmap.createBitmap(mDrawWidth, mDrawHeight, Bitmap.Config.ARGB_8888);
//
			Canvas canvas = new Canvas(templateBitmap);
			canvas.drawBitmap(matrixBitmap, ((canvas.getWidth() - matrixBitmap.getWidth()) / 2), ((canvas.getHeight() - matrixBitmap.getHeight()) / 2), null);

			for (DrawInfo v : arrayDrawInfo) {
				canvas.drawPath(v.getPath(), v.getPaint());
			}

			RectF rectF = getDisplayRect();

			int width = (int) (rectF.right - rectF.left);
			int height = (int) (rectF.bottom - rectF.top);
			// Daniel (2016-06-29 11:58:18): Okay, To prevent IllegalArgumentException y + height must be <= bitmap.height() or x
			if (width > templateBitmap.getWidth())
				width = templateBitmap.getWidth();

			if (height > templateBitmap.getHeight())
				height = templateBitmap.getHeight();

			Bitmap cropImageBitmap = Bitmap.createBitmap(templateBitmap, (int) rectF.left, (int) rectF.top, width, height);

			// Daniel (2016-06-22 14:50:28): recycle previous image
			if (originalBitmap != templateBitmap && templateBitmap != null && templateBitmap != cropImageBitmap && !templateBitmap.isRecycled()) {
				templateBitmap.recycle();
				templateBitmap = null;
			}

			if (originalBitmap != matrixBitmap && matrixBitmap != null && matrixBitmap != cropImageBitmap && !matrixBitmap.isRecycled()) {
				matrixBitmap.recycle();
				matrixBitmap = null;
			}

			if (originalBitmap != cropImageBitmap)
				return saveFile(cropImageBitmap, true);
			else
				return saveFile(cropImageBitmap, false);

		} else {
			// No arrayDraw stuff...
			Matrix mMatrix = new Matrix();
			mMatrix.setRotate(imageDegree, oriWidth * 0.5f, oriHeight * 0.5f);

			Bitmap matrixBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, oriWidth, oriHeight, mMatrix, true);

			if (originalBitmap != matrixBitmap)
				return saveFile(matrixBitmap, true);
			else
				return saveFile(matrixBitmap, false);
		}
	}

	private File getCropElse() {
		Bitmap originalBitmap = getOriginalBitmap();
		int oriWidth = 0;
		int oriHeight = 0;
		if (originalBitmap != null) {
			oriWidth = originalBitmap.getWidth();
			oriHeight = originalBitmap.getHeight();
        } else {
            // Daniel (2016-09-03 23:49:15): If Original Image is null then just leave it!
            return null;
        }

		if (oriWidth == 0 || oriHeight == 0) {
			oriWidth = mDrawWidth;
			oriHeight = mDrawHeight;
		}

		Bitmap matrixBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, oriWidth, oriHeight, getDisplayMatrix(), true);
		Bitmap templateBitmap = Bitmap.createBitmap(mDrawWidth, mDrawHeight, Bitmap.Config.ARGB_8888);
//
		Canvas canvas = new Canvas(templateBitmap);
		canvas.drawBitmap(matrixBitmap, ((canvas.getWidth() - matrixBitmap.getWidth()) / 2), ((canvas.getHeight() - matrixBitmap.getHeight()) / 2), null);

		mRectanglePath.reset();

		mRectanglePath.moveTo(centerPoint.x, centerPoint.y);
		mRectanglePath.lineTo(coordinatePoints[0].x, coordinatePoints[0].y);

		mRectanglePath.lineTo(coordinatePoints[1].x, coordinatePoints[1].y);
		mRectanglePath.lineTo(coordinatePoints[2].x, coordinatePoints[2].y);
		mRectanglePath.lineTo(coordinatePoints[3].x, coordinatePoints[3].y);

		canvas.clipPath(mRectanglePath, Region.Op.DIFFERENCE);
		canvas.drawColor(0xff424242, PorterDuff.Mode.CLEAR);

		if (mCropMode == CropMode.CROP_SHRINK) {
			Bitmap cropImageBitmap = Bitmap.createBitmap(templateBitmap, (int) mCropRect.left, (int) mCropRect.top, (int) (mCropRect.right - mCropRect.left), (int) (mCropRect.bottom - mCropRect.top));

			// Daniel (2016-06-22 14:50:28): recycle previous image
			if (originalBitmap != templateBitmap && templateBitmap != null && templateBitmap != cropImageBitmap && !templateBitmap.isRecycled()) {
				templateBitmap.recycle();
				templateBitmap = null;
			}

			if (originalBitmap != matrixBitmap && matrixBitmap != null && matrixBitmap != cropImageBitmap && !matrixBitmap.isRecycled()) {
				matrixBitmap.recycle();
				matrixBitmap = null;
			}

			if (originalBitmap != cropImageBitmap)
				return saveFile(cropImageBitmap, true);
			else
				return saveFile(cropImageBitmap, false);
		} else {
			if (originalBitmap != matrixBitmap && matrixBitmap != null && matrixBitmap != templateBitmap && !matrixBitmap.isRecycled()) {
				matrixBitmap.recycle();
				matrixBitmap = null;
			}

			if (originalBitmap != templateBitmap)
				return saveFile(templateBitmap, true);
			else
				return saveFile(templateBitmap, false);
		}
	}

    /**
     * it returns crop-stretch thumbnail bitmap <br>
     *     {@link #thumbnailSizeRatio} is default ratio size
     */
    private Bitmap getCropStretchThumbnailBitmap() {
        Bitmap originalBitmap = getOriginalBitmap();
        int oriWidth = 0;
        int oriHeight = 0;
        if (originalBitmap != null) {
            oriWidth = originalBitmap.getWidth();
            oriHeight = originalBitmap.getHeight();
        } else {
            // Daniel (2016-09-03 23:49:15): If Original Image is null then just leave it!
            return null;
        }

        if (oriWidth == 0 || oriHeight == 0) {
            oriWidth = mDrawWidth;
            oriHeight = mDrawHeight;
        }


        RectF displayRect = getDisplayRect();

        float X_Factor = (float) oriWidth / displayRect.width();
        float Y_Factor = (float) oriHeight / displayRect.height();

        // if there is rotation issue than you must recalculate Factor
        if (imageDegree % 360 == 90 || imageDegree % 360 == 270) {
            X_Factor = (float) oriHeight / displayRect.width();
            Y_Factor = (float) oriWidth / displayRect.height();
        }

        Matrix mMatrix = new Matrix();
        mMatrix.setRotate(imageDegree, oriWidth * 0.5f, oriHeight * 0.5f);

        Bitmap matrixBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, oriWidth, oriHeight, mMatrix, true);

        float widthGap = Math.abs(mDrawWidth - displayRect.width());
        float heightGap = Math.abs(mDrawHeight - displayRect.height());

        float removeX = displayRect.left;
        float removeY = displayRect.top;

        if (widthGap > heightGap)
            removeY = 0;
        else
            removeX = 0;

        float[] src = new float[]{
                (centerPoint.x - removeX) * X_Factor, (centerPoint.y - removeY) * Y_Factor,
                (coordinatePoints[0].x - removeX) * X_Factor, (coordinatePoints[0].y - removeY) * Y_Factor,
                (coordinatePoints[1].x - removeX) * X_Factor, (coordinatePoints[1].y - removeY) * Y_Factor,
                (coordinatePoints[2].x - removeX) * X_Factor, (coordinatePoints[2].y - removeY) * Y_Factor
        };

        // Daniel (2016-07-01 18:21:54): Find perfect ratio of IMAGE
        double L1 = Math.sqrt(Math.pow(src[0] - src[2], 2) + Math.pow(src[1] - src[3], 2));
        double L2 = Math.sqrt(Math.pow(src[6] - src[4], 2) + Math.pow(src[7] - src[5], 2));

        double M1 = Math.sqrt(Math.pow(src[0] - src[6], 2) + Math.pow(src[1] - src[7], 2));
        double M2 = Math.sqrt(Math.pow(src[2] - src[4], 2) + Math.pow(src[3] - src[5], 2));

        double h = (M1 + M2) / 2;
        double w = (L1 + L2) / 2;

        double diff = Math.abs(L1 - L2) / 2;

        float X2 = src[0];
        float Y2 = src[1];
        float X1 = src[4];
        float Y1 = src[5];
        float CX = src[6];
        float CY = src[7];

        double leftTop = Math.atan((Y2 - CY) / (X2 - CX));
        double leftBottom = Math.atan((Y1 - CY) / (X1 - CX));

        double radian = leftTop - leftBottom;

//                double angle = Math.abs(radian * 180 / Math.PI);
//                Log.d("OKAY2", "angle : " + angle);

        double factor = Math.abs(90 / (radian * 180 / Math.PI));
        double diffFactor = (1 + diff * 1.5 / w);

//                Log.d("OKAY2", "factor : " + factor);
//                Log.d("OKAY2", "diffFactor : " + diffFactor);
//                Log.d("OKAY2", " f / 2 : " +  ((factor + diffFactor) / 2));

        h = h * ((factor + diffFactor) / 2);

        // Daniel (2016-08-06 18:18:14): If h is bigger than original image, then it should be fixed
        // It might happened to be higher than original picture...
        // Daniel (2016-08-08 17:11:30): consider Image's degree!
        if (imageDegree % 360 == 90 || imageDegree % 360 == 270) {
            double wRatio = oriHeight / w;
            double hRatio = oriWidth / h;

            if (h > oriWidth) {
                w = w * hRatio;
                h = oriWidth;    // h = h * (oriWidth / h);
            } else if (w > oriHeight) {
                w = oriHeight;  // w = w * (oriHeight / w);
                h = h * wRatio;
            }
        } else {
            double wRatio = oriWidth / w;
            double hRatio = oriHeight / h;

            if (h > oriHeight) {
                w = w * hRatio;
                h = oriHeight;    // h = h * (oriHeight / h);
            } else if (w > oriWidth) {
                w = oriWidth;      // w = w * (oriWidth / w);
                h = h * wRatio;
            }
        }

        float[] dsc = new float[]{
                0, 0,
                (float) w, 0,
                (float) w, (float) h,
                0, (float) h
        };

        // Daniel (2016-10-24 17:09:54): w and h should be equal or larger than 10
        if (w < 10) w = 10;
        if (h < 10) h = 10;

        Bitmap perfectBitmap = Bitmap.createBitmap((int) w, (int) h, Bitmap.Config.ARGB_8888);

        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(src, 0, dsc, 0, 4);

        Canvas canvas = new Canvas(perfectBitmap);

        if (mShapeMode == ShapeMode.CIRCLE) {
            final int color = 0xff424242;
            final Paint paint = new Paint();

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(color);
            canvas.drawCircle(perfectBitmap.getWidth() / 2, perfectBitmap.getHeight() / 2,
                    perfectBitmap.getWidth() / 2, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(matrixBitmap, matrix, paint);
        }
        else if (mShapeMode == ShapeMode.RECTANGLE) {
            canvas.drawBitmap(matrixBitmap, matrix, null);
        }

        if (originalBitmap != matrixBitmap && matrixBitmap != perfectBitmap && matrixBitmap != null && !matrixBitmap.isRecycled()) {
            matrixBitmap.recycle();
            matrixBitmap = null;
        }

        try {
            return BitmapUtil.getBitmap(getContext(), perfectBitmap, (int) (perfectBitmap.getWidth() * (thumbnailSizeRatio / 100)), (int) (perfectBitmap.getHeight() * (thumbnailSizeRatio / 100)), true);
        } catch (Exception e) {
            return perfectBitmap;
        }
    }

    @Override
    public File getCropImage() {

        try {
            switch (mCropMode) {
                case CROP_STRETCH:
                    return getCropStretch();
                case NONE:
                    return getNoCrop();
                default:
                    return getCropElse();
            }
        } catch (IllegalArgumentException e) {
            // Daniel (2017-04-18 14:02:34): it happens especially getting file before loading image
            return null;
        }
    }

    @Override
    public Bitmap getCropImageThumbnail() {

        try {
            switch (mCropMode) {
                case CROP_STRETCH:
                    return getCropStretchThumbnailBitmap();
            }
            return null;
        } catch (IllegalArgumentException e) {
            // Daniel (2017-04-18 14:02:34): it happens especially getting file before loading image
            return null;
        }
    }

    @Override
	public void onRecycleBitmap() {
		try {
			getOriginalBitmap().recycle();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	@SuppressLint("WrongThread")
    private File saveFile(Bitmap bitmap, boolean shouldRecycle) {
        OutputStream output = null;

        // Daniel (2016-06-24 11:52:55): if dstFile is invalid, we create our own file and return it to user!
        if (isNewFile || dstFile == null || !dstFile.exists() || !dstFile.isFile()) {
//            final File filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/Bapul/");
            // Daniel (2017-01-20 12:07:43): Use cache directory instead
            final File filePath = getContext().getExternalCacheDir();

            if (filePath != null && !filePath.exists()) {
                filePath.mkdirs();
            }

            // Create a media file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new Date());

            dstFile = new File(filePath, "CropperLibrary_"+timeStamp+"_." + mCropExtension.name());
            try {
                dstFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            output = new FileOutputStream(dstFile);

            // TODO: @namgyu.park (2019-12-18) : You can execute bitmap compress on UI thread. Make sure to fix later.
            if (mCropExtension == CropExtension.png)
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, output);
            else
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output);

            output.flush();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.isNewFile = true;

            if (shouldRecycle && bitmap != null && !bitmap.isRecycled()) {
				bitmap.recycle();
				bitmap = null;
			}
        }

        return dstFile;
    }

    /**
     * among 4 coordinates the minimum X is Left
     * @return
     */
    private int getCropLeft() {
        int left = coordinatePoints[0].x;

        for (Point p : coordinatePoints) {
            if (p.x <= left)
                left = p.x;
        }

        return left;
    }

    /**
     * among 4 coordinates the minimum y is top
     * @return
     */
    private int getCropTop() {
        int top = coordinatePoints[0].y;

        for (Point p : coordinatePoints) {
            if (p.y <= top)
                top = p.y;
        }

        return top;
    }

    /**
     * among 4 coordinates the maximum x is right
     * @return
     */
    private int getCropRight() {
        int top = coordinatePoints[0].x;

        for (Point p : coordinatePoints) {
            if (p.x >= top)
                top = p.x;
        }

        return top;
    }

    /**
     * among 4 coordinates the maximum y is bottom
     * @return
     */
    private int getCropBottom() {
        int bottom = coordinatePoints[0].y;

        for (Point p : coordinatePoints) {
            if (p.y >= bottom)
                bottom = p.y;
        }

        return bottom;
    }

    // Daniel (2016-06-22 15:21:04): set Matrix

    private final Matrix mBaseMatrix = new Matrix();
    private final Matrix mDrawMatrix = new Matrix();
    private final Matrix mSuppMatrix = new Matrix();
    private final RectF mDisplayRect = new RectF();

    private ScaleType mScaleType = ScaleType.FIT_CENTER;

    static final int EDGE_NONE = -1;
    static final int EDGE_LEFT = 0;
    static final int EDGE_RIGHT = 1;
    static final int EDGE_BOTH = 2;

    private int mScrollEdge = EDGE_BOTH;
    private final float[] mMatrixValues = new float[9];

    /**
     * get current displayed Matrix
     * @return
     */
    public Matrix getDisplayMatrix() {
        return new Matrix(getDrawMatrix());
    }

    public boolean setDisplayMatrix(Matrix finalMatrix) {
        if (finalMatrix == null)
            throw new IllegalArgumentException("Matrix cannot be null!");

        if (null == getDrawable())
            return false;

        mSuppMatrix.set(finalMatrix);
        setImageViewMatrix(getDrawMatrix());
        checkMatrixBounds();

        return true;
    }

    public Matrix getDrawMatrix() {
        mDrawMatrix.set(mBaseMatrix);
        mDrawMatrix.postConcat(mSuppMatrix);
        return mDrawMatrix;
    }

    /**
     * calculate
     * @param d
     */
    private void updateBaseMatrix(Drawable d) {

        final float viewWidth = getImageViewWidth();
        final float viewHeight = getImageViewHeight();
        final int drawableWidth = d.getIntrinsicWidth();
        final int drawableHeight = d.getIntrinsicHeight();

        mBaseMatrix.reset();

        final float widthScale = viewWidth / drawableWidth;
        final float heightScale = viewHeight / drawableHeight;

        if (mScaleType == ScaleType.CENTER) {
            mBaseMatrix.postTranslate((viewWidth - drawableWidth) / 2F,
                    (viewHeight - drawableHeight) / 2F);

        } else if (mScaleType == ScaleType.CENTER_CROP) {
            float scale = Math.max(widthScale, heightScale);
            mBaseMatrix.postScale(scale, scale);
            mBaseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2F,
                    (viewHeight - drawableHeight * scale) / 2F);

        } else if (mScaleType == ScaleType.CENTER_INSIDE) {
            float scale = Math.min(1.0f, Math.min(widthScale, heightScale));
            mBaseMatrix.postScale(scale, scale);
            mBaseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2F,
                    (viewHeight - drawableHeight * scale) / 2F);

        } else {
            RectF mTempSrc = new RectF(0, 0, drawableWidth, drawableHeight);
            RectF mTempDst = new RectF(0, 0, viewWidth, viewHeight);

            switch (mScaleType) {
                case FIT_CENTER:
                    mBaseMatrix
                            .setRectToRect(mTempSrc, mTempDst, Matrix.ScaleToFit.CENTER);
                    break;

                case FIT_START:
                    mBaseMatrix.setRectToRect(mTempSrc, mTempDst, Matrix.ScaleToFit.START);
                    break;

                case FIT_END:
                    mBaseMatrix.setRectToRect(mTempSrc, mTempDst, Matrix.ScaleToFit.END);
                    break;

                case FIT_XY:
                    mBaseMatrix.setRectToRect(mTempSrc, mTempDst, Matrix.ScaleToFit.FILL);
                    break;

                default:
                    break;
            }
        }

        resetMatrix();
    }

    /**
     * Resets the Matrix back to FIT_CENTER, and then displays it.s
     */
    private void resetMatrix() {
        mSuppMatrix.reset();
        setImageViewMatrix(getDrawMatrix());
        checkMatrixBounds();
    }

    private void setImageViewMatrix(Matrix matrix) {
        checkImageViewScaleType();
        setImageMatrix(matrix);

//        // Call MatrixChangedListener if needed
//        if (mMatrixChangeListener != null) {
//            RectF displayRect = getDisplayRect(matrix);
//            if (displayRect != null) {
//                mMatrixChangeListener.onMatrixChanged(displayRect);
//            }
//        }
    }

    private void checkImageViewScaleType() {

        /**
         * PhotoView's getScaleType() will just divert to this.getScaleType() so
         * only call if we're not attached to a PhotoView.
         */
        if (!ScaleType.MATRIX.equals(getScaleType())) {
            throw new IllegalStateException(
                    "The ImageView's ScaleType has been changed since attaching a PhotoViewAttacher");
        }
    }

    private boolean checkMatrixBounds() {
        final RectF rect = getDisplayRect(getDrawMatrix());
        if (null == rect)
            return false;

        final float height = rect.height(), width = rect.width();
        float deltaX = 0, deltaY = 0;

        final int viewHeight = getImageViewHeight();
        if (height <= viewHeight) {
            switch (mScaleType) {
                case FIT_START:
                    deltaY = -rect.top;
                    break;
                case FIT_END:
                    deltaY = viewHeight - height - rect.top;
                    break;
                default:
                    deltaY = (viewHeight - height) / 2 - rect.top;
                    break;
            }
        } else if (rect.top > 0) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom;
        }

        final int viewWidth = getImageViewWidth();
        if (width <= viewWidth) {
            switch (mScaleType) {
                case FIT_START:
                    deltaX = -rect.left;
                    break;
                case FIT_END:
                    deltaX = viewWidth - width - rect.left;
                    break;
                default:
                    deltaX = (viewWidth - width) / 2 - rect.left;
                    break;
            }
            mScrollEdge = EDGE_BOTH;
        } else if (rect.left > 0) {
            mScrollEdge = EDGE_LEFT;
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
            mScrollEdge = EDGE_RIGHT;
        } else {
            mScrollEdge = EDGE_NONE;
        }

        // Finally actually translate the matrix
        mSuppMatrix.postTranslate(deltaX, deltaY);
        return true;
    }

    /**
     * Helper method that maps the supplied Matrix to the current Drawable
     * @param matrix
     * @return
     */
    private RectF getDisplayRect(Matrix matrix) {
        Drawable d = getDrawable();

        if (d != null) {
            mDisplayRect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            matrix.mapRect(mDisplayRect);
            return mDisplayRect;
        }

        return null;
    }

    /**
     * get ImageView's width
     * @return
     */
    private int getImageViewWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    /**
     * get ImageView's height
     * @return
     */
    private int getImageViewHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    /**
     * get original bitmap
     * @return
     */
    private Bitmap getOriginalBitmap() {
        if (getDrawable() != null) {
            return ((BitmapDrawable) getDrawable()).getBitmap();
        }
        return null;
    }
    /**
     * get current scale
     * @return
     */
    public float getScale() {
        return (float) Math.sqrt((float) Math.pow(getValue(mSuppMatrix, Matrix.MSCALE_X), 2) + (float) Math.pow(getValue(mSuppMatrix, Matrix.MSKEW_Y), 2));
    }

    /**
     * Helper method that 'unpacks' a Matrix and returns the required value
     *
     * @param matrix     - Matrix to unpack
     * @param whichValue - Which value from Matrix.M* to return
     * @return float - returned value
     */
    private float getValue(Matrix matrix, int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    /**
     * fit image to screen when parameter is true
	 */
    private void resizeImageToFitScreen(){
        try {
			// 3. Adjust Image to fit ImageView
			final float viewWidth = getImageViewWidth();
			final float viewHeight = getImageViewHeight();
			RectF displayRect = getDisplayRect();
			final int drawableWidth = (int) displayRect.width();
			final int drawableHeight = (int) displayRect.height();

//			Log.d("OKAY2", "viewWidth : " + viewWidth);
//			Log.d("OKAY2", "viewHeight : " + viewHeight);
//
//			Log.d("OKAY2", "drawableWidth : " + drawableWidth);
//			Log.d("OKAY2", "drawableHeight : " + drawableHeight);

			final float widthScale = viewWidth / drawableWidth;
			final float heightScale = viewHeight / drawableHeight;

			final float scale = Math.min(widthScale, heightScale);

//					RectF mTempSrc = new RectF(0, 0, drawableWidth, drawableHeight);
//					RectF mTempDst = new RectF(0, 0, viewWidth, viewHeight);

//                        mSuppMatrix.postScale(widthScale, heightScale, displayRect.centerX(), displayRect.centerY());
			mSuppMatrix.postScale(scale, scale, displayRect.centerX(), displayRect.centerY());
			checkAndDisplayMatrix();    // applied

			// Daniel (2016-01-13 19:51:08): to prevent from downscaling image below Screen size.
			float currentScale = getScale();
			setScaleLevels(currentScale, currentScale * 2, currentScale * 3);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * get current displayed image RectF
     * @return
     */
    public RectF getDisplayRect() {
        checkMatrixBounds();
        return getDisplayRect(getDrawMatrix());
    }

    /**
     * Helper method that simply checks the Matrix, and then displays the result
     */
    private void checkAndDisplayMatrix() {
        if (checkMatrixBounds()) {
            setImageViewMatrix(getDrawMatrix());
        }
    }

    public void setScaleLevels(float minimumScale, float mediumScale, float maximumScale) {
        try {
            if(checkZoomLevels(minimumScale, mediumScale, maximumScale)) {
                mMinScale = minimumScale;
                mMidScale = mediumScale;
                mMaxScale = maximumScale;
            }
        }catch (IllegalArgumentException e) {}
    }

    private float mMinScale = 1.0f;
    private float mMidScale = 3.0f;
    private float mMaxScale = 6.0f;

    private static boolean checkZoomLevels(float minZoom, float midZoom,
                                           float maxZoom) throws IllegalArgumentException {
        if (minZoom >= midZoom) {
            throw new IllegalArgumentException(
                    "MinZoom has to be less than MidZoom");
        } else if (midZoom >= maxZoom) {
            throw new IllegalArgumentException(
                    "MidZoom has to be less than MaxZoom");
        }else{
            return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        onUndoRedoStateChangeListener = null;
        onThumbnailChangeListener = null;
        super.onDetachedFromWindow();
    }
}