package com.lucario.gpt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import se.warting.signatureview.views.SignaturePad;

public class GetConsent extends AppCompatActivity {
    private EditText editInvestigatorName;
    private Button buttonSignature;
    private SignaturePad signaturePad;

    private File signatureFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String sessionKey = getIntent().getStringExtra("sessionKey");
        setContentView(R.layout.activity_get_consent);
        editInvestigatorName = findViewById(R.id.edit_investigator_name);
        buttonSignature = findViewById(R.id.button_signature);

        signaturePad = findViewById(R.id.signature_pad);
        int color = ContextCompat.getColor(this, R.color.background_color);
        signaturePad.setBackgroundColor(color);
        signatureFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), (sessionKey + ".png"));
        buttonSignature.setOnClickListener(new View.OnClickListener() {
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
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                String investigatorName = editInvestigatorName.getText().toString();
                if(editInvestigatorName.getText()!=null) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("consent", true);
                    resultIntent.putExtra("name", editInvestigatorName.getText().toString());
                    resultIntent.putExtra("filename", signatureFile.getAbsolutePath());
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                }
                else {
                    Toast.makeText(GetConsent.this, "Enter Investigator Name", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkIfSignatureExists();
    }

    private void checkIfSignatureExists(){
        File signatureFile = new File(getCacheDir(), "signature.png");
        if (signatureFile.exists()) {
            // Show the signature image

        }
    }
}
