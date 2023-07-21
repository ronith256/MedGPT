package com.lucario.gpt;

import static android.content.Context.MODE_PRIVATE;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.nio.charset.StandardCharsets;

public class SettingsDialogFragment extends DialogFragment {

    private EditText sessionTimeEditText;

    private EditText httpTimeOutEditText;

    private EditText ipAddressEditText;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Create a new AlertDialog.Builder object
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());

        // Inflate the custom layout for the dialog
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_settings, null);

        // Find the EditText and Button views in the layout
        sessionTimeEditText = view.findViewById(R.id.session_time_edit_text);
        httpTimeOutEditText = view.findViewById(R.id.http_timeout_edit_text);
        ipAddressEditText = view.findViewById(R.id.ip_address);
        Button saveButton = view.findViewById(R.id.save_button);

        String sessionTime = requireContext().getSharedPreferences("session", MODE_PRIVATE).getString("timeout", "3600");
        String httpTimeOut = requireContext().getSharedPreferences("session", MODE_PRIVATE).getString("http", "300");
        String ipAddress = requireContext().getSharedPreferences("session", MODE_PRIVATE).getString("ip", "http://13.235.27.136");
        if(!ipAddress.contains("http")){
            ipAddress = "http://" + ipAddress;
        }
        if(!sessionTime.equals("not")){
            sessionTimeEditText.setText(sessionTime);
            httpTimeOutEditText.setText(httpTimeOut);
            ipAddressEditText.setText(ipAddress);
        }
        // Set an onClickListener for the save button
        String finalIpAddress = ipAddress;
        saveButton.setOnClickListener(v -> {
            // Handle the save button click event
            requireContext().getSharedPreferences("session", MODE_PRIVATE).edit().putString("timeout", sessionTimeEditText.getText().toString()).apply();
            requireContext().getSharedPreferences("session", MODE_PRIVATE).edit().putString("http", httpTimeOutEditText.getText().toString()).apply();
            requireContext().getSharedPreferences("session", MODE_PRIVATE).edit().putString("ip", ipAddressEditText.getText().toString()).apply();
            try{
                MessageView.sessionTimeout = Integer.parseInt(sessionTimeEditText.getText().toString());
                MessageView.httpSessionTimeout = Integer.parseInt(httpTimeOutEditText.getText().toString());
                MessageView.BASE_URL = finalIpAddress;
            } catch (Exception ignored){
                MessageView.BASE_URL = "ip here";
                MessageView.sessionTimeout = 600;
                MessageView.httpSessionTimeout = 300;
            }
            dismiss();
        });

        // Set the custom layout for the dialog and return it
        builder.setView(view);
        return builder.create();
    }
}

