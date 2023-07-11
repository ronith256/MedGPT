package com.lucario.gpt;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

public class GetConsent extends AppCompatActivity {
    private EditText editInvestigatorName;
    private Button buttonSignature;

    private ImageView imageSignature;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_consent);

        editInvestigatorName = findViewById(R.id.edit_investigator_name);
        buttonSignature = findViewById(R.id.button_signature);
        imageSignature = findViewById(R.id.image_signature);
        checkIfSignatureExists();
        buttonSignature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String investigatorName = editInvestigatorName.getText().toString();
                if(editInvestigatorName.getText()!=null){
                Intent intent = new Intent(GetConsent.this, SignatureActivity.class);
                intent.putExtra("investigator_name", investigatorName);
                startActivity(intent);} else {
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
            imageSignature.setVisibility(View.VISIBLE);
            Bitmap signatureBitmap = BitmapFactory.decodeFile(signatureFile.getAbsolutePath());
            imageSignature.setImageBitmap(signatureBitmap);
        } else {
            // Hide the signature image
            imageSignature.setVisibility(View.GONE);
        }
    }
}
