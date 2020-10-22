package net.techbrewery.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.edwardvanraak.materialbarcodescanner.MaterialBarcodeScannerFragment

class MainActivity : AppCompatActivity() {

    val LOG_TAG = "DEBUG_MBS"
    val PERMISSION_REQ_CODE = 11

    private var scannerFragment: MaterialBarcodeScannerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (isPermissionGranted()) initScannerView()
        else askForPermission()
    }

    private fun isPermissionGranted(): Boolean {
        return PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED
    }

    private fun askForPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQ_CODE)
    }

    private fun initScannerView() {
        scannerFragment = supportFragmentManager.findFragmentById(R.id.scanner) as MaterialBarcodeScannerFragment
        scannerFragment?.setOnly2DScanning()
        scannerFragment?.setEnableAutoFocus(true)
        scannerFragment?.setBleepEnabled(false)
        scannerFragment?.setBackfacingCamera()
        scannerFragment?.setCenterTracker()
        scannerFragment?.setResultListener { barcode -> Log.d(LOG_TAG, "Barcode detected: ") }
        scannerFragment?.initScanning()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQ_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initScannerView()
        }
    }
}