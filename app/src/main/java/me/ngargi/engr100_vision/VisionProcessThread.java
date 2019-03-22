package me.ngargi.engr100_vision;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;

import static aruco.min3d.Min3d.TAG;

public class VisionProcessThread implements Runnable {
    public VisionProcessThread(CameraParameters cp) {
        cameraParameters = cp;
        input = new Mat();
        output = new Mat();
        mDetector = new MarkerDetector();
//        mDetector.setThresholdParams(9,9);
//        mDetector.setThresholdMethod(MarkerDetector.thresSuppMethod.CANNY);
    }

    private final Lock inmatlock = new ReentrantLock(true);
    private CameraParameters cameraParameters;
    public Mat input;
    public Mat output;
    private Lock outmatlock = new ReentrantLock(true);
    public boolean exit = false;
    private MarkerDetector mDetector;


    public void setInput(Mat frame) {
        inmatlock.lock();
        frame.copyTo(input);
        inmatlock.unlock();
    }

    public void getOutput(Mat frame) {
        if (output.empty()) {
            inmatlock.lock();
            input.copyTo(frame);
            inmatlock.unlock();
            return;
        }
        outmatlock.lock();
        output.copyTo(frame);
        outmatlock.unlock();
    }


    @Override
    public void run() {
        while (!exit) {
            inmatlock.lock();
            Mat mRgba = new Mat();
            input.copyTo(mRgba);
            inmatlock.unlock();
            if (mRgba.empty()) {
                continue;
            }

            if (cameraParameters.isValid()) {
                long dt = System.currentTimeMillis();

                Vector<Marker> detectedMarkers = new Vector<>();
                mDetector.detect(mRgba, detectedMarkers, cameraParameters, 0.1f);
                Log.e(TAG, "Detection Time: " + (System.currentTimeMillis() - dt));
                Log.e(TAG, mRgba.width() + " by " + mRgba.height() );


                for (Marker m : detectedMarkers) {
                    m.draw(mRgba, new Scalar(255, 0, 0), 2, true);
                    m.draw3dAxis(mRgba, cameraParameters, new Scalar(0, 255, 0));
                    m.draw3dCube(mRgba, cameraParameters, new Scalar(0, 255, 0));
                }

                Log.e(TAG, "Marker count: " + detectedMarkers.size());
            } else {
                Imgproc.putText(mRgba, "Invalid Camera Parameters", new Point(mRgba.width()/4, mRgba.height()/2), Core.FONT_HERSHEY_SIMPLEX, 2, new Scalar(0, 255, 0));
            }
//            Imgproc.putText(mRgba, ""+FPS, new Point(10, 10), Core.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 0));
            outmatlock.lock();
            mRgba.copyTo(output);
            outmatlock.unlock();
        }


    }
}
