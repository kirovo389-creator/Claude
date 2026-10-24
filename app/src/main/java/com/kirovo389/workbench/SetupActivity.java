package com.kirovo389.workbench;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SetupActivity extends AppCompatActivity {
    private EditText baseUrl, apiKey, modelId;
    private Spinner protocol;
    private CheckBox remember;
    private TextView error;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        baseUrl = findViewById(R.id.base_url_input);
        apiKey = findViewById(R.id.api_key_input);
        modelId = findViewById(R.id.model_id_input);
        protocol = findViewById(R.id.protocol_spinner);
        remember = findViewById(R.id.remember_key_checkbox);
        error = findViewById(R.id.setup_error);

        baseUrl.setText(AppConfig.baseUrl(this));
        apiKey.setText(AppConfig.apiKey(this));
        modelId.setText(AppConfig.model(this));
        remember.setChecked(AppConfig.rememberKey(this));
        protocol.setSelection("anthropic".equals(AppConfig.protocol(this)) ? 1 : 0);

        Button save = findViewById(R.id.save_config_button);
        save.setOnClickListener(v -> save());
    }

    private void save() {
        String url = baseUrl.getText().toString().trim();
        String key = apiKey.getText().toString().trim();
        String model = modelId.getText().toString().trim();

        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            error.setText("Укажите Base URL, начинающийся с https:// или http://.");
            return;
        }
        if (key.isEmpty()) {
            error.setText("Введите API Key.");
            return;
        }
        if (model.isEmpty()) {
            error.setText("Введите Model ID.");
            return;
        }

        String protocolValue = protocol.getSelectedItemPosition() == 1 ? "anthropic" : "openai";
        AppConfig.save(this, url, key, model, protocolValue, remember.isChecked());
        setResult(Activity.RESULT_OK);
        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
        finish();
    }
}
