# 🧱 DESAIN VERSIONING (v1 → v10)

## ✅ V1 (Initial Release — Foundation)

**Isi:**

* `projects`
* `files`
* `file_metadata`
* `conversations`
* `cron_jobs`
* `files_fts`

**Rules:**

* Semua column sudah dipikirkan nullable/default
* Hindari future breaking change

---

## 🔼 V2 (Non-breaking additive)

**Contoh:**

* Tambah column:

  * `projects.description TEXT`
  * `files.mime_type TEXT`

**Migration:**

```sql
ALTER TABLE projects ADD COLUMN description TEXT;
ALTER TABLE files ADD COLUMN mime_type TEXT;
```

👉 AMAN (tidak reset)

---

## 🔼 V3 (Index & performance)

**Contoh:**

* Tambah index:

```sql
CREATE INDEX index_files_mime_type ON files(mime_type);
```

👉 Tidak ubah data → aman

---

## 🔼 V4 (FTS improvement ⚠️ tricky)

**Contoh:**

* Tambah field ke FTS (misal: `extension`)

**Migration strategy:**

```sql
DROP TABLE IF EXISTS files_fts;

CREATE VIRTUAL TABLE files_fts USING FTS4(
    file_name,
    relative_path,
    extension,
    content='files'
);

INSERT INTO files_fts(rowid, file_name, relative_path, extension)
SELECT id, file_name, relative_path, extension FROM files;
```

👉 **WAJIB reindex (tidak bisa ALTER)**

---

## 🔼 V5 (Relational improvement)

**Contoh:**

* Tambah table baru:

  * `tags`
  * `file_tags`

👉 Best practice:

* Jangan ubah table lama
* Tambah table baru

---

## 🔼 V6 (Column rename ❗ dangerous)

Room tidak support rename langsung → pakai copy table

**Strategy:**

```sql
CREATE TABLE files_new (...);

INSERT INTO files_new (...)
SELECT ... FROM files;

DROP TABLE files;

ALTER TABLE files_new RENAME TO files;
```

👉 Ini yang sering bikin dev gagal

---

## 🔼 V7 (Data normalization)

Contoh:

* Pisah `messagesJson` → table `messages`

👉 Migration:

* Extract JSON
* Insert ke table baru

---

## 🔼 V8 (Constraint change)

Contoh:

* Tambah UNIQUE constraint

👉 Harus recreate table (copy pattern lagi)

---

## 🔼 V9 (Cleanup / remove column)

SQLite:
❌ tidak bisa drop column

👉 Solusi:

* recreate table tanpa column lama

---

## 🔼 V10 (Optimization + reindex)

* Rebuild index
* Vacuum DB
* Rebuild FTS

---

# 🔥 TEMPLATE MIGRATION (WAJIB PUNYA)

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            // SQL disini

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
```

---

# ⚠️ RULE KRITIS (INI YANG NYELAMETIN DB KAMU)

## ❌ JANGAN PERNAH

* pakai `fallbackToDestructiveMigration()` di production
* ubah entity tanpa migration
* ubah nama column langsung

---

## ✅ WAJIB

* Simpan semua migration (jangan dihapus)
* Test upgrade dari versi lama
* Gunakan default value saat tambah column

---

# 🧪 TEST STRATEGY (PRO LEVEL)

Simulasi real:

1. Install app v1
2. Insert data
3. Upgrade ke v10
4. Verify:

   * data utuh
   * FTS jalan
   * foreign key valid

---

# 🧰 STRUKTUR FOLDER (SCALABLE)

```
data/
 ├── local/
 │    ├── entity/
 │    ├── dao/
 │    ├── db/
 │    │    ├── AppDatabase.kt
 │    │    └── migrations/
 │    │         ├── Migration1_2.kt
 │    │         ├── Migration2_3.kt
 │    │         └── ...
```

---

# 🧠 STRATEGI KHUSUS FTS (PENTING BANGET)

Karena kamu pakai:

```sql
files_fts (content = files)
```

👉 RULE:

* Jangan anggap FTS = normal table
* Setiap perubahan schema → REBUILD

Checklist:

* DROP FTS
* CREATE ulang
* INSERT dari `files`

---

# 🚀 BONUS: FAIL-SAFE STRATEGY (ANTI DATA LOSS)

Kalau takut migration gagal:

## 1. Backup sebelum migration

```kotlin
context.getDatabasePath("app_db").copyTo(...)
```

## 2. Restore jika gagal

---

# 🔥 KESIMPULAN

Strategi kamu harus:

> ✅ Additive first
> ✅ Recreate only when necessary
> ✅ FTS selalu rebuild
> ❌ Tidak pernah destructive

---

