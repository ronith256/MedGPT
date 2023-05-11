package com.lucario.gpt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {
    private EditText username, password;
    private String usr, pwd;
    private Button loginButton;
    private SharedPreferences sharedPreferences;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("cred", MODE_PRIVATE);
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
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
        });
    }

    private boolean isLoggedIn(){
        usr = sharedPreferences.getString("user","null");
        pwd = sharedPreferences.getString("password", null);
        if(usr.equals("null") || pwd.equals("null")){
            return false;
        } else {
            return true;
        }
    }
}