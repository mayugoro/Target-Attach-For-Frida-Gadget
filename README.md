# Target Attach For Frida Gadget

Aplikasi Android untuk mengelola daftar **target aplikasi** yang akan dibekukan/ditunda saat Frida (ZygiskFrida gadget) melakukan attach. Tidak perlu lagi salin-tempel command di Termux — cukup toggle dari UI.

## Fungsi

- **Menampilkan** semua aplikasi yang ter-install di HP (hasil download, bukan aplikasi bawaan/system), lengkap dengan **icon asli** tiap app dan package name.
- **Toggle per aplikasi** untuk menandai mana yang menjadi *target* Frida. Aplikasi yang aktif (toggle ON) otomatis ditaruh di paling atas daftar.
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

APK terbaru dapat diunduh dari halaman [Releases](https://github.com/mayugoro/Target-Attach-For-Frida-Gadget/releases).

Atau install langsung lewat Termux:

```bash
curl -sSL -o /sdcard/Download/TargetAttach4Frida.apk <LINK_APK_TERBARU>
su -c 'pm install -r /sdcard/Download/TargetAttach4Frida.apk'
```

## Build dari source

```bash
./build.sh
```

Membutuhkan: JDK 8+, `aapt2`, `r8`/`d8`, `apksigner` (path menyesuaikan di `build.sh`).

## Lisensi

[MIT](LICENSE) © 2026 MAYUGORO