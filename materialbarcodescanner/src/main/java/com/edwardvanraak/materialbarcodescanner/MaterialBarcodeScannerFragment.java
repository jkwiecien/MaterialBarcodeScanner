package com.edwardvanraak.materialbarcodescanner;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;

import static junit.framework.Assert.assertNotNull;

/**
 * Created by Jacek Kwiecień on 10.05.2017.
 */

public class MaterialBarcodeScannerFragment extends Fragment {

    private static final int RC_HANDLE_GMS = 9001;

    private static final String TAG = "MaterialBarcodeScanner";

    private MaterialBarcodeScanner mMaterialBarcodeScanner;
    private MaterialBarcodeScannerBuilder mMaterialBarcodeScannerBuilder;

    private BarcodeDetector barcodeDetector;

    private CameraSourcePreview mCameraSourcePreview;

    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    private SoundPoolPlayer mSoundPoolPlayer;

    /**
     * true if no further barcode should be detected or given as a result
     */
    private boolean mDetectionConsumed = false;

    private View findViewById(int resId) {
        return getActivity().findViewById(resId);
    }

    private void runOnUiThread(Runnable runnable) {
        getActivity().runOnUiThread(runnable);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.barcode_capture, container, false);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onMaterialBarcodeScanner(MaterialBarcodeScanner materialBarcodeScanner) {
        this.mMaterialBarcodeScanner = materialBarcodeScanner;
        mMaterialBarcodeScannerBuilder = mMaterialBarcodeScanner.getMaterialBarcodeScannerBuilder();
        barcodeDetector = mMaterialBarcodeScanner.getMaterialBarcodeScannerBuilder().getBarcodeDetector();
        startCameraSource();
        setupLayout();
    }

    private void setupLayout() {
        final TextView topTextView = (TextView) findViewById(R.id.topText);
        assertNotNull(topTextView);
        String topText = mMaterialBarcodeScannerBuilder.getText();
        if (!mMaterialBarcodeScannerBuilder.getText().equals("")) {
            topTextView.setText(topText);
        }
        setupCenterTracker();
    }

    private void setupCenterTracker() {
        if (mMaterialBarcodeScannerBuilder.getScannerMode() == MaterialBarcodeScanner.SCANNER_MODE_CENTER) {
            final ImageView centerTracker = (ImageView) findViewById(R.id.barcode_square);
            centerTracker.setImageResource(mMaterialBarcodeScannerBuilder.getTrackerResourceID());
            mGraphicOverlay.setVisibility(View.INVISIBLE);
        }
    }

    private void updateCenterTrackerForDetectedState() {
        if (mMaterialBarcodeScannerBuilder.getScannerMode() == MaterialBarcodeScanner.SCANNER_MODE_CENTER) {
            final ImageView centerTracker = (ImageView) findViewById(R.id.barcode_square);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    centerTracker.setImageResource(mMaterialBarcodeScannerBuilder.getTrackerDetectedResourceID());
                }
            });
        }
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        mSoundPoolPlayer = new SoundPoolPlayer(getActivity());
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getActivity());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), code, RC_HANDLE_GMS);
            dialog.show();
        }
        mGraphicOverlay = (GraphicOverlay<BarcodeGraphic>) findViewById(R.id.graphicOverlay);
        BarcodeGraphicTracker.NewDetectionListener listener = new BarcodeGraphicTracker.NewDetectionListener() {
            @Override
            public void onNewDetection(Barcode barcode) {
                if (!mDetectionConsumed) {
                    mDetectionConsumed = true;
                    Log.d(TAG, "Barcode detected! - " + barcode.displayValue);
                    EventBus.getDefault().postSticky(barcode);
                    updateCenterTrackerForDetectedState();
                    if (mMaterialBarcodeScannerBuilder.isBleepEnabled()) {
                        mSoundPoolPlayer.playShortResource(R.raw.bleep);
                    }
                }
            }
        };
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, listener, mMaterialBarcodeScannerBuilder.getTrackerColor());
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());
        CameraSource mCameraSource = mMaterialBarcodeScannerBuilder.getCameraSource();
        if (mCameraSource != null) {
            try {
                mCameraSourcePreview = (CameraSourcePreview) findViewById(R.id.preview);
                mCameraSourcePreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
        if (mCameraSourcePreview != null) mCameraSourcePreview.stop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() != null && getActivity().isFinishing()) clean();
    }

    private void clean() {
        EventBus.getDefault().removeStickyEvent(MaterialBarcodeScanner.class);
        if (mCameraSourcePreview != null) {
            mCameraSourcePreview.release();
            mCameraSourcePreview = null;
        }
        if (mSoundPoolPlayer != null) {
            mSoundPoolPlayer.release();
            mSoundPoolPlayer = null;
        }
    }
}


