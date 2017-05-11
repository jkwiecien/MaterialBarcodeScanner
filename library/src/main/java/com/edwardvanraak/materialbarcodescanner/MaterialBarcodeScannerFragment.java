package com.edwardvanraak.materialbarcodescanner;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

import static junit.framework.Assert.assertNotNull;

/**
 * Created by Jacek Kwiecień on 10.05.2017.
 */

public class MaterialBarcodeScannerFragment extends Fragment {

    public static final int SCANNER_MODE_FREE = 1;
    public static final int SCANNER_MODE_CENTER = 2;

    protected CameraSource cameraSource;

    protected boolean used = false; //used to check if a builder is only used

    protected int facing = CameraSource.CAMERA_FACING_BACK;
    protected boolean autoFocusEnabled = false;

    protected OnResultListener onResultListener;

    protected int trackerColor = Color.parseColor("#F44336"); //Material Red 500

    protected boolean mBleepEnabled = false;

    protected int barcodeFormats = Barcode.ALL_FORMATS;

    protected String text = "";

    protected int scannerMode = SCANNER_MODE_FREE;

    protected int trackerResourceID = R.drawable.material_barcode_square_512;
    protected int trackerDetectedResourceID = R.drawable.material_barcode_square_512_green;


    private static final int RC_HANDLE_GMS = 9001;

    private static final String TAG = "MaterialBarcodeScanner";

    private BarcodeDetector barcodeDetector;

    private SoundPoolPlayer soundPoolPlayer;

    private TextView topTextView;
    private ImageView centerTracker;
    private GraphicOverlay<BarcodeGraphic> graphicOverlay;
    private CameraSourcePreview cameraSourcePreview;

    /**
     * true if no further barcode should be detected or given as a result
     */
    private boolean mDetectionConsumed = false;

    private void runOnUiThread(Runnable runnable) {
        getActivity().runOnUiThread(runnable);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);

        topTextView = (TextView) view.findViewById(R.id.topText);
        centerTracker = (ImageView) view.findViewById(R.id.barcode_square);
        graphicOverlay = (GraphicOverlay<BarcodeGraphic>) view.findViewById(R.id.graphicOverlay);
        cameraSourcePreview = (CameraSourcePreview) view.findViewById(R.id.preview);

        return view;
    }

    private void setupLayout() {
        assertNotNull(topTextView);
        if (!TextUtils.isEmpty(text)) topTextView.setText(text);
        setupCenterTracker();
    }

    private void setupCenterTracker() {
        if (scannerMode == SCANNER_MODE_CENTER) {
            centerTracker.setImageResource(trackerResourceID);
            graphicOverlay.setVisibility(View.INVISIBLE);
        }
    }

    private void updateCenterTrackerForDetectedState() {
        if (scannerMode == SCANNER_MODE_CENTER) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    centerTracker.setImageResource(trackerDetectedResourceID);
                }
            });
        }
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void initCameraSource() throws SecurityException {
        // check that the device has play services available.
        soundPoolPlayer = new SoundPoolPlayer(getActivity());
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getActivity());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), code, RC_HANDLE_GMS);
            dialog.show();
        }
        BarcodeGraphicTracker.NewDetectionListener listener = new BarcodeGraphicTracker.NewDetectionListener() {
            @Override
            public void onNewDetection(Barcode barcode) {
                if (!mDetectionConsumed) {
                    mDetectionConsumed = true;
                    Log.d(TAG, "Barcode detected! - " + barcode.displayValue);
                    updateCenterTrackerForDetectedState();
                    if (mBleepEnabled) soundPoolPlayer.playShortResource(R.raw.bleep);
                    onResultListener.onResult(barcode);
                }
            }
        };
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(graphicOverlay, listener, trackerColor);
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());
        startCameraPreview();
    }

    private void startCameraPreview() {
        try {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (cameraSource != null) cameraSourcePreview.start(cameraSource, graphicOverlay);
        } catch (IOException e) {
            Log.e(TAG, "Unable to start camera source.", e);
            cameraSource.release();
            cameraSource = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cameraSourcePreview != null) cameraSourcePreview.stop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clean();
    }

    private void clean() {
        if (cameraSourcePreview != null) {
            cameraSourcePreview.release();
            cameraSourcePreview = null;
        }

        if (soundPoolPlayer != null) {
            soundPoolPlayer.release();
            soundPoolPlayer = null;
        }

        if (barcodeDetector != null) {
            barcodeDetector.release();
            barcodeDetector = null;
        }

        if (cameraSource != null) {
            cameraSource = null;
        }
    }

    /**
     * Called immediately after a barcode was scanned
     *
     * @param onResultListener
     */
    public void setResultListener(@NonNull OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
    }

    /**
     * Makes the barcode scanner use the camera facing back
     */
    public void setBackfacingCamera() {
        facing = CameraSource.CAMERA_FACING_BACK;
    }

    /**
     * Makes the barcode scanner use camera facing front
     */
    public void setFrontfacingCamera() {
        facing = CameraSource.CAMERA_FACING_FRONT;
    }

    /**
     * Either CameraSource.CAMERA_FACING_FRONT or CameraSource.CAMERA_FACING_BACK
     *
     * @param cameraFacing
     */
    public void setCameraFacing(int cameraFacing) {
        facing = cameraFacing;
    }

    /**
     * Enables or disables auto focusing on the camera
     */
    public void setEnableAutoFocus(boolean enabled) {
        autoFocusEnabled = enabled;
    }

    /**
     * Sets the tracker color used by the barcode scanner, By default this is Material Red 500 (#F44336).
     *
     * @param color
     */
    public void setTrackerColor(int color) {
        trackerColor = color;
    }

    /**
     * Enables or disables a bleep sound whenever a barcode is scanned
     */
    public void setBleepEnabled(boolean enabled) {
        mBleepEnabled = enabled;
    }

    /**
     * Shows a text message at the top of the barcode scanner
     */
    public void setTopText(String text) {
        this.text = text;
    }

    /**
     * Bit mask (containing values like QR_CODE and so on) that selects which formats this barcode detector should recognize.
     *
     * @param barcodeFormats
     * @return
     */
    public void setBarcodeFormats(int barcodeFormats) {
        this.barcodeFormats = barcodeFormats;
    }

    /**
     * Enables exclusive scanning on EAN-13, EAN-8, UPC-A, UPC-E, Code-39, Code-93, Code-128, ITF and Codabar barcodes.
     *
     * @return
     */
    public void setOnly2DScanning() {
        barcodeFormats = Barcode.EAN_13 | Barcode.EAN_8 | Barcode.UPC_A | Barcode.UPC_A | Barcode.UPC_E | Barcode.CODE_39 | Barcode.CODE_93 | Barcode.CODE_128 | Barcode.ITF | Barcode.CODABAR;
    }

    /**
     * Enables exclusive scanning on QR Code, Data Matrix, PDF-417 and Aztec barcodes.
     *
     * @return
     */
    public void setOnly3DScanning() {
        barcodeFormats = Barcode.QR_CODE | Barcode.DATA_MATRIX | Barcode.PDF417 | Barcode.AZTEC;
    }

    /**
     * Enables exclusive scanning on QR Codes, no other barcodes will be detected
     *
     * @return
     */
    public void setOnlyQRCodeScanning() {
        barcodeFormats = Barcode.QR_CODE;
    }

    /**
     * Enables the default center tracker. This tracker is always visible and turns green when a barcode is found.\n
     * Please note that you can still scan a barcode outside the center tracker! This is purely a visual change.
     *
     * @return
     */
    public void setCenterTracker() {
        scannerMode = SCANNER_MODE_CENTER;
    }

    /**
     * Enables the center tracker with a custom drawable resource. This tracker is always visible.\n
     * Please note that you can still scan a barcode outside the center tracker! This is purely a visual change.
     *
     * @param trackerResourceId         a drawable resource id
     * @param detectedTrackerResourceId a drawable resource id for the detected tracker state
     * @return
     */
    public void setCenterTracker(int trackerResourceId, int detectedTrackerResourceId) {
        scannerMode = SCANNER_MODE_CENTER;
        trackerResourceID = trackerResourceId;
        trackerDetectedResourceID = detectedTrackerResourceId;
    }

    /**
     * Build a barcode scanner using the Mobile Vision Barcode API
     */
    private void buildMobileVisionBarcodeDetector() {
        String focusMode = autoFocusEnabled ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : Camera.Parameters.FOCUS_MODE_FIXED;
        barcodeDetector = new BarcodeDetector.Builder(getActivity())
                .setBarcodeFormats(barcodeFormats)
                .build();
        cameraSource = new CameraSource.Builder(getActivity(), barcodeDetector)
                .setFacing(facing)
                .setFlashMode(null)
                .setFocusMode(focusMode)
                .build();
    }

    public void initScanning() {
        buildMobileVisionBarcodeDetector();
        initCameraSource();
        setupLayout();
    }

    public void stopCamera() {
        cameraSourcePreview.stop();
    }

    public void resumeCamera() {
        startCameraPreview();
    }

    public void enableTorch() throws SecurityException {
        cameraSource.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        try {
            cameraSource.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disableTorch() throws SecurityException {
        cameraSource.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        try {
            cameraSource.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}


