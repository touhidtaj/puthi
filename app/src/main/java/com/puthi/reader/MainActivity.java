package com.puthi.reader;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puthi: a thin native shell around the offline web reader.
 * No AndroidX, no network permission. The web app is served from assets over
 * https://appassets.puthi/ so IndexedDB, workers and fetch behave normally.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.puthi";
    private static final int REQ_FILES = 11, REQ_TREE = 12, MAX_FILES = 5000;

    private WebView web;
    private boolean awaitingPerm = false;
    private Uri pendingView = null;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0B0907"));
        getWindow().setNavigationBarColor(Color.parseColor("#0B0907"));
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0B0907"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B0907"));
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBlockNetworkLoads(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Bridge(), "PuthiNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return empty(403, "Forbidden");
                String path = u.getPath() == null ? "/" : u.getPath();
                if (path.equals("/_f")) return serveFile(u.getQueryParameter("u"));
                return serveAsset(path);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                openExternal(u.toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (pendingView != null) waitThenView();
            }
        });

        handleIntent(getIntent());
        web.loadUrl("https://" + HOST + "/index.html");
    }

    // ---- serving ---------------------------------------------------------

    private static WebResourceResponse empty(int code, String msg) {
        return new WebResourceResponse("text/plain", "utf-8", code, msg, new HashMap<String, String>(),
                new ByteArrayInputStream(new byte[0]));
    }

    private WebResourceResponse serveAsset(String path) {
        String name = path.equals("/") ? "index.html" : path.substring(1);
        if (name.contains("..")) return empty(403, "Forbidden");
        try {
            InputStream in = getAssets().open(name);
            Map<String, String> h = new HashMap<String, String>();
            h.put("Cache-Control", "no-cache");
            return new WebResourceResponse(mime(name), "utf-8", 200, "OK", h, in);
        } catch (IOException e) {
            return empty(404, "Not Found");
        }
    }

    private static String mime(String n) {
        n = n.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "text/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private WebResourceResponse serveFile(String src) {
        InputStream in = openSource(src);
        if (in == null) return empty(404, "Not Found");
        Map<String, String> h = new HashMap<String, String>();
        h.put("Cache-Control", "no-store");
        return new WebResourceResponse("application/pdf", null, 200, "OK", h, in);
    }

    /** Opens a PDF by content:// URI or by absolute path under shared storage. */
    private InputStream openSource(String src) {
        if (src == null) return null;
        try {
            if (src.startsWith("content://")) {
                return getContentResolver().openInputStream(Uri.parse(src));
            }
            File root = Environment.getExternalStorageDirectory().getCanonicalFile();
            File f = new File(src).getCanonicalFile();
            if (!f.getPath().startsWith(root.getPath() + File.separator)) return null;
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf") || !f.isFile()) return null;
            return new FileInputStream(f);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- JS bridge ---------------------------------------------------------

    private class Bridge {
        @JavascriptInterface
        public boolean hasAllFiles() {
            return Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
        }

        @JavascriptInterface
        public void requestAllFiles() {
            ui.post(new Runnable() {
                public void run() {
                    if (Build.VERSION.SDK_INT < 30) {
                        Toast.makeText(MainActivity.this, "Android 11 or newer is needed. Use “Choose a folder” instead.", Toast.LENGTH_LONG).show();
                        callJs("window.__puthi&&window.__puthi.perm(false)");
                        return;
                    }
                    awaitingPerm = true;
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                }
            });
        }

        @JavascriptInterface
        public void scanAll(final String kind) {
            io.execute(new Runnable() {
                public void run() {
                    JSONArray out = new JSONArray();
                    File root = Environment.getExternalStorageDirectory();
                    ArrayDeque<File> stack = new ArrayDeque<File>();
                    stack.push(root);
                    int rootLen = root.getPath().length();
                    while (!stack.isEmpty() && out.length() < MAX_FILES) {
                        File d = stack.pop();
                        File[] list = d.listFiles();
                        if (list == null) continue;
                        for (File f : list) {
                            String n = f.getName();
                            if (n.startsWith(".")) continue;
                            if (f.isDirectory()) {
                                if (d.equals(root) && n.equals("Android")) continue;
                                stack.push(f);
                            } else if (n.toLowerCase(Locale.ROOT).endsWith(".pdf") && f.length() > 0) {
                                try {
                                    JSONObject o = new JSONObject();
                                    o.put("src", f.getPath());
                                    o.put("name", n);
                                    o.put("rel", f.getPath().substring(rootLen));
                                    out.put(o);
                                } catch (Exception ignored) { }
                            }
                        }
                    }
                    sendItems(kind, out);
                }
            });
        }

        @JavascriptInterface
        public void pickFiles() {
            ui.post(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/pdf");
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(i, REQ_FILES);
                }
            });
        }

        @JavascriptInterface
        public void pickFolder() {
            ui.post(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(i, REQ_TREE);
                }
            });
        }

        @JavascriptInterface
        public void setImmersive(final boolean on) {
            ui.post(new Runnable() {
                public void run() { applyImmersive(on); }
            });
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            ui.post(new Runnable() {
                public void run() { openExternal(url); }
            });
        }

        @JavascriptInterface
        public boolean saveFile(String name, String mime, String base64) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) return false;
                try { os.write(data); } finally { os.close(); }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ---- results & helpers -------------------------------------------------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_FILES) {
            JSONArray out = new JSONArray();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) addPicked(out, data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                addPicked(out, data.getData());
            }
            sendItems("files", out);
        } else if (req == REQ_TREE && data.getData() != null) {
            final Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            io.execute(new Runnable() {
                public void run() { sendItems("folder", walkTree(tree)); }
            });
        }
    }

    private void addPicked(JSONArray out, Uri u) {
        try {
            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        try {
            JSONObject o = new JSONObject();
            String name = displayName(u);
            o.put("src", u.toString());
            o.put("name", name);
            o.put("rel", "/Added files/" + name);
            out.put(o);
        } catch (Exception ignored) { }
    }

    private String displayName(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String last = u.getLastPathSegment();
        return last == null ? "document.pdf" : last;
    }

    private JSONArray walkTree(Uri tree) {
        JSONArray out = new JSONArray();
        ArrayDeque<String[]> stack = new ArrayDeque<String[]>(); // {documentId, relPath}
        stack.push(new String[]{DocumentsContract.getTreeDocumentId(tree), ""});
        while (!stack.isEmpty() && out.length() < MAX_FILES) {
            String[] cur = stack.pop();
            Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, cur[0]);
            Cursor c = null;
            try {
                c = getContentResolver().query(kids, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
                while (c != null && c.moveToNext() && out.length() < MAX_FILES) {
                    String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                    if (name == null || name.startsWith(".")) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        stack.push(new String[]{id, cur[1] + "/" + name});
                    } else if (name.toLowerCase(Locale.ROOT).endsWith(".pdf") || "application/pdf".equals(mime)) {
                        JSONObject o = new JSONObject();
                        o.put("src", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                        o.put("name", name);
                        o.put("rel", cur[1] + "/" + name);
                        out.put(o);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
        }
        return out;
    }

    private void sendItems(final String kind, final JSONArray items) {
        callJs("window.__puthi&&window.__puthi.items(" + JSONObject.quote(kind) + "," + items.toString() + ")");
    }

    private void callJs(final String js) {
        ui.post(new Runnable() {
            public void run() { if (web != null) web.evaluateJavascript(js, null); }
        });
    }

    private void applyImmersive(boolean on) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            if (on) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            View d = getWindow().getDecorView();
            d.setSystemUiVisibility(on
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void openExternal(String url) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) { }
    }

    // ---- "Open with Puthi" ---------------------------------------------------

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
        if (pendingView != null) waitThenView();
    }

    private void handleIntent(Intent i) {
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            pendingView = i.getData();
        }
    }

    private void waitThenView() {
        final Uri u = pendingView;
        if (u == null) return;
        final int[] tries = {0};
        final Runnable[] poll = new Runnable[1];
        poll[0] = new Runnable() {
            public void run() {
                if (pendingView == null) return;
                web.evaluateJavascript("(window.__puthi&&window.__puthi.ready)?1:0", new android.webkit.ValueCallback<String>() {
                    public void onReceiveValue(String v) {
                        if ("1".equals(v)) {
                            pendingView = null;
                            try {
                                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignored) { }
                            try {
                                JSONObject o = new JSONObject();
                                o.put("src", u.toString());
                                o.put("name", displayName(u));
                                o.put("rel", "/Opened files/" + displayName(u));
                                callJs("window.__puthi.view(" + o.toString() + ")");
                            } catch (Exception ignored) { }
                        } else if (++tries[0] < 60) {
                            ui.postDelayed(poll[0], 300);
                        }
                    }
                });
            }
        };
        ui.post(poll[0]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (awaitingPerm) {
            awaitingPerm = false;
            boolean ok = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
            callJs("window.__puthi&&window.__puthi.perm(" + ok + ")");
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__puthi&&window.__puthi.back())?1:0", new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String v) {
                if (!"1".equals(v)) finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
