package com.kirovo389.workbench;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;\nimport android.app.ProgressDialog;\nimport org.json.JSONArray;\nimport org.json.JSONObject;
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
    private TextView error;\n    private Button loadModels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        baseUrl = findViewById(R.id.base_url_input);
        apiKey = findViewById(R.id.api_key_input);
        modelId = findViewById(R.id.model_id_input);
        protocol = findViewById(R.id.protocol_spinner);
        remember = findViewById(R.id.remember_key_checkbox);
        error = findViewById(R.id.setup_error);\n        loadModels = findViewById(R.id.load_models_button);\n        loadModels.setOnClickListener(v -> loadModels());

        baseUrl.setText(AppConfig.baseUrl(this));
        apiKey.setText(AppConfig.apiKey(this));
        modelId.setText(AppConfig.model(this));
        remember.setChecked(AppConfig.rememberKey(this));
        protocol.setSelection("anthropic".equals(AppConfig.protocol(this)) ? 1 : 0);

        Button save = findViewById(R.id.save_config_button);
        save.setOnClickListener(v -> save());
    }


    private void loadModels() {
        String url = baseUrl.getText().toString().trim();
        String key = apiKey.getText().toString().trim();
        if (!(url.startsWith("https://") || url.startsWith("http://")) || key.isEmpty()) {
            error.setText("Сначала укажите Base URL и API Key, затем загрузите модели.");
            return;
        }
        String protocolValue = protocol.getSelectedItemPosition() == 1 ? "anthropic" : "openai";
        AppConfig.save(this, url, key, modelId.getText().toString().trim(), protocolValue, remember.isChecked());
        loadModels.setEnabled(false);
        loadModels.setText("Загружаю модели…");
        error.setText("");
        new Thread(() -> {
            try {
                JSONArray models = new ApiClient(this).fetchModels();
                java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                for (int i = 0; i < models.length(); i++) {
                    JSONObject item = models.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", item.optString("name", ""));
                    if (id.isEmpty()) continue;
                    String label = item.optString("name", item.optString("display_name", id));
                    ids.add(id);
                    labels.add(label.equals(id) ? id : label + "  ·  " + id);
                }
                runOnUiThread(() -> {
                    loadModels.setEnabled(true);
                    loadModels.setText("Загрузить список моделей");
                    if (ids.isEmpty()) {
                        error.setText("API вернул пустой список моделей. Можно ввести Model ID вручную.");
                        return;
                    }
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Выберите модель")
                            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                                modelId.setText(ids.get(which));
                                error.setText("Модель выбрана.");
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadModels.setEnabled(true);
                    loadModels.setText("Загрузить список моделей");
                    error.setText("Не удалось загрузить модели: " + e.getMessage()
                            + "\\nПроверьте URL, ключ и доступность /models. Можно ввести Model ID вручную.");
                });
            }
        }).start();
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
