# Target Attach For Frida Gadget

<img src="icon.png" width="96" align="left" style="margin-right:16px">

**Freeze · Attach · Hook** — kelola target Frida Gadget dari UI Android.

Aplikasi Android untuk mengelola daftar **target aplikasi** yang akan **dibekukan (freeze) saat diluncurkan**, agar kamu bisa **attach Frida Gadget** (via **ZygiskFrida gadget**, **bukan Frida server biasa**). Tidak perlu lagi salin-tempel command di Termux — cukup toggle dari UI.

<br clear="all">

> ⚠️ **Catatan penting:** Aplikasi ini bekerja dengan **Frida Gadget**, bukan **frida-server**.
> - **Frida Gadget** = library `.so` yang di-inject langsung ke dalam proses aplikasi target secara *embedded*. Aplikasi target ditahan (gated) pada saat startup, lalu kita *attach* ke proses tersebut.
> - **Frida Server** = daemon yang berjalan di HP dan melakukan *spawn* proses dari luar.
> Aplikasi ini **khusus** untuk alur Frida Gadget (injection via ZygiskFrida + `libgadget.so` + mode `child_gating: freeze`).

## Fungsi

Aplikasi ini **membekukan (freeze/menahan)** app target sebelum masuk ke `main()`-nya, sehingga proses diam di tempat dan menunggu Frida Gadget **attach** dari sisi client (PC/Termux). Ini memudahkan hooking karena kita bisa pasang script *sebelum* logika aplikasi berjalan.

- **Menampilkan** semua aplikasi yang ter-install di HP (hasil download, bukan aplikasi bawaan/system), lengkap dengan **icon asli** tiap app dan package name.
- **Toggle per aplikasi** untuk menandai mana yang menjadi *target* Frida Gadget. Aplikasi yang aktif (toggle ON) otomatis ditaruh di paling atas daftar.
- Saat toggle digeser, config ditulis ke `/data/local/tmp/re.zyg.fri/config.json` (format ZygiskFrida: `targets[]` + `child_gating{freeze}` + `libgadget.so`), sehingga app yang dipilih akan **membeku saat diluncurkan** dan menunggu Frida attach.
- **Tombol Restart** di pojok kanan atas untuk me-reboot HP agar config ZygiskFrida diterapkan ulang dengan bersih.
- **Aplikasi ini mengecualikan dirinya sendiri** dari daftar, sehingga tidak mungkin tidak sengaja membekukan dirinya sendiri.

## Kebutuhan Akses Root

Aplikasi ini **membutuhkan akses root (SuperUser)**. Tanpa akses root, app tidak dapat membaca/menulis config di `/data/local/tmp/`.

Saat pertama dibuka, app mendeteksi apakah akses root sudah diberikan. Jika belum, muncul peringatan:

> **Berikan kami akses ke root SuperUser agar aplikasi berjalan normal**
>
> [Berikan akses root]

Tombol tersebut akan mengarahkan ke **Magisk / KernelSU / APatch** untuk memberikan akses root.

## Download

APK terbaru (v1.0):

**[⬇️ TargetAttach4Frida-v1.0.apk](https://github.com/mayugoro/Target-Attach-For-Frida-Gadget/releases/download/v1.0/TargetAttach4Frida-v1.0.apk)**

Atau install langsung lewat Termux:

```bash
curl -sSL -o /sdcard/Download/TargetAttach4Frida.apk https://github.com/mayugoro/Target-Attach-For-Frida-Gadget/releases/download/v1.0/TargetAttach4Frida-v1.0.apk
su -c 'pm install -r /sdcard/Download/TargetAttach4Frida.apk'
```

## Build dari source

```bash
./build.sh
```

Membutuhkan: JDK 8+, `aapt2`, `r8`/`d8`, `apksigner` (path menyesuaikan di `build.sh`).

## Lisensi

[MIT](LICENSE) © 2026 MAYUGORO