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

public class SettingsDialogFragment extends DialogFragment {

    private EditText sessionTimeEditText;
    private Button saveButton;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Create a new AlertDialog.Builder object
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Inflate the custom layout for the dialog
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_settings, null);

        // Find the EditText and Button views in the layout
        sessionTimeEditText = view.findViewById(R.id.session_time_edit_text);
        saveButton = view.findViewById(R.id.save_button);
        String sessionTime = requireContext().getSharedPreferences("session", MODE_PRIVATE).getString("timeout", "not");
        if(!sessionTime.equals("not")){
            sessionTimeEditText.setText(sessionTime);
        }
        // Set an onClickListener for the save button
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle the save button click event
                requireContext().getSharedPreferences("session", MODE_PRIVATE).edit().putString("api", "Bearer " + sessionTimeEditText.getText().toString()).apply();
                try{
                    MessageView.sessionTimeout = Integer.parseInt(sessionTimeEditText.getText().toString());
                } catch (Exception ignored){}
                dismiss();
            }
        });

        // Set the custom layout for the dialog and return it
        builder.setView(view);
        return builder.create();
    }
}

