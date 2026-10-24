# Workbench — local AI chat workspace

A static, browser-based coding assistant workspace with a configurable model endpoint. It is a clean-room implementation inspired by modern AI chat tools; it is **not an official Anthropic product and is not Claude Code**.

## Features

- First-run connection form for **Base URL**, **API Key**, **Model ID**, and API protocol.
- OpenAI-compatible Chat Completions and Anthropic Messages API modes.
- Streaming assistant responses, stop generation, retry response, copy response, rename/delete chats.
- Normal and extended-reasoning request modes. Actual reasoning support depends on the selected provider and model.
- Local chat history, searchable conversations, local projects, and explicit saved memory injected into future prompts.
- Real browser file selection, drag/drop, and pasted images; attachments are stored in IndexedDB on the current device.
- Image attachments are sent as image content. Text/code files are read and included in the prompt. Video files are sampled into a few frames and those frames are sent as images; the original video is kept locally, not uploaded as a video stream. PDF is sent as a document only in Anthropic Messages mode.
- Light/dark themes, responsive layout, keyboard shortcuts, animated streaming/think state.

## Run

Open `index.html` in a modern browser. No local server, Node install, runtime libraries, or build step is required for local use.

## Configure the API

1. Open the app and enter your Base URL, API Key, and Model ID.
2. Choose the API protocol your provider supports.
3. Save, then use **Settings → Check connection** to test the endpoint.
4. If requests fail with a network/CORS error, the provider may not allow browser-origin requests. The app cannot bypass the provider's CORS rules.

For OpenAI-compatible APIs, the app appends `/chat/completions` if the provided base ends in `/v1`, or `/v1/chat/completions` if no versioned path is present. For Anthropic Messages, it appends `/messages` to a `/v1` base, or `/v1/messages` otherwise.

## Data and security

- Chats, projects, and memory stay in this browser's local storage.
- Attachment blobs stay in this browser's IndexedDB.
- If “remember API key” is selected, the key is stored locally on the device. Otherwise it is only kept in page memory until reload.
- The API key is never embedded in this repository or deployed site by the app. The browser sends it to the configured API endpoint; use only a trusted endpoint and a scoped key with spending limits.
- Clearing browser site data removes locally stored chats and attachments. Use Settings → Delete local chats and settings to clear the app's stored data.

## Current limitations

This is a browser-based chat workspace, not an agent with shell access. It cannot autonomously inspect or change your computer's filesystem, run terminal commands, edit project files outside files you upload, or run Claude Code's internal tools. It does not implement every proprietary Claude Code feature.

The OpenAI-compatible Chat Completions format has no universal PDF/video input schema. In that mode, PDFs are not transferred as raw PDF files; videos are sampled into a few frames and sent as images, if the selected model supports vision. Model/provider-specific capabilities vary.

## Deploy on GitHub Pages

The repository includes `.github/workflows/deploy.yml`, which validates the inline JavaScript and deploys the static site when `main` changes.

One-time setup:
1. Open repository **Settings → Pages**.
2. Under **Build and deployment → Source**, choose **GitHub Actions**.
3. Open the **Actions** tab and check the “Validate and deploy Workbench” workflow.

The public site will be available at `https://kirovo389-creator.github.io/Claude/` after Pages is enabled and the deployment workflow succeeds.

## License

No third-party UI assets or runtime libraries are bundled. Add a license before redistributing this project if you intend to make it public as a reusable package.
