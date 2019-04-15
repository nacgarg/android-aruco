package me.ngargi.engr100_vision;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.SurfaceView;
import android.widget.TextView;


import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import CameraCalibration.CameraCalibrationActivity;
import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;

import static aruco.min3d.Min3d.TAG;
//import static com.felhr.usbserial.SerialPortBuilder.ACTION_USB_PERMISSION;


public class MainActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "MainActivity";
private String ACTION_USB_PERMISSION = "me.ngargi.engr100_vision.USB_PERMISSION";

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private Scalar               DRAW_COLOR;
    private CameraParameters cameraParameters;
    private VisionProcessThread visionProcess;
    private Thread processThread;
    private TextView text;

    private UsbSerialDevice serialPort;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                data.concat("/n");
                final String d = data;
//                runOnUiThread(() -> text.append(d));


            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    public static final int SERVERPORT = 6000;


    private CameraBridgeViewBase mOpenCvCameraView;

    private ServerSocket serverSocket;

    Handler updateConversationHandler;

    Thread serverThread = null;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.enableFpsMeter();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        updateConversationHandler = new Handler();
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        text = findViewById(R.id.textView);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setMaxFrameSize(1280, 960);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

//        this.serverThread = new Thread(new ServerThread());
//        this.serverThread.start();

        serialPort = null;
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            cameraParameters = new CameraParameters();
        cameraParameters.readFromFile(getApplicationContext().getFilesDir() + CameraCalibrationActivity.DATA_FILEPATH);
            if (!cameraParameters.isValid()) {
                calibrate(findViewById(R.id.content));
            }
            if (processThread != null) {
                processThread.interrupt();
            }
            visionProcess = new VisionProcessThread(cameraParameters);
            processThread = new Thread(visionProcess);
            processThread.start();
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        DRAW_COLOR = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
    return true;
    }

    public void calibrate(View view) {
        Intent i = new Intent(getApplicationContext(), CameraCalibrationActivity.class);
        startActivity(i);
    }

    public void checkUSB(View view) {
        // todo
        HashMap usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Object entry : usbDevices.keySet()) {
                device  = (UsbDevice) usbDevices.get(entry);
                int deviceVID = device.getVendorId();
                text.append(device.getDeviceName() + " - " +Integer.toString(deviceVID) + "\n\n");
//                showDialog(MainActivity.this, "title", Integer.toString(deviceVID), "OK", "Cancel");
                if (deviceVID == 0x1FFB)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                            new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted =
                        intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
//                            setUiEnabled(true); //Enable Buttons in UI
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback); //
                            text.append("Serial connection established!");
//                            runOnUiThread(new updateUIThread("Serial connection established!"));
//                            tvAppend(textView,"Serial Connection Opened!\n");

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                            text.append("port not open");

                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                        text.append("port is null");

                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                    text.append("no perm");

                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                checkUSB(findViewById(R.id.usb_button));
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
//                onClickStop(stopButton);
            }
        };
    };

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        long t = System.currentTimeMillis();
        mRgba = inputFrame.rgba();
        visionProcess.setInput(mRgba);
        visionProcess.getOutput(mRgba);
//        runOnUiThread();
        Thread a = new Thread(new sendSerialThread(getBaseContext(), visionProcess.getSerialBytes(), serialPort));
        a.start();
        runOnUiThread(new updateUIThread(visionProcess.getPositionStr()));
        return mRgba;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    class ServerThread implements Runnable {

        public void run() {
            Socket socket = null;
            try {
                serverSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {

                try {

                    socket = serverSocket.accept();

                    CommunicationThread commThread = new CommunicationThread(socket);
                    new Thread(commThread).start();

                } catch (IOException e) {
                   // e.printStackTrace();
                }
            }
        }
    }

    class CommunicationThread implements Runnable {

        private Socket clientSocket;

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {

            this.clientSocket = clientSocket;

            try {

                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
            }
            try {

                String read = input.readLine();
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                out.append(visionProcess.getPositionStr());
                out.flush();
                out.close();
//                    updateConversationHandler.post(new updateUIThread(read));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {

//            while (!Thread.currentThread().isInterrupted()) {
//                if (clientSocket.isClosed()) {
//                    continue;
//                }
//
//
//            }
        }

    }

    class sendSerialThread implements Runnable {
        private UsbSerialDevice serial;
        private VisionProcessThread visionProcess;
        private byte[] bytes;
        public sendSerialThread(Context ctx, byte[] toSend, UsbSerialDevice s) {
            this.serial = s;
            this.bytes = toSend;

        }

        @Override
        public void run() {
            if (serial == null) {
                return;
            }
            serial.write(bytes);
        }
    }

    class updateUIThread implements Runnable {
        private String msg;

        public updateUIThread(String str) {
            this.msg = str;
        }

        @Override
        public void run() {
//            msg += "\n Local IP: " + getIpAddress(getApplicationContext());
//            text.setText(msg);
        }
    }

    public static String getIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(WIFI_SERVICE);

        String ipAddress = intToInetAddress(wifiManager.getDhcpInfo().ipAddress).toString();

        ipAddress = ipAddress.substring(1);

        return ipAddress;
    }

    public static InetAddress intToInetAddress(int hostAddress) throws AssertionError{
        byte[] addressBytes = { (byte)(0xff & hostAddress),
                (byte)(0xff & (hostAddress >> 8)),
                (byte)(0xff & (hostAddress >> 16)),
                (byte)(0xff & (hostAddress >> 24)) };

        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }
}
