package com.example.learncamera2opengl.camera;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.util.Size;

import java.nio.ByteBuffer;

public class CameraUtil {
    private static final String TAG = "Abbott CameraUtil";
    public static Size getFitInScreenSize(int previewWidth, int previewHeight, int screenWidth,
                                          int screenHeight) {
        Point res = new Point(0, 0);
        float ratioPreview = previewWidth *1f / previewHeight;
        float ratioScreen = 0.0f;

        //landscape
        if (screenWidth > screenHeight) {
            ratioScreen = screenWidth * 1f / screenHeight;
            if (ratioPreview >= ratioScreen) {
                res.x = screenWidth;
                res.y = (int)(res.x * previewHeight * 1f / previewWidth);
            }else {
                res.y = screenHeight;
                res.x = (int)(res.y * previewWidth * 1f / previewHeight);
            }
        //portrait
        }else {
            ratioScreen = screenHeight * 1f / screenWidth;
            if (ratioPreview >= ratioScreen) {
                res.y = screenHeight;
                res.x = (int)(res.y * previewHeight * 1f / previewWidth);
            }else {
                res.x = screenWidth;
                res.y = (int)(res.x * previewWidth * 1f / previewHeight);
            }
        }
        return new Size(res.x, res.y);
    }

    static public byte[] GetNV21DataFormImage(Image image) {
        byte[] mNv21Data = null, uPlane = null, vPlane = null;
        int height = image.getHeight();
        int width = image.getWidth();
        if (height % 2 != 0 || width % 2 != 0) {
            Log.d(TAG, "height or width is not seven number");
            return null;
        }
//        Log.d(TAG, "Y  Size " + image.getPlanes()[0].getBuffer().remaining());
//        Log.d(TAG, "U Size " + image.getPlanes()[1].getBuffer().remaining());
//        Log.d(TAG, "V  Size " + image.getPlanes()[2].getBuffer().remaining());
        int size = height * width;
        mNv21Data = new byte[size * 3 / 2];
        image.getPlanes()[0].getBuffer().get(mNv21Data, 0, size);

        int offset = 0;
        int rstride = image.getPlanes()[0].getRowStride();
        int pstride = image.getPlanes()[0].getPixelStride();
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        for (int i = 0; i < height; ++i) {
            byteBuffer.position(offset);
            byteBuffer.get(mNv21Data, i * width, width);
            offset += rstride;
        }

        byte[] Udata = new byte[image.getPlanes()[1].getBuffer().remaining()];
        byte[] Vdata = new byte[image.getPlanes()[2].getBuffer().remaining()];
        image.getPlanes()[1].getBuffer().get(Udata, 0, Udata.length);
        image.getPlanes()[2].getBuffer().get(Vdata, 0, Vdata.length);

        Rect rect = image.getCropRect();
//        Log.d(TAG, "rect h " + rect.height() + " w " + rect.width() + " l " + rect.left + " r " + rect.right + " t " + rect.top + " b " + rect.bottom);
//        Log.d(TAG, "height " + height + " width " + width);
//        Log.d(TAG, "Y " + image.getPlanes()[0].getRowStride() + " " + image.getPlanes()[0].getPixelStride());
//        Log.d(TAG, "STRIDE " + rstride + " " + pstride + " " + image.getPlanes()[2].getRowStride() + " " + image.getPlanes()[2].getPixelStride());
        offset = size;
        int offsetr = 0, offsetp = 0;
        rstride = image.getPlanes()[1].getRowStride();
        pstride = image.getPlanes()[1].getPixelStride();

        int half_h = height / 2;
        int half_w = width / 2;

        for (int i = 0; i < half_h; ++i) {
            for (int j = 0; j < half_w; ++j) {
                mNv21Data[offset++] = Vdata[offsetr + offsetp];
                mNv21Data[offset++] = Udata[offsetr + offsetp];
                offsetp += pstride;
            }
            offsetr += rstride;
            offsetp = 0;
        }
//        Log.d(TAG, "mNv21Data size " + mNv21Data.length);
        return mNv21Data;
    }


    public static byte[] YUV_420_888_data(Image image) {
        final int imageWidth = image.getWidth();
        final int imageHeight = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[imageWidth * imageHeight *
                ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        int offset = 0;

        for (int plane = 0; plane < planes.length; ++plane) {
            final ByteBuffer buffer = planes[plane].getBuffer();
            final int rowStride = planes[plane].getRowStride();
            // Experimentally, U and V planes have |pixelStride| = 2, which
            // essentially means they are packed.
            final int pixelStride = planes[plane].getPixelStride();
            final int planeWidth = (plane == 0) ? imageWidth : imageWidth / 2;
            final int planeHeight = (plane == 0) ? imageHeight : imageHeight / 2;
            if (pixelStride == 1 && rowStride == planeWidth) {
                // Copy whole plane from buffer into |data| at once.
                buffer.get(data, offset, planeWidth * planeHeight);
                offset += planeWidth * planeHeight;
            } else {
                // Copy pixels one by one respecting pixelStride and rowStride.
                byte[] rowData = new byte[rowStride];
                for (int row = 0; row < planeHeight - 1; ++row) {
                    buffer.get(rowData, 0, rowStride);
                    for (int col = 0; col < planeWidth; ++col) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
                // Last row is special in some devices and may not contain the full
                // |rowStride| bytes of data.
                // See http://developer.android.com/reference/android/media/Image.Plane.html#getBuffer()
                buffer.get(rowData, 0, Math.min(rowStride, buffer.remaining()));
                for (int col = 0; col < planeWidth; ++col) {
                    data[offset++] = rowData[col * pixelStride];
                }
            }
        }

        return data;
    }
}
