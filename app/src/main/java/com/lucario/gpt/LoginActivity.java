package com.lucario.gpt;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import java.io.File;
import java.io.IOException;

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
    private SharedPreferences sharedPreferences;
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
        loginButton.setOnClickListener(e->{
            new LoginTask().execute(username.getText().toString(), password.getText().toString());
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
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
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
                    .url("http://13.235.27.136/login-app")
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