# Rencana Peningkatan Agent Reliability, Resilience, dan Fitur Khas AI Agent
**Amaya Intelligence Architecture**

Dokumen ini merangkum status implementasi serta *roadmap* penambahan fitur untuk memperkuat keandalan (*reliability*), ketahanan (*resilience*), dan kemampuan khas AI Agent pada Amaya.

---

## 1. Ringkasan Status & Klasifikasi Fitur

### A. Fitur Agent Reliability (Keandalan)

| Fitur | Status | Catatan / Rencana Implementasi |
| :--- | :---: | :--- |
| **1. Fault detection & error handling** | `[implement]` | Klasifikasi error multi-kategori (`classifyError`), deteksi respon kosong, penanganan status HTTP/SSE terputus, dan pesan error ramah pengguna. |
| **2. Retry & backoff** | `[implement]` | Exponential backoff ber-jitter acak (1s, 2s, 4s + 100-500ms) dan delay khusus Rate Limit (5s-10s). |
| **3. Idempotensi** | `[partial]` | Edit file sudah menggunakan pencocokan blok unik (`targetContent`), namun eksekusi command shell di terminal masih bergantung pada skrip. |
| **4. Timeout & deadline** | `[implement]` | Watchdog inactivity timer (60 detik) untuk memutus dan memulihkan stream menggantung tanpa response delta. |
| **5. Validasi input/output** | `[implement]` | Skema validasi ketat via `AiToolArgumentValidator` sebelum tool dijalankan untuk mencegah kegagalan runtime. |
| **6. Determinisme & reproducibility** | `[partial]` | Konfigurasi temperatur & output tokens sudah tersedia per model; *seed locking* eksplisit per sesi dapat ditambahkan. |
| **7. Observability** | `[implement]` | Logging menyeluruh via `StreamDebugLog`, pencatatan pemanggilan tool di `TaskLedger`, dan tracking konsumsi token input/output. |
| **8. Monitoring & alerting** | `[belum ada namun bagus ditambahkan dengan penyesuaian]` | Disesuaikan untuk arsitektur client/mobile: membuat **Diagnostics & Health Dashboard** lokal (latensi model, persentase keberhasilan tool, peringatan kuota). |
| **9. Testing & evaluation** | `[partial]` | Unit test ketahanan sudah ada (`AiAgentResilienceTest`), namun automated benchmark *LLM eval* bawaan belum ada. |
| **10. Versioning & rollback** | `[partial]` | Riwayat perubahan file di workspace tersedia, namun belum ada rollback 1-klik untuk snapshot instruksi persona atau toolset agent. |
| **11. Konsistensi memori & state** | `[implement]` | `ActiveContextLedgerStore`, `SessionMemoryRepository`, dan database Room dengan transaksi ACID lokal. |
| **12. Kontrak tool/API yang jelas** | `[implement]` | Definisi parameter via JSON Schema, deskripsi fungsi yang kaya, serta tipe error yang terdefinisi. |

---

### B. Fitur Agent Resilience (Ketahanan)

| Fitur | Status | Catatan / Rencana Implementasi |
| :--- | :---: | :--- |
| **1. Self-healing** | `[implement]` | Loop verifikasi otomatis dan pantulan error validasi kembali ke LLM agar agent mengoreksi argumennya secara mandiri. |
| **2. Redundancy & failover** | `[implement]` | Fungsi `findFallbackCandidate` otomatis mengalihkan tugas ke model cadangan/provider lain yang aktif jika model utama gagal. |
| **3. Circuit breaker** | `[implement]` | Memutus pemanggilan tool identik yang berulang (>= 3 kali berturut-turut atau berfrekuensi tinggi tanpa perubahan data) guna mencegah *infinite loop*. |
| **4. Bulkhead isolation** | `[partial]` | Subagent berjalan di channel terisolasi dan background task terpisah, namun proses aplikasi Android masih dalam satu sandbox. |
| **5. Graceful degradation** | `[implement]` | Peringatan otomatis jika model tidak mendukung input gambar/tool; auto-compacting jika context window mendekati limit. |
| **6. Fallback strategy** | `[implement]` | Strategi bertingkat: Model Aktif -> Model Cadangan Koneksi Sama -> Provider Cadangan Aktif -> Pesan Panduan Pengguna. |
| **7. Checkpointing & recovery** | `[implement]` | Proyeksi turn tersimpan secara real-time ke Room DB; turn yang terputus ditandai rapi dan dapat dilanjutkan. |
| **8. Adaptive replanning** | `[implement]` | Integrasi `TaskPlan` dan `TaskLedger` untuk merencanakan ulang langkah saat tool menghasilkan output error. |
| **9. Rate limiting & backpressure** | `[partial]` | Backpressure aliran ditangani Kotlin Flow & Channel, serta penundaan dinamis saat 429; perlu token bucket lokal proaktif. |
| **10. Resource isolation & quota** | `[partial]` | Token input/output dibatasi ketat (`ContextBudgetManager`); resource CPU/RAM dikendalikan oleh OS Android. |
| **11. Chaos engineering & fault injection** | `[belum ada - tidak cocok]` | *Tidak cocok* untuk aplikasi mobile pengguna akhir karena dapat merusak stabilitas perangkat dan menghabiskan kuota pengguna. |
| **12. Drift detection & adaptation** | `[belum ada dan bagusnya ditambahkan]` | Mendeteksi jika file atau dependensi project berubah drastis di luar sesi chat, lalu memperbarui ringkasan konteks arsitektur. |
| **13. Human-in-the-loop escalation** | `[implement]` | Konfirmasi aksi destruktif (`onConfirmation`), dialog klarifikasi, dan tombol stop generasi di UI. |
| **14. Multi-agent consensus/arbitration** | `[partial]` | Pemanggilan subagent sudah didukung (`InvokeSubagentsTool`), namun mekanisme voting antar beberapa model belum diterapkan. |
| **15. Security & sandboxing** | `[partial]` | Validasi path traversal workspace; eksekusi terminal dibatasi oleh sandbox Linux/Android. |

---

### C. Fitur Khas AI Agent

| Fitur | Status | Catatan / Rencana Implementasi |
| :--- | :---: | :--- |
| **1. Anti-hallucination** | `[partial]` | Grounding berbasis workspace sudah ada (grep, read_file, file index); perlu penambahan badge sitasi visual (*citation chips*) di UI. |
| **2. Manajemen konteks** | `[implement]` | Summarization otomatis, sliding context window, memory pruning, dan context budgeting. |
| **3. Orkestrasi tool** | `[implement]` | Multi-step agentic loop, retry terintegrasi, fallback, dan validasi dinamis. |
| **4. Safety & alignment** | `[implement]` | Guardrail instruksi sistem, proteksi path luar workspace, dan konfirmasi eksekusi sensitif. |
| **5. Evaluasi berkelanjutan** | `[belum ada dan bagusnya ditambahkan]` | Tombol jempol suka/tidak suka (feedback loop) di bubble chat yang otomatis memperbarui aturan `AgentMemory`. |

---

## 2. Roadmap Rencana Eksekusi Bertahap

### Fase 1: Feedback Loop & Penguatan Anti-Hallucination (Prioritas Utama)
1. **Feedback Loop ke Agent Memory**:
   - Menambahkan tombol aksi 👍 / 👎 pada pesan asisten.
   - Saat pengguna memberi 👎 atau komentar koreksi, buat entri memori baru bertipe `USER_PREFERENCE` atau `CORRECTION_RULE` di `AgentMemoryRepository`.
2. **Grounding & Visual Citation Chips**:
   - Menangkap daftar file yang dibaca oleh tool `read_file` atau `grep` dalam turn bersangkutan.
   - Menampilkan *chip* interaktif di bawah pesan respons: `"📄 Berdasarkan AiAgentLoop.kt & AiRepository.kt"`.

### Fase 2: Diagnostics Dashboard & Kontrol Presisi (Prioritas Menengah)
3. **Local Diagnostics & Health Panel**:
   - Menampilkan latensi respons terakhir per provider (ms).
   - Menampilkan status keberhasilan fallback dan status circuit breaker.
4. **Preset Temperatur per Chat**:
   - Menyediakan pilihan mode cepat: *Strict Coding* (0.0), *Balanced* (0.4), *Brainstorming* (0.7).

### Fase 3: Drift Detection & Sinkronisasi Konteks
5. **Project Drift Detector**:
   - Menghitung checksum berkas kunci (`build.gradle.kts`, `package.json`, dll.).
   - Jika terdeteksi perubahan dari luar, trigger auto-refresh pada `ActiveContextLedger`.
