package com.lucario.gpt;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import se.warting.signatureview.views.SignaturePad;

public class SignatureActivity extends AppCompatActivity {
    private SignaturePad signaturePad;
    private Button buttonClear;
    private Button buttonSave;
    File signatureFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature);

        signaturePad = findViewById(R.id.signature_pad);
        int color = ContextCompat.getColor(this, R.color.background_color);
        signaturePad.setBackgroundColor(color);
        buttonClear = findViewById(R.id.button_clear);
        buttonSave = findViewById(R.id.button_save);
        signatureFile = new File(getCacheDir(), "signature.png");
        buttonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signaturePad.clear();
            }
        });

        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap signatureBitmap = signaturePad.getSignatureBitmap();
                FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(signatureFile);
                    // Compress the signature bitmap to PNG format and save it to the file
                    signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);

                    // Flush and close the file output stream
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    getSharedPreferences("cred", MODE_PRIVATE).edit().putBoolean("consent", true).apply();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                startActivity(new Intent(SignatureActivity.this, MainActivity.class));
                finish();
            }
        });
    }
}