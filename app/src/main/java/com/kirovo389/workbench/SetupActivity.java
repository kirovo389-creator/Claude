package com.kirovo389.workbench;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends AppCompatActivity {
    private EditText baseUrl;
    private EditText apiKey;
    private EditText modelId;
    private CheckBox remember;
    private TextView error;
    private Button loadModels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        baseUrl = findViewById(R.id.base_url_input);
        apiKey = findViewById(R.id.api_key_input);
        modelId = findViewById(R.id.model_id_input);
        remember = findViewById(R.id.remember_key_checkbox);
        error = findViewById(R.id.setup_error);
        loadModels = findViewById(R.id.load_models_button);

        baseUrl.setText(AppConfig.baseUrl(this));
        apiKey.setText(AppConfig.apiKey(this));
        modelId.setText(AppConfig.model(this));
        remember.setChecked(AppConfig.rememberKey(this));

        loadModels.setOnClickListener(v -> loadModels());
        findViewById(R.id.save_config_button).setOnClickListener(v -> save());
    }

    private void save() {
        String url = baseUrl.getText().toString().trim();
        String key = apiKey.getText().toString().trim();
        String model = modelId.getText().toString().trim();

        if (!"https://api.reformboss.com/v1".equalsIgnoreCase(url.replaceAll("/+$", ""))) {
            error.setText("Для этого приложения Base URL должен быть https://api.reformboss.com/v1.");
            return;
        }
        if (key.isEmpty()) {
            error.setText("Введите API Key.");
            return;
        }
        if (model.isEmpty()) {
            error.setText("Введите Model ID или загрузите список моделей.");
            return;
        }

        AppConfig.save(this, AppConfig.DEFAULT_BASE_URL, key, model, AppConfig.PROVIDER_ID, remember.isChecked());
        setResult(Activity.RESULT_OK);
        Toast.makeText(this, "Подключение Reformboss сохранено", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void loadModels() {
        String url = baseUrl.getText().toString().trim().replaceAll("/+$", "");
        String key = apiKey.getText().toString().trim();
        if (!"https://api.reformboss.com/v1".equalsIgnoreCase(url) || key.isEmpty()) {
            error.setText("Укажите endpoint Reformboss и API Key.");
            return;
        }

        AppConfig.save(this, AppConfig.DEFAULT_BASE_URL, key, modelId.getText().toString().trim(),
                AppConfig.PROVIDER_ID, remember.isChecked());
        loadModels.setEnabled(false);
        loadModels.setText("Загружаю список…");
        error.setText("");

        new Thread(() -> {
            try {
                JSONArray models = new ApiClient(getApplicationContext()).fetchModels();
                List<String> ids = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < models.length(); i++) {
                    JSONObject item = models.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", item.optString("name", ""));
                    if (id.isEmpty()) continue;
                    String label = item.optString("name", item.optString("display_name", id));
                    ids.add(id);
                    labels.add(label.equals(id) ? id : label + " · " + id);
                }
                runOnUiThread(() -> {
                    loadModels.setEnabled(true);
                    loadModels.setText("Загрузить список моделей");
                    if (ids.isEmpty()) {
                        error.setText("API не вернул список моделей. Можно ввести Model ID вручную.");
                        return;
                    }
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Модели Reformboss")
                            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                                modelId.setText(ids.get(which));
                                error.setText("Выбрана модель: " + ids.get(which));
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadModels.setEnabled(true);
                    loadModels.setText("Загрузить список моделей");
                    error.setText("Не удалось загрузить список: " + e.getMessage()
                            + "\nМожно ввести Model ID вручную.");
                });
            }
        }, "reformboss-model-list").start();
    }
}
