package com.lucario.gpt;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {
    private EditText username, password;
    private String usr, pwd;
    private Boolean consent;
    private Button loginButton;

    private boolean buttonState = false;
    private SharedPreferences sharedPreferences;

    private int consentRequestTimes = 0;

    private ActivityResultLauncher<Intent> launcher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("cred", MODE_PRIVATE);
        consent = false;
        if(isLoggedIn()){
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
        setContentView(R.layout.login_activity);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);
        loginButton.setEnabled(false);

        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            String name = data.getStringExtra("name");
                            String filename = data.getStringExtra("filename");
                            sharedPreferences.edit().putString("inv-name",name).apply();
                            sharedPreferences.edit().putString("sign-file-path", filename).apply();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        if(consentRequestTimes > 3){
                            Toast.makeText(this, "Maximum tries exceeded", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
        username.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(charSequence.length() > 1){
                    buttonState = true;
                } else {
                   buttonState = false;
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        password.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(charSequence.length() > 1 && buttonState){
                    loginButton.setEnabled(true);
                } else {
                    loginButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });


        loginButton.setOnClickListener(e->{
            new LoginTask().execute(username.getText().toString().trim(), password.getText().toString());
        });
    }

    private class LoginTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String username = params[0];
            String password = params[1];

            try {
                return login(username, password);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                sharedPreferences.edit().putString("user", username.getText().toString()).apply();
                sharedPreferences.edit().putString("password", password.getText().toString()).apply();
                launcher.launch(new Intent(LoginActivity.this, GetConsent.class).putExtra("sessionKey", "inv-sign").putExtra("inv", true));
            } else {
                showErrorDialog();
            }
        }

        private boolean login(String username, String password) {
            OkHttpClient client = new OkHttpClient();

            FormBody formBody = new FormBody.Builder()
                    .add("grant_type", "")
                    .add("username", username)
                    .add("password", password)
                    .add("scope", "")
                    .add("client_id", "")
                    .add("client_secret", "")
                    .build();

            Request request = new Request.Builder()
                    .url("http://13.235.27.136/login?client=app")
                    .post(formBody)
                    .addHeader("Accept", "application/json")
                    .build();

            try {
                Response response = client.newCall(request).execute();
                return response.code() == 200;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void showErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Invalid Credentials")
                .setMessage("The username or password you entered doesn't appear to belong to an account. Please check your input and try again.")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    // Handle "Try Again" button click
                    dialog.dismiss();
                });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Set a custom width for the dialog buttons area
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setMinimumWidth(0);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setMinimumHeight(0);
    }

    private boolean isLoggedIn(){
        usr = sharedPreferences.getString("user","null");
        pwd = sharedPreferences.getString("password", null);
        return !usr.equals("null") && !pwd.equals("null");
    }
}