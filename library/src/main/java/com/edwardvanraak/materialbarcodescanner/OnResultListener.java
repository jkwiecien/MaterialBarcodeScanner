package com.edwardvanraak.materialbarcodescanner;

import com.google.android.gms.vision.barcode.Barcode;

/**
 * Created by Jacek Kwiecień on 11.05.2017.
 */

public interface OnResultListener {
    void onResult(Barcode barcode);
}
