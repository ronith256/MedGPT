package com.lucario.gpt;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.noties.markwon.Markwon;
import se.warting.signatureview.views.SignaturePad;

public class GetConsent extends AppCompatActivity {
    private EditText editInvestigatorName;
    private Button buttonSignature;
    private SignaturePad signaturePad;

    private File signatureFile;
    private Button languageButton;

    private String consentFormML, consentFormEN;
    private String role = "Patient";
    private String roleML = "രോഗി";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String sessionKey = getIntent().getStringExtra("sessionKey");
        boolean investigatorMode = getIntent().getBooleanExtra("inv", false);
        if(investigatorMode){setContentView(R.layout.activity_inv_get_consent);}
        else{setContentView(R.layout.activity_get_consent);}
        Markwon markwon = Markwon.create(this);
        TextView consentTextView = findViewById(R.id.text_consent_form);
        editInvestigatorName = findViewById(R.id.edit_investigator_name);
        buttonSignature = findViewById(R.id.button_signature);
        languageButton = findViewById(R.id.language_switch);
        consentTextView.setMovementMethod(new ScrollingMovementMethod());
        consentTextView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (scrollY > oldScrollY) {
                    languageButton.setVisibility(View.GONE);
                }
                else if(scrollY < 10){
                    languageButton.setVisibility(View.VISIBLE);
                }
            }
        });

        if(languageButton!=null){
            String investigatorName = getIntent().getStringExtra("name");
            consentFormML = loadFileFromAssets(GetConsent.this, "Informedml.md");
            consentFormML = consentFormML.replace("%1$s", investigatorName);
            consentFormEN = loadFileFromAssets(GetConsent.this, "informeden.md");
            consentFormEN = consentFormEN.replace("%1$s", investigatorName);
            markwon.setMarkdown(consentTextView, consentFormEN);

            languageButton.setVisibility(View.VISIBLE);
            showRadioButtonDialog();
            languageButton.setOnClickListener(e->{
                if(languageButton.getText().equals("EN")){
                    markwon.setMarkdown(consentTextView, consentFormML);
                    languageButton.setText("ML");
                }
                else if(languageButton.getText().equals("ML")){
                    markwon.setMarkdown(consentTextView, consentFormEN);
                    languageButton.setText("EN");
                }
            });
        }

        buttonSignature.setEnabled(false);
        signaturePad = findViewById(R.id.signature_pad);
        int color = ContextCompat.getColor(this, R.color.background_color);
        signaturePad.setBackgroundColor(color);
        signatureFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), (sessionKey + ".png"));
        editInvestigatorName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(charSequence.length() > 2){
                    buttonSignature.setEnabled(true);
                } else {
                    buttonSignature.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
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
                if(investigatorName.length() > 2) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("consent", true);
                    resultIntent.putExtra("name", investigatorName);
                    resultIntent.putExtra("role",role);
                    resultIntent.putExtra("roleml", roleML);
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
    String loadFileFromAssets(Context context, String fileName) {
        try {
            InputStream inputStream = context.getAssets().open(fileName);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append("\n");
            }
            bufferedReader.close();

            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public void showRadioButtonDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Role");

        final String[] roles = {"Patient", "Legal Guardian"};
        final String[] rolesML = {"രോഗി", "നിയമപരമായ രക്ഷിതാവ്"};
        int checkedItem = 0; // Default checked item index

        builder.setSingleChoiceItems(roles, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                role = roles[which];
                roleML = rolesML[which];
            }
        });

        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {

            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

}
