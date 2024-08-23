package com.example.bluecon;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.ImageView;
import android.hardware.camera2.CaptureRequest;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bluecon.ml.SsdMobilenetV11Metadata1;

import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private List<String> labels;
    private List<Integer> colors = List.of(
            Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
            Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED);
    private Paint paint = new Paint();
    private ImageProcessor imageProcessor;
    private Bitmap bitmap;
    private ImageView imageView;
    private CameraDevice cameraDevice;
    private Handler handler;
    private CameraManager cameraManager;
    private TextureView textureView;
    private TextView textView;
    private SsdMobilenetV11Metadata1 model;

    String address = null;
    private ProgressDialog progress;

    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getPermission();

        address = DataHolder.getBluetoothAddress();

        textView = findViewById(R.id.textView);

        new MainActivity.ConnectBT().execute();

        try {
            labels = FileUtil.loadLabels(this, "labels.txt");
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the IOException here, such as showing an error message to the user.
        }
        imageProcessor = new ImageProcessor.Builder().add(new ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build();

        try {
            model = SsdMobilenetV11Metadata1.newInstance(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HandlerThread handlerThread = new HandlerThread("videoThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        imageView = findViewById(R.id.imageView);
        final int[] unknownCounter = {0};

        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            private boolean isUpdating = false;

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                if (!isUpdating) {
                    isUpdating = true;

                    bitmap = textureView.getBitmap();
                    TensorImage image = TensorImage.fromBitmap(bitmap);
                    image = imageProcessor.process(image);

                    SsdMobilenetV11Metadata1.Outputs outputs = model.process(image);
                    float[] locations = outputs.getLocationsAsTensorBuffer().getFloatArray();
                    float[] classes = outputs.getClassesAsTensorBuffer().getFloatArray();
                    float[] scores = outputs.getScoresAsTensorBuffer().getFloatArray();

                    Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(mutable);

                    int h = mutable.getHeight();
                    int w = mutable.getWidth();
                    paint.setTextSize(h / 15f);
                    paint.setStrokeWidth(h / 85f);
                    int x;

                    String laptopPosition = "Unknown";







                    for (int i = 0; i < scores.length; i++) {
                        x = i * 4;
                        if (scores[i] > 0.5 && labels.get((int) classes[i]).equals("laptop")) {
                            paint.setColor(colors.get(i));
                            paint.setStyle(Paint.Style.STROKE);
                            canvas.drawRect(new RectF(
                                    locations[x+1]*w, locations[x]*h,
                                    locations[x+3]*w, locations[x+2]*h), paint);
                            paint.setStyle(Paint.Style.FILL);
                            canvas.drawText(labels.get((int)classes[i]) + " " + scores[i], locations[x+1]*w, locations[x]*h, paint);

                            float objectCenterX = (locations[x + 1] + locations[x + 3]) * w / 2; // Center X of the bounding box
                            float centerX = w / 2; // Center X of the screen

                            if (objectCenterX < centerX - w / 5) {
                                laptopPosition = "Left";
                                unknownCounter[0] = 0;
                            } else if (objectCenterX > centerX + w / 5) {
                                laptopPosition = "Right";
                                unknownCounter[0] = 0;
                            } else {
                                laptopPosition = "Center";
                                unknownCounter[0] = 0;
                            }

                            // Break the loop after finding the laptop
                            break;
                        }
                    }

                    if (laptopPosition.equals("Unknown")) {
                        unknownCounter[0]++;
                        Log.d("UnknownCounter", "Value: " + unknownCounter);
                        if(unknownCounter[0]>=5){

                            sendSignal(String.valueOf(4));
                            unknownCounter[0] = 0;
                        }
                    }




                    imageView.setImageBitmap(mutable);

                    // Update the TextView with the laptop position on the main thread
                    final String position = laptopPosition;  // Declare a final variable
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText("Position: " + position);  // Use the final variable here
                            Log.d("TextViewUpdate", "TextView updated with position: " + position);

                            // Add logic to send signals based on the position
                            int signal;
                            switch (position) {
                                case "Center":
                                    signal = 7;
                                    break;
                                case "Left":
                                    signal = 3;
                                    break;
                                case "Right":
                                    signal = 2;
                                    break;
                                case "Unknown":
                                    signal = 2;
                                    break;
                                default:
                                    signal = 5;
                                    break;
                            }

                            // Send the signal
                            sendSignal(String.valueOf(signal));


                        }
                    });

                    // Reset the flag and schedule the next update after 5 seconds
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            isUpdating = false;
                        }
                    }, 1000); // 5000 milliseconds = 5 seconds
                }
            }






        });
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    }

    private void sendSignal ( String number ) {
        if ( btSocket != null ) {
            try {
                btSocket.getOutputStream().write(number.toString().getBytes());
            } catch (IOException e) {
                msg("Error");
            }
        }
    }
    private void Disconnect () {
        if ( btSocket!=null ) {
            try {
                btSocket.close();
            } catch(IOException e) {
                msg("Error");
            }
        }

        finish();
    }
    private void msg (String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected  void onPreExecute () {
            progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please Wait!!!");
        }

        @Override
        protected Void doInBackground (Void... devices) {
            try {
                if ( btSocket==null || !isBtConnected ) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();
                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }

            return null;
        }

        @Override
        protected void onPostExecute (Void result) {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            } else {
                msg("Connected");
                isBtConnected = true;
            }

            progress.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        model.close();
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            // Find the ID of the wide-angle camera (typically rear-facing)
            String[] cameraIdList = cameraManager.getCameraIdList();
            String wideAngleCameraId = null;
            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    wideAngleCameraId = cameraId;
                    break;
                }
            }

            if (wideAngleCameraId != null) {
                cameraManager.openCamera(wideAngleCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice cameraDevice) {
                        MainActivity.this.cameraDevice = cameraDevice;

                        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                        Surface surface = new Surface(surfaceTexture);

                        try {
                            android.hardware.camera2.CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);

                            captureRequestBuilder.addTarget(surface);

                            cameraDevice.createCaptureSession(List.of(surface), new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                    try {
                                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {}
                            }, handler);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onDisconnected(CameraDevice cameraDevice) {}

                    @Override
                    public void onError(CameraDevice cameraDevice, int i) {}
                }, handler);
            } else {
                Toast.makeText(MainActivity.this, "No wide-angle camera found", Toast.LENGTH_SHORT).show();

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void getPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission();
        }
    }
}
