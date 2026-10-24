package com.kirovo389.workbench;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ChatStore {
    private static final String PREFS = "workbench_chat_data";
    private static final String CHATS_KEY = "chats";
    private static final String MEMORY_KEY = "memory";

    private ChatStore() {}

    public static String id() { return UUID.randomUUID().toString(); }
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Attachment {
        public String uri;
        public String name;
        public String mime;
        public long size;
        public Attachment(String uri, String name, String mime, long size) {
            this.uri = uri; this.name = name; this.mime = mime; this.size = size;
        }
        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("uri", uri); o.put("name", name); o.put("mime", mime); o.put("size", size);
            return o;
        }
        public static Attachment fromJson(JSONObject o) {
            return new Attachment(o.optString("uri"), o.optString("name", "file"),
                    o.optString("mime", "application/octet-stream"), o.optLong("size", 0));
        }
    }

    public static final class Message {
        public String id = id();
        public String role;
        public String text;
        public long time = System.currentTimeMillis();
        public boolean streaming;
        public final List<Attachment> attachments = new ArrayList<>();
        public Message(String role, String text) { this.role = role; this.text = text == null ? "" : text; }
        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("role", role); o.put("text", text); o.put("time", time);
            JSONArray files = new JSONArray();
            for (Attachment a : attachments) files.put(a.toJson());
            o.put("attachments", files);
            return o;
        }
        public static Message fromJson(JSONObject o) {
            Message m = new Message(o.optString("role", "assistant"), o.optString("text", ""));
            m.id = o.optString("id", id()); m.time = o.optLong("time", System.currentTimeMillis());
            JSONArray files = o.optJSONArray("attachments");
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i); if (f != null) m.attachments.add(Attachment.fromJson(f));
            }
            return m;
        }
    }

    public static final class Chat {
        public String id = id();
        public String title = "Новый чат";
        public long updated = System.currentTimeMillis();
        public String project = "";
        public final List<Message> messages = new ArrayList<>();
        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("updated", updated); o.put("project", project);
            JSONArray ms = new JSONArray(); for (Message m : messages) ms.put(m.toJson());
            o.put("messages", ms); return o;
        }
        public static Chat fromJson(JSONObject o) {
            Chat c = new Chat(); c.id = o.optString("id", id());
            c.title = o.optString("title", "Новый чат"); c.updated = o.optLong("updated", System.currentTimeMillis());
            c.project = o.optString("project", "");
            JSONArray ms = o.optJSONArray("messages");
            if (ms != null) for (int i = 0; i < ms.length(); i++) {
                JSONObject m = ms.optJSONObject(i); if (m != null) c.messages.add(Message.fromJson(m));
            }
            return c;
        }
    }

    public static List<Chat> loadChats(Context c) {
        List<Chat> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(CHATS_KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o != null) result.add(Chat.fromJson(o));
            }
        } catch (JSONException ignored) {}
        Collections.sort(result, (a, b) -> Long.compare(b.updated, a.updated));
        return result;
    }

    public static void saveChats(Context c, List<Chat> chats) {
        JSONArray a = new JSONArray();
        try { for (Chat chat : chats) a.put(chat.toJson()); }
        catch (JSONException ignored) {}
        prefs(c).edit().putString(CHATS_KEY, a.toString()).apply();
    }

    public static List<String> loadMemory(Context c) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(MEMORY_KEY, "[]"));
            for (int i = 0; i < a.length(); i++) result.add(a.optString(i, ""));
        } catch (JSONException ignored) {}
        return result;
    }

    public static void saveMemory(Context c, List<String> memory) {
        JSONArray a = new JSONArray(); for (String item : memory) a.put(item);
        prefs(c).edit().putString(MEMORY_KEY, a.toString()).apply();
    }

    public static void clear(Context c) {
        prefs(c).edit().remove(CHATS_KEY).remove(MEMORY_KEY).apply();
    }
}
