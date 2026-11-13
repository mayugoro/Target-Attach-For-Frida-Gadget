package com.mayugoro.targetattach;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String CONFIG_PATH = "/data/local/tmp/re.zyg.fri/config.json";
    private static final String GADGET_PATH = "/data/local/tmp/re.zyg.fri/libgadget.so";

    private List<AppEntry> allApps = new ArrayList<>();
    private Set<String> targets = new HashSet<>();
    private AppAdapter adapter;

    static class AppEntry {
        final String packageName;
        final String label;
        Drawable icon;
        AppEntry(String p, String l, Drawable i) { packageName = p; label = l; icon = i; }
    }

    static class ViewHolder {
        ImageView icon;
        TextView label;
        TextView pkgText;
        Switch sw;
        String pkg;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        checkRootAndWarn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // balik dari Magisk/KernelSU -> cek ulang root
        if (rootDenied) {
            checkRootAndWarn();
        }
    }

    private boolean rootDenied = false;
    private boolean rootDialogShowing = false;

    private void checkRootAndWarn() {
        new Thread(() -> {
            final boolean has = hasRoot();
            runOnUiThread(() -> {
                if (!has) {
                    rootDenied = true;
                    showRootDialog();
                } else {
                    // root aktif, baru muat data
                    rootDenied = false;
                    loadData();
                }
            });
        }).start();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // --- top bar ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(10), dp(8), dp(10));

        TextView title = new TextView(this);
        title.setText("Target Attach 4 Frida");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button restartBtn = new Button(this);
        restartBtn.setText("Restart");
        restartBtn.setOnClickListener(v -> confirmRestart());

        topBar.addView(title);
        topBar.addView(restartBtn);

        // --- search box ---
        EditText searchBox = new EditText(this);
        searchBox.setHint("Cari package / nama app...");
        searchBox.setSingleLine(true);
        searchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { adapter.filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        // --- list ---
        ListView list = new ListView(this);
        adapter = new AppAdapter();
        list.setAdapter(adapter);

        root.addView(topBar);
        root.addView(searchBox);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            List<AppEntry> apps = loadApps();
            Set<String> t = loadTargets();
            runOnUiThread(() -> {
                allApps = apps;
                targets = t;
                adapter.update(allApps, targets, "");
            });
        }).start();
    }

    private List<AppEntry> loadApps() {
        List<AppEntry> result = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> infos = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : infos) {
            // blacklist: app kita sendiri (jangan sampai bisa di-stuck sendiri)
            if (getPackageName().equals(ai.packageName)) continue;
            // hanya app hasil install user (skip app bawaan/system)
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystem) continue;
            String label = String.valueOf(pm.getApplicationLabel(ai));
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(ai); } catch (Exception ignored) {}
            result.add(new AppEntry(ai.packageName, label, icon));
        }
        Collections.sort(result, (a, b) ->
                a.label.toLowerCase().compareTo(b.label.toLowerCase()));
        return result;
    }

    private Set<String> loadTargets() {
        Set<String> set = new HashSet<>();
        String json = suRead(CONFIG_PATH);
        if (json == null || json.trim().isEmpty()) return set;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("targets");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject t = arr.optJSONObject(i);
                    if (t == null) continue;
                    if (!t.optBoolean("enabled", false)) continue;
                    String name = t.optString("app_name", "");
                    if (!name.isEmpty()) set.add(name);
                }
            }
        } catch (Exception ignored) {}
        return set;
    }

    private void toggleTarget(String packageName, boolean on) {
        if (on) targets.add(packageName);
        else targets.remove(packageName);
        boolean ok = suWrite(CONFIG_PATH, buildConfig());
        runOnUiThread(() -> {
            Toast.makeText(this, ok
                    ? (on ? "✔ " + packageName : "✖ " + packageName)
                    : "✖ GAGAL tulis config (root?)", Toast.LENGTH_SHORT).show();
            if (!ok) loadData();
        });
    }

    private String buildConfig() {
        try {
            JSONArray arr = new JSONArray();
            List<String> names = new ArrayList<>(targets);
            Collections.sort(names);
            for (String name : names) {
                JSONObject t = new JSONObject();
                t.put("app_name", name);
                t.put("enabled", true);
                t.put("start_up_delay_ms", 0);
                JSONArray libs = new JSONArray();
                JSONObject lib = new JSONObject();
                lib.put("path", GADGET_PATH);
                libs.put(lib);
                t.put("injected_libraries", libs);
                JSONObject gate = new JSONObject();
                gate.put("enabled", true);
                gate.put("mode", "freeze");
                gate.put("injected_libraries", new JSONArray());
                t.put("child_gating", gate);
                arr.put(t);
            }
            JSONObject root = new JSONObject();
            root.put("targets", arr);
            return root.toString(4);
        } catch (Exception e) {
            return "{\"targets\":[]}";
        }
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this)
                .setTitle("Restart HP?")
                .setMessage("Menjalankan `su -c reboot`. HP akan mati-hidup agar config ZygiskFrida re-apply bersih.")
                .setPositiveButton("Reboot", (d, w) -> {
                    suExec("reboot");
                    Toast.makeText(this, "Reboot dikirim...", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ---- su helpers ----
    private boolean hasRoot() {
        try {
            Process p = new ProcessBuilder("su", "-c", "id -u")
                    .redirectErrorStream(true).start();
            String out = readStream(p.getInputStream());
            p.waitFor();
            // root aktif HANYA jika `su -c "id -u"` balik 0.
            // (jangan fallback cek binary su, karena su selalu ada di HP rooted
            //  walau akses app-nya di-deny -> bikin false positive)
            return out != null && out.trim().equals("0");
        } catch (Exception e) {
            return false;
        }
    }

    private void showRootDialog() {
        if (rootDialogShowing) return;
        rootDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle("Butuh akses root")
                .setMessage("Berikan kami akses ke root SuperUser agar aplikasi berjalan normal.")
                .setPositiveButton("Berikan akses root", (d, w) -> {
                    rootDialogShowing = false;
                    openRootManager();
                })
                .setNegativeButton("Nanti", (d, w) -> {
                    rootDialogShowing = false;
                    Toast.makeText(this, "Akses root belum diberikan", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private void openRootManager() {
        // coba buka Magisk / KernelSU / APatch / superuser manager umum
        String[] packages = {
                "com.topjohnwu.magisk",
                "com.topjohnwu.magisk.debug",
                "me.weishu.kernelsu",
                "me.bmax.apatch",
                "com.kingroot.kinguser",
                "eu.basicairdata.graziano",
        };
        String[] activities = {
                "com.topjohnwu.magisk.ui.MainActivity",
                "com.topjohnwu.magisk.debug.ui.MainActivity",
        };
        for (String pkg : packages) {
            try {
                android.content.Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) {
                    startActivity(i);
                    return;
                }
            } catch (Exception ignored) {}
        }
        // fallback: minta buka settings app list
        try {
            android.content.Intent i = new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e2) {
            Toast.makeText(this, "Buka Magisk/KernelSU lalu beri akses root", Toast.LENGTH_LONG).show();
        }
    }

    private static String suRead(String path) {
        try {
            Process p = new ProcessBuilder("su", "-c", "cat " + path)
                    .redirectErrorStream(true).start();
            String out = readStream(p.getInputStream());
            p.waitFor();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean suWrite(String path, String content) {
        try {
            File tmp = File.createTempFile("frida_target", ".json");
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            String tmpPath = tmp.getAbsolutePath();
            Process p = new ProcessBuilder("su", "-c",
                    "cp " + tmpPath + " " + path + " && chmod 644 " + path)
                    .redirectErrorStream(true).start();
            readStream(p.getInputStream());
            int code = p.waitFor();
            tmp.delete();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void suExec(String cmd) {
        new Thread(() -> {
            try {
                new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            } catch (Exception ignored) {}
        }).start();
    }

    private static String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ---- adapter ----
    class AppAdapter extends BaseAdapter {
        private final Object lock = new Object();
        private List<AppEntry> display = new ArrayList<>();

        void update(List<AppEntry> from, Set<String> t, String query) {
            synchronized (lock) {
                targets = t;
                display = filterList(from, query);
            }
            notifyDataSetChanged();
        }

        void filter(String query) {
            synchronized (lock) {
                display = filterList(allApps, query);
            }
            notifyDataSetChanged();
        }

        private List<AppEntry> filterList(List<AppEntry> from, String q) {
            String query = q == null ? "" : q.toLowerCase().trim();
            List<AppEntry> out = new ArrayList<>();
            for (AppEntry a : from) {
                if (query.isEmpty()
                        || a.label.toLowerCase().contains(query)
                        || a.packageName.toLowerCase().contains(query)) {
                    out.add(a);
                }
            }
            // target-ON ditaruh paling atas, sisanya urut abjad
            Collections.sort(out, (a, b) -> {
                boolean ta = targets.contains(a.packageName);
                boolean tb = targets.contains(b.packageName);
                if (ta != tb) return ta ? -1 : 1;
                return a.label.toLowerCase().compareTo(b.label.toLowerCase());
            });
            return out;
        }

        @Override public int getCount() { synchronized (lock) { return display.size(); } }
        @Override public Object getItem(int i) { synchronized (lock) { return display.get(i); } }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            ViewHolder vh;
            if (convert == null) {
                LinearLayout ll = new LinearLayout(MainActivity.this);
                ll.setOrientation(LinearLayout.HORIZONTAL);
                ll.setGravity(Gravity.CENTER_VERTICAL);
                ll.setPadding(dp(14), dp(8), dp(12), dp(8));

                ImageView iv = new ImageView(MainActivity.this);
                int sz = dp(40);
                iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

                // container teks (label + package) di tengah
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(12), 0, dp(8), 0);
                col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView tv = new TextView(MainActivity.this);
                tv.setTextSize(15);
                TextView pv = new TextView(MainActivity.this);
                pv.setTextSize(11);
                pv.setTextColor(0xFF888888);
                pv.setPadding(0, dp(2), 0, 0);

                col.addView(tv);
                col.addView(pv);

                Switch sw = new Switch(MainActivity.this);

                ll.addView(iv);
                ll.addView(col);
                ll.addView(sw);

                vh = new ViewHolder();
                vh.icon = iv;
                vh.label = tv;
                vh.pkgText = pv;
                vh.sw = sw;
                ll.setTag(vh);
                convert = ll;
            } else {
                vh = (ViewHolder) convert.getTag();
            }

            final AppEntry entry;
            synchronized (lock) {
                if (position >= display.size()) return convert;
                entry = display.get(position);
            }
            final String pkg = entry.packageName;

            vh.pkg = pkg;
            vh.label.setText(entry.label);
            vh.pkgText.setText(pkg);
            if (entry.icon != null) vh.icon.setImageDrawable(entry.icon);
            else vh.icon.setImageDrawable(null);

            vh.sw.setOnCheckedChangeListener(null);
            vh.sw.setChecked(targets.contains(pkg));
            vh.sw.setOnCheckedChangeListener((btn, isChecked) -> toggleTarget(pkg, isChecked));

            return convert;
        }
    }
}