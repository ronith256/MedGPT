package com.lucario.gpt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import java.io.File;

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
            sharedPreferences.edit().putString("user", username.getText().toString()).apply();
            sharedPreferences.edit().putString("password", password.getText().toString()).apply();
            startActivity(new Intent(LoginActivity.this, GetConsent.class));
        });
    }

    private boolean isLoggedIn(){
        usr = sharedPreferences.getString("user","null");
        pwd = sharedPreferences.getString("password", null);
        File signatureFile = new File(getCacheDir(), "signature.png");
        if (signatureFile.exists()) {
            consent = true;
        }
        return !usr.equals("null") && !pwd.equals("null") && consent;
    }
}