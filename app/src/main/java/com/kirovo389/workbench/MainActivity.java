package com.kirovo389.workbench;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int SETUP_REQUEST = 81;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<ChatStore.Chat> chats = new ArrayList<>();
    private final List<String> memories = new ArrayList<>();
    private final List<ChatStore.Attachment> draftAttachments = new ArrayList<>();

    private LinearLayout messagesContainer;
    private ScrollView messagesScroll;
    private EditText promptInput;
    private TextView titleText, modelLabel, thinkButton, thinkStatus, sendButton, attachmentsLabel;
    private ChatStore.Chat currentChat;
    private ChatStore.Message streamingMessage;
    private TextView streamingTextView;
    private TextView streamingStateView;
    private ApiClient activeClient;
    private ObjectAnimator thinkAnimator;
    private boolean streaming;
    private boolean stopAfterCurrent;
    private boolean setupLaunched;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) return;
                for (Uri uri : uris) addAttachment(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        loadData();
        bindActions();
        updateChrome();
        renderMessages();

        if (!AppConfig.isConfigured(this)) openSetup();
    }

    private void bindViews() {
        messagesContainer = findViewById(R.id.messages_container);
        messagesScroll = findViewById(R.id.messages_scroll);
        promptInput = findViewById(R.id.prompt_input);
        titleText = findViewById(R.id.title_text);
        modelLabel = findViewById(R.id.model_label);
        thinkButton = findViewById(R.id.think_button);
        thinkStatus = findViewById(R.id.think_status);
        sendButton = findViewById(R.id.send_button);
        attachmentsLabel = findViewById(R.id.attachments_label);
    }

    private void loadData() {
        chats.clear();
        chats.addAll(ChatStore.loadChats(this));
        memories.clear();
        memories.addAll(ChatStore.loadMemory(this));
        SharedPreferences ui = getSharedPreferences("workbench_ui", MODE_PRIVATE);
        String lastId = ui.getString("active_chat", "");
        currentChat = null;
        for (ChatStore.Chat c : chats) if (c.id.equals(lastId)) { currentChat = c; break; }
        if (currentChat == null && !chats.isEmpty()) currentChat = chats.get(0);
    }

    private void bindActions() {
        findViewById(R.id.new_chat_button).setOnClickListener(v -> newChat());
        findViewById(R.id.history_button).setOnClickListener(v -> showHistory());
        findViewById(R.id.memory_button).setOnClickListener(v -> showMemory());
        findViewById(R.id.settings_button).setOnClickListener(v -> openSetup());

        findViewById(R.id.attach_button).setOnClickListener(v -> {
            if (streaming) {
                toast("Остановите генерацию перед добавлением файлов.");
                return;
            }
            filePicker.launch(new String[]{"*/*"});
        });
        thinkButton.setOnClickListener(v -> {
            boolean enabled = !AppConfig.thinkMode(this);
            AppConfig.setThinkMode(this, enabled);
            updateThinkMode();
            if (enabled) {
                thinkButton.animate().scaleX(1.08f).scaleY(1.08f).setDuration(130)
                        .withEndAction(() -> thinkButton.animate().scaleX(1f).scaleY(1f).setDuration(130)).start();
            }
        });
        sendButton.setOnClickListener(v -> {
            if (streaming) stopGeneration(); else sendMessage();
        });
        attachmentsLabel.setOnClickListener(v -> {
            if (!draftAttachments.isEmpty() && !streaming) {
                draftAttachments.clear();
                updateAttachmentsLabel();
            }
        });
        promptInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (!streaming && (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || enter)) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void openSetup() {
        if (setupLaunched) return;
        setupLaunched = true;
        startActivityForResult(new Intent(this, SetupActivity.class), SETUP_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETUP_REQUEST) {
            setupLaunched = false;
            if (AppConfig.isConfigured(this)) {
                updateChrome();
                renderMessages();
            } else if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Для работы нужно настроить API.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateChrome() {
        titleText.setText(currentChat == null ? "Workbench" : currentChat.title);
        modelLabel.setText(AppConfig.model(this).isEmpty() ? "AI workspace" : AppConfig.model(this));
        updateThinkMode();
        updateAttachmentsLabel();
        sendButton.setText(streaming ? "■" : "↑");
        sendButton.setContentDescription(streaming ? "Остановить генерацию" : "Отправить сообщение");
        thinkStatus.setText(streaming
                ? (AppConfig.thinkMode(this) ? "Расширенно размышляю…" : "Генерирую ответ…")
                : "Готов к работе");
    }

    private void updateThinkMode() {
        boolean enabled = AppConfig.thinkMode(this);
        thinkButton.setText(enabled ? "✳ Think · ON" : "✳ Think");
        thinkButton.setTextColor(getColor(enabled ? R.color.accent : R.color.text_secondary));
        thinkButton.setBackgroundResource(enabled ? R.drawable.bg_primary_button : R.drawable.bg_chip);
        if (enabled) {
            thinkButton.setTextColor(getColor(R.color.white));
        }
    }

    private void updateAttachmentsLabel() {
        if (draftAttachments.isEmpty()) {
            attachmentsLabel.setVisibility(View.GONE);
            attachmentsLabel.setText("");
            return;
        }
        StringBuilder b = new StringBuilder("Прикреплено · нажмите, чтобы убрать все: ");
        for (int i = 0; i < draftAttachments.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(draftAttachments.get(i).name);
        }
        attachmentsLabel.setText(b.toString());
        attachmentsLabel.setVisibility(View.VISIBLE);
    }

    private void addAttachment(Uri uri) {
        try {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some picker providers grant a temporary, non-persistable URI.
            }
            String name = queryDisplayName(uri);
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";
            for (ChatStore.Attachment a : draftAttachments) if (a.uri.equals(uri.toString())) return;
            draftAttachments.add(new ChatStore.Attachment(uri.toString(), name, mime, 0));
            updateAttachmentsLabel();
            toast("Добавлен файл: " + name);
        } catch (Exception e) {
            toast("Не удалось прикрепить файл: " + e.getMessage());
        }
    }

    private String queryDisplayName(Uri uri) {
        String name = uri.getLastPathSegment() == null ? "file" : uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) name = cursor.getString(col);
            }
        } catch (Exception ignored) {}
        return name == null ? "file" : name;
    }

    private void newChat() {
        if (streaming) {
            Toast.makeText(this, "Сначала остановите текущий ответ.", Toast.LENGTH_SHORT).show();
            return;
        }
        ChatStore.Chat c = new ChatStore.Chat();
        chats.add(0, c);
        currentChat = c;
        draftAttachments.clear();
        promptInput.setText("");
        saveData();
        updateChrome();
        renderMessages();
    }

    private void showHistory() {
        if (chats.isEmpty()) {
            newChat();
            return;
        }
        String[] titles = new String[chats.size()];
        for (int i = 0; i < chats.size(); i++) {
            ChatStore.Chat c = chats.get(i);
            titles[i] = (c == currentChat ? "✓  " : "") + c.title + "  ·  " + c.messages.size();
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("История чатов")
                .setItems(titles, (d, which) -> {
                    currentChat = chats.get(which);
                    saveData();
                    updateChrome();
                    renderMessages();
                })
                .setNeutralButton("Новый чат", (d, which) -> newChat())
                .setNegativeButton("Удалить текущий", (d, which) -> confirmDeleteCurrent())
                .setPositiveButton("Закрыть", null)
                .create();
        dialog.show();
    }

    private void confirmDeleteCurrent() {
        if (currentChat == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Удалить чат?")
                .setMessage("История этого чата будет удалена с устройства.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d, w) -> {
                    chats.remove(currentChat);
                    currentChat = chats.isEmpty() ? null : chats.get(0);
                    saveData();
                    updateChrome();
                    renderMessages();
                }).show();
    }

    private void showMemory() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));

        TextView explainer = new TextView(this);
        explainer.setText("Эти заметки добавляются в контекст будущих запросов. Не сохраняйте API-ключи или пароли.");
        explainer.setTextSize(12);
        explainer.setTextColor(getColor(R.color.text_secondary));
        box.addView(explainer);

        ScrollView memoryScroll = new ScrollView(this);
        LinearLayout entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        memoryScroll.addView(entries);
        for (int i = 0; i < memories.size(); i++) {
            final int index = i;
            TextView item = new TextView(this);
            item.setText("✳  " + memories.get(i) + "\nНажмите, чтобы удалить");
            item.setTextSize(13);
            item.setTextColor(getColor(R.color.text_primary));
            item.setPadding(0, dp(10), 0, dp(10));
            entries.addView(item);
            item.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Удалить эту запись памяти?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dd, ww) -> {
                        memories.remove(index);
                        ChatStore.saveMemory(this, memories);
                        showMemory();
                    }).show());
        }
        if (memories.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Память пока пуста.");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setPadding(0, dp(14), 0, dp(14));
            entries.addView(empty);
        }
        box.addView(memoryScroll, new LinearLayout.LayoutParams(-1, Math.min(dp(190), dp(46) * Math.max(1, memories.size()))));

        EditText input = new EditText(this);
        input.setHint("Добавить заметку в память…");
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setTextSize(14);
        box.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Память")
                .setView(box)
                .setPositiveButton("Добавить", null)
                .setNeutralButton("Очистить память", null)
                .setNegativeButton("Закрыть", null)
                .create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) {
                    input.setError("Введите текст памяти");
                    return;
                }
                memories.add(value);
                ChatStore.saveMemory(this, memories);
                dialog.dismiss();
                showMemory();
                toast("Запись памяти сохранена.");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(btn ->
                    new AlertDialog.Builder(this).setMessage("Очистить всю память?")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Очистить", (d, w) -> {
                                memories.clear();
                                ChatStore.saveMemory(this, memories);
                                dialog.dismiss();
                                showMemory();
                            }).show());
        });
        dialog.show();
    }

    private void renderMessages() {
        messagesContainer.removeAllViews();
        streamingTextView = null;
        streamingStateView = null;
        if (currentChat == null || currentChat.messages.isEmpty()) {
            renderWelcome();
            scrollToBottom();
            return;
        }
        for (ChatStore.Message m : currentChat.messages) {
            if ("assistant".equals(m.role) && m.text.isEmpty() && m.streaming) {
                addStreamingBubble(m);
            } else {
                addMessageBubble(m);
            }
        }
        scrollToBottom();
    }

    private void renderWelcome() {
        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setPadding(dp(9), dp(34), dp(9), dp(30));

        TextView eyebrow = new TextView(this);
        eyebrow.setText("ВАШЕ РАБОЧЕЕ ПРОСТРАНСТВО");
        eyebrow.setTextColor(getColor(R.color.accent));
        eyebrow.setTextSize(11);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        welcome.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText("Над чем поработаем?");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(31);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(12), 0, dp(8));
        welcome.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Чат с вашей моделью, вложения и локальная память — в одном рабочем пространстве.");
        subtitle.setTextColor(getColor(R.color.text_secondary));
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(dp(3), 1f);
        welcome.addView(subtitle);

        String[][] examples = {
                {"Разобрать проект", "Объясни архитектуру и ключевые файлы."},
                {"Найти ошибку", "Помоги пошагово диагностировать эту ошибку: "},
                {"Спланировать функцию", "Составь план реализации функции с этапами и тестами."},
                {"Проанализировать файлы", "Проанализируй прикреплённые файлы и перечисли главные выводы."}
        };
        for (String[] example : examples) {
            TextView card = new TextView(this);
            card.setText(example[0] + "\n" + example[1]);
            card.setTextColor(getColor(R.color.text_primary));
            card.setTextSize(13);
            card.setLineSpacing(dp(3), 1f);
            card.setPadding(dp(14), dp(13), dp(14), dp(13));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.surface));
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), getColor(R.color.line));
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(9);
            welcome.addView(card, lp);
            card.setOnClickListener(v -> {
                promptInput.setText(example[1]);
                promptInput.setSelection(promptInput.length());
                promptInput.requestFocus();
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(promptInput, InputMethodManager.SHOW_IMPLICIT);
            });
            card.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) v.animate().scaleX(0.99f).scaleY(0.99f).setDuration(80).start();
                else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                return false;
            });
        }

        TextView note = new TextView(this);
        note.setText("Примечание: это нативный AI-клиент, а не встроенный терминал. Анализируйте файлы, выбрав их вручную. Функции зависят от API и модели.");
        note.setTextColor(getColor(R.color.text_secondary));
        note.setTextSize(11);
        note.setPadding(0, dp(17), 0, 0);
        welcome.addView(note);

        messagesContainer.addView(welcome, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addMessageBubble(ChatStore.Message message) {
        boolean user = "user".equals(message.role);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(user ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(-1, -2);
        colParams.bottomMargin = dp(17);
        messagesContainer.addView(column, colParams);

        TextView label = new TextView(this);
        label.setText(user ? "ВЫ" : "WORKBENCH");
        label.setTextColor(getColor(R.color.text_secondary));
        label.setTextSize(10);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(dp(3), 0, dp(3), dp(5));
        column.addView(label);

        TextView body = new TextView(this);
        body.setText(message.text);
        body.setTextColor(getColor(R.color.text_primary));
        body.setTextSize(14);
        body.setLineSpacing(dp(4), 1f);
        body.setTextIsSelectable(true);
        body.setPadding(dp(13), dp(11), dp(13), dp(11));
        body.setBackgroundResource(user ? R.drawable.bg_user_message : R.drawable.bg_assistant_message);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(user ? (int)(getResources().getDisplayMetrics().widthPixels * .84f) : -1, -2);
        column.addView(body, bodyParams);
        body.setAlpha(0f);
        body.setTranslationY(dp(5));
        body.animate().alpha(1f).translationY(0f).setDuration(180).start();

        if (!message.attachments.isEmpty()) {
            TextView files = new TextView(this);
            StringBuilder b = new StringBuilder("Вложения: ");
            for (int i = 0; i < message.attachments.size(); i++) {
                if (i > 0) b.append(", ");
                b.append(message.attachments.get(i).name);
            }
            files.setText(b.toString());
            files.setTextColor(getColor(R.color.text_secondary));
            files.setTextSize(11);
            files.setPadding(dp(4), dp(5), dp(4), 0);
            column.addView(files);
        }

        if (!user) body.setOnLongClickListener(v -> {
            android.content.ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("AI response", message.text));
            toast("Ответ скопирован");
            return true;
        });
    }

    private void addStreamingBubble(ChatStore.Message message) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.START);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(-1, -2);
        colParams.bottomMargin = dp(18);
        messagesContainer.addView(column, colParams);

        TextView label = new TextView(this);
        label.setText("WORKBENCH");
        label.setTextColor(getColor(R.color.text_secondary));
        label.setTextSize(10);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(dp(3), 0, dp(3), dp(5));
        column.addView(label);

        streamingStateView = new TextView(this);
        streamingStateView.setText(AppConfig.thinkMode(this) ? "✳  Расширенно размышляю…" : "◌  Думаю…");
        streamingStateView.setTextColor(getColor(R.color.text_secondary));
        streamingStateView.setTextSize(12);
        streamingStateView.setPadding(dp(13), dp(12), dp(13), dp(12));
        streamingStateView.setBackgroundResource(R.drawable.bg_assistant_message);
        column.addView(streamingStateView);

        streamingTextView = new TextView(this);
        streamingTextView.setText(message.text);
        streamingTextView.setTextColor(getColor(R.color.text_primary));
        streamingTextView.setTextSize(14);
        streamingTextView.setLineSpacing(dp(4), 1f);
        streamingTextView.setTextIsSelectable(true);
        streamingTextView.setPadding(dp(13), dp(11), dp(13), dp(11));
        streamingTextView.setBackgroundResource(R.drawable.bg_assistant_message);
        if (!message.text.isEmpty()) column.addView(streamingTextView, new LinearLayout.LayoutParams(-1, -2));
        if (AppConfig.thinkMode(this)) {
            thinkAnimator = ObjectAnimator.ofFloat(thinkButton, View.ROTATION, 0f, 360f);
            thinkAnimator.setDuration(2600);
            thinkAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            thinkAnimator.start();
        }
    }

    private void sendMessage() {
        if (streaming) return;
        String text = promptInput.getText().toString().trim();
        if (text.isEmpty() && draftAttachments.isEmpty()) {
            promptInput.setError("Введите сообщение или прикрепите файл");
            return;
        }
        if (!AppConfig.isConfigured(this)) {
            openSetup();
            return;
        }
        if (currentChat == null) {
            currentChat = new ChatStore.Chat();
            chats.add(0, currentChat);
        }
        ChatStore.Message user = new ChatStore.Message("user", text);
        user.attachments.addAll(draftAttachments);
        currentChat.messages.add(user);
        if ("Новый чат".equals(currentChat.title)) {
            String title = text.isEmpty() ? (user.attachments.isEmpty() ? "Новый чат" : user.attachments.get(0).name) : text;
            title = title.replaceAll("\\s+", " ").trim();
            currentChat.title = title.length() > 48 ? title.substring(0, 48) + "…" : title;
        }
        currentChat.updated = System.currentTimeMillis();
        draftAttachments.clear();
        promptInput.setText("");
        hideKeyboard();
        saveData();
        updateChrome();
        renderMessages();

        streamingMessage = new ChatStore.Message("assistant", "");
        streamingMessage.streaming = true;
        currentChat.messages.add(streamingMessage);
        streaming = true;
        stopAfterCurrent = false;
        updateChrome();
        addStreamingBubble(streamingMessage);
        scrollToBottom();

        activeClient = new ApiClient(this);
        ChatStore.Chat targetChat = currentChat;
        executor.execute(() -> {
            String error = null;
            try {
                activeClient.stream(targetChat, new ArrayList<>(memories), chunk -> {
                    runOnUiThread(() -> appendStreamChunk(chunk));
                });
            } catch (Exception e) {
                if (!stopAfterCurrent && !(e instanceof java.net.SocketException && stopAfterCurrent)) {
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
            }
            String finalError = error;
            runOnUiThread(() -> finishStreaming(finalError));
        });
    }

    private void appendStreamChunk(String chunk) {
        if (streamingMessage == null || !streaming) return;
        streamingMessage.text += chunk;
        if (streamingStateView != null && streamingStateView.getVisibility() == View.VISIBLE) {
            streamingStateView.setVisibility(View.GONE);
        }
        if (streamingTextView == null) {
            // Create the text bubble on first token without recreating the rest of the chat.
            streamingTextView = new TextView(this);
            streamingTextView.setTextColor(getColor(R.color.text_primary));
            streamingTextView.setTextSize(14);
            streamingTextView.setLineSpacing(dp(4), 1f);
            streamingTextView.setTextIsSelectable(true);
            streamingTextView.setPadding(dp(13), dp(11), dp(13), dp(11));
            streamingTextView.setBackgroundResource(R.drawable.bg_assistant_message);
            // The active streaming column is the final child of the conversation.
            View last = messagesContainer.getChildAt(messagesContainer.getChildCount() - 1);
            if (last instanceof LinearLayout) ((LinearLayout) last).addView(streamingTextView, new LinearLayout.LayoutParams(-1, -2));
        }
        streamingTextView.setText(streamingMessage.text);
        scrollToBottom();
    }

    private void finishStreaming(String error) {
        streaming = false;
        if (thinkAnimator != null) {
            thinkAnimator.cancel();
            thinkAnimator = null;
            thinkButton.setRotation(0f);
        }
        if (streamingMessage != null) {
            streamingMessage.streaming = false;
            if (error != null && !stopAfterCurrent) {
                if (!streamingMessage.text.isEmpty()) streamingMessage.text += "\n\n";
                streamingMessage.text += "Ошибка: " + error;
                if (error.toLowerCase(java.util.Locale.ROOT).contains("timeout")) {
                    streamingMessage.text += "\nПроверьте доступность endpoint и настройки API.";
                }
            } else if (streamingMessage.text.trim().isEmpty() && stopAfterCurrent) {
                streamingMessage.text = "Генерация остановлена.";
            } else if (streamingMessage.text.trim().isEmpty() && error == null) {
                streamingMessage.text = "API завершил ответ без текста.";
            }
        }
        activeClient = null;
        streaming = false;
        stopAfterCurrent = false;
        if (currentChat != null) currentChat.updated = System.currentTimeMillis();
        saveData();
        renderMessages();
        updateChrome();
        if (error != null) toast("Ошибка API: " + (error.length() > 170 ? error.substring(0, 170) + "…" : error));
    }

    private void stopGeneration() {
        if (!streaming) return;
        stopAfterCurrent = true;
        sendButton.setEnabled(false);
        thinkStatus.setText("Останавливаю…");
        if (activeClient != null) activeClient.cancel();
        // finishStreaming is called when the request worker exits.
    }

    private void showHistoryMemoryRefresh() {
        memories.clear();
        memories.addAll(ChatStore.loadMemory(this));
    }

    private void saveData() {
        Collections.sort(chats, (a, b) -> Long.compare(b.updated, a.updated));
        ChatStore.saveChats(this, chats);
        getSharedPreferences("workbench_ui", MODE_PRIVATE).edit()
                .putString("active_chat", currentChat == null ? "" : currentChat.id)
                .apply();
    }

    private void scrollToBottom() {
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void hideKeyboard() {
        View current = getCurrentFocus();
        if (current != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(current.getWindowToken(), 0);
        }
    }

    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (activeClient != null) activeClient.cancel();
        if (thinkAnimator != null) thinkAnimator.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }
}
