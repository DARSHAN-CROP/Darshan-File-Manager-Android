package com.darshan.filemanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java sorting engine (no Android imports) so it can be unit-tested on a normal JVM.
 * Rules: never delete, never overwrite; skip folders, hidden files and unfinished downloads.
 */
public final class Sorter {

    public static final int MODE_EXTENSION = 1;
    public static final int MODE_TYPE = 2;
    /** Year/Month folders by modified date, e.g. 2026/11-November. */
    public static final int MODE_DATE = 3;
    /** Only custom rules (used by Auto mode when type/extension sort is not ticked). */
    public static final int MODE_RULES_ONLY = 0;

    public static final String NO_EXTENSION = "NO_EXTENSION";
    public static final String OTHERS = "Others";

    /** Files changed within this window are treated as still downloading. */
    public static final long DEFAULT_MIN_AGE_MS = 10_000L;

    private static final Map<String, String> TYPE_OF = new HashMap<>();
    /** Category folder names, in display order. */
    public static final List<String> CATEGORIES = new ArrayList<>();

    private static void cat(String folder, String exts) {
        CATEGORIES.add(folder);
        for (String e : exts.trim().split("\\s+")) TYPE_OF.put(e, folder);
    }

    static {
        cat("Images", "jpg jpeg jpe jfif jif pjpeg pjp png apng gif bmp dib webp heic heif hif avif jxl svg svgz ico icns cur tif tiff raw cr2 cr3 crw nef nrw arw srf sr2 dng orf rw2 raf pef srw x3f 3fr erf kdc mrw psd psb ai eps tga pcx ppm pgm pbm pnm hdr exr dds jp2 j2k jpf jpx wbmp emf wmf xcf kra ora");
        cat("Videos", "mp4 m4v mkv avi mov qt wmv asf flv f4v swf webm 3gp 3g2 3gpp ts mts m2ts m2t mpg mpeg mpe mpv m2v vob ogv rm rmvb divx xvid dv mxf yuv h264 h265 hevc insv lrv");
        cat("Music", "mp3 wav wave aac flac ogg oga opus m4a m4b m4r wma amr awb mid midi kar aiff aif aifc alac ape wv mka 3ga caf ac3 dts mp2 mpa ra au snd voc gsm spx xm mod it s3m cda");
        cat("PDF", "pdf");
        cat("Documents", "doc docx docm dot dotx dotm odt ott rtf txt text wpd wps pages md markdown rst tex log nfo xls xlsx xlsm xlsb xlt xltx ods ots csv tsv numbers ppt pptx pptm pot potx pps ppsx odp otp key xps oxps one onetoc2 msg eml emlx mht mhtml vsd vsdx pub gdoc gsheet gslides hwp");
        cat("Ebooks", "epub mobi azw azw3 kf8 kfx fb2 djvu djv cbz cbr cb7 lit lrf ibooks");
        cat("Archives", "zip zipx rar 7z tar gz tgz bz2 tbz tbz2 xz txz lz lzma lz4 zst tzst z cab arj ace lzh lha iso img dmg vhd vmdk wim sit sitx cpio 001 002 pak");
        cat("Apps", "apk xapk apks apkm aab obb exe msi msix msixbundle appx appxbundle bat cmd com scr jar vbs ps1 reg deb rpm appimage run bin snap flatpakref ipa pkg mpkg gadget");
        cat("Code", "py pyw pyc ipynb c h cpp cc cxx hpp hh hxx cs java class kt kts dart js mjs cjs tsx jsx vue svelte html htm xhtml css scss sass less php phtml rb go rs swift m mm sql db sqlite sqlite3 json jsonc xml xsd xsl yml yaml toml ini cfg conf env properties gradle sh bash zsh fish ino lua pl pm r rmd scala groovy asm s v sv vhdl f f90 pas vb vba fs erl ex exs hs clj lisp ml jl nim zig cmake makefile mk dockerfile patch diff");
        cat("Fonts", "ttf otf woff woff2 eot fon fnt pfb pfm ttc");
        cat("Design", "xd fig sketch cdr indd idml afdesign afphoto blend obj fbx stl 3ds max dwg dxf skp c4d ma mb dae gltf glb ply 3mf step stp iges igs sldprt");
        cat("Contacts_Calendar", "vcf vcard ics ical ifb");
        cat("Torrents", "torrent");
        CATEGORIES.add(OTHERS);
    }

    public static final class Result {
        public int moved;
        public int skipped;
        public int errors;
        public int byRule;
        public final List<String> lines = new ArrayList<>();
        public String summary() {
            String s = moved + (moved == 1 ? " file moved" : " files moved");
            if (errors > 0) s += ", " + errors + (errors == 1 ? " error" : " errors");
            return s;
        }
    }

    private Sorter() {}

    /** Extension without the dot, or null when the name has no usable extension. */
    public static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;   // ".gitignore", "file."
        String ext = name.substring(dot + 1);
        if (ext.length() > 12) return null;
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') return null; // "John.Smith resume"
        }
        return ext;
    }

    public static String typeFolderFor(String name) {
        String ext = extensionOf(name);
        if (ext == null) return OTHERS;
        String f = TYPE_OF.get(ext.toLowerCase(Locale.ROOT));
        return f == null ? OTHERS : f;
    }

    /** Two-part extensions kept together, so backup.tar.gz goes to TAR.GZ (not GZ). */
    static final String[] COMPOUND = {".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst", ".tar.lz", ".tar.lzma", ".tar.z"};

    public static String extFolderFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String c : COMPOUND)
            if (lower.endsWith(c) && lower.length() > c.length()) return c.substring(1).toUpperCase(Locale.ROOT);
        String ext = extensionOf(name);
        return ext == null ? NO_EXTENSION : ext.toUpperCase(Locale.ROOT);
    }

    /** Number of extensions Sort by Type knows (everything else goes to Others). */
    public static int knownExtensionCount() { return TYPE_OF.size(); }

    /** Hidden files, unfinished downloads, temp/lock files are never touched. */
    public static boolean isSkippedName(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        if (n.startsWith(".")) return true;                 // hidden, .pending-*, .trashed-*, .nomedia
        if (n.startsWith("~$")) return true;                // Office lock files
        if (l.equals("desktop.ini") || l.equals("thumbs.db")) return true;
        String[] partial = {".crdownload", ".tmp", ".temp", ".part", ".partial", ".download", ".opdownload",
                ".downloading", ".dlpart", ".!ut", ".bc!", ".aria2"};
        for (String p : partial) if (l.endsWith(p)) return true;
        return false;
    }

    public static String todayFolderName(Date d) {
        return new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(d);
    }

    /** Creates DD-MM-YYYY inside dir. Returns the folder; created==false if it already existed. */
    public static File makeDateFolder(File dir, Date d, boolean[] created) {
        File f = new File(dir, todayFolderName(d));
        boolean existed = f.isDirectory();
        if (!existed && !f.mkdirs()) return null;
        if (created != null && created.length > 0) created[0] = !existed;
        return f;
    }

    /** dir/name, or dir/name (1).ext, (2)... if taken. */
    public static File uniqueDest(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int k = 1; k < 100000; k++) {
            f = new File(dir, base + " (" + k + ")" + ext);
            if (!f.exists()) return f;
        }
        return null;
    }

    /** Moves without ever overwriting. Same-volume rename first, copy+verify+delete as fallback. */
    static boolean moveFile(File src, File dst) {
        if (dst.exists()) return false;
        if (src.renameTo(dst)) return true;
        long len = src.length();
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[256 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } catch (IOException e) {
            dst.delete();
            return false;
        }
        if (dst.length() != len) { dst.delete(); return false; }
        if (!src.delete()) { dst.delete(); return false; }   // keep exactly one copy
        return true;
    }

    /** "2026/11-November" (year / month number-month name) from a file's modified time. */
    public static String dateFolderFor(long modifiedMs) {
        Date d = new Date(modifiedMs);
        return new SimpleDateFormat("yyyy", Locale.US).format(d) + "/"
                + new SimpleDateFormat("MM-MMMM", Locale.US).format(d);
    }

    public static Result sort(File dir, int mode, File undoLog, long minAgeMs, long nowMs) {
        return sort(dir, mode, null, undoLog, minAgeMs, nowMs);
    }

    /**
     * Sorts the files directly inside dir. Custom rules are checked first, then the mode
     * (Type, Extension, Date Year/Month, or RULES_ONLY = only files a rule matches move).
     * The undo log is written only when something moved, so the previous undo stays available after an empty run.
     */
    public static Result sort(File dir, int mode, List<Rules.Rule> rules, File undoLog, long minAgeMs, long nowMs) {
        Result r = new Result();
        List<String> log = new ArrayList<>();
        sortInto(dir, mode, rules, minAgeMs, nowMs, r, log);
        saveUndo(undoLog, log, r);
        return r;
    }

    /** Same as sort() but appends the undo entries to log (so several steps can share one Undo). */
    public static void sortInto(File dir, int mode, List<Rules.Rule> rules, long minAgeMs, long nowMs, Result r, List<String> log) {
        File[] files = dir.listFiles();
        if (files == null) {
            r.errors++;
            r.lines.add("Cannot read folder (permission?): " + dir.getAbsolutePath());
            return;
        }
        Arrays.sort(files);
        for (File f : files) {
            if (!f.isFile()) continue;                          // folders (incl. our own) stay
            String name = f.getName();
            if (isSkippedName(name)) { r.skipped++; continue; }
            if (minAgeMs > 0 && nowMs - f.lastModified() < minAgeMs) {
                r.skipped++;
                r.lines.add("Skipped (still downloading?): " + name);
                continue;
            }
            String folder = Rules.folderFor(name, rules);
            boolean byRule = folder != null;
            if (folder == null) {
                if (mode == MODE_EXTENSION) folder = extFolderFor(name);
                else if (mode == MODE_TYPE) folder = typeFolderFor(name);
                else if (mode == MODE_DATE) folder = dateFolderFor(f.lastModified());
                else continue;                                  // rules only: no rule, file stays
            }
            if (moveInto(dir, folder, f, r, log)) {
                if (byRule) r.byRule++;
            }
        }
    }

    /** Creates dir/rel (one or more levels), logging each created level for Undo. Null on failure. */
    static File ensureDir(File dir, String rel, Result r, List<String> log) {
        File cur = dir;
        for (String part : rel.split("/")) {
            if (part.isEmpty()) continue;
            cur = new File(cur, part);
            if (cur.exists() && !cur.isDirectory()) {
                r.errors++;
                r.lines.add("Error: a file named " + part + " blocks the folder");
                return null;
            }
            if (!cur.exists()) {
                if (!cur.mkdirs()) { r.errors++; r.lines.add("Error: cannot create " + rel); return null; }
                log.add("D\t" + cur.getAbsolutePath());
            }
        }
        return cur;
    }

    /** Moves f into dir/rel without overwriting, recording it for Undo. */
    static boolean moveInto(File dir, String rel, File f, Result r, List<String> log) {
        File target = ensureDir(dir, rel, r, log);
        if (target == null) return false;
        String name = f.getName();
        File dst = uniqueDest(target, name);
        if (dst == null || !moveFile(f, dst)) {
            r.errors++;
            r.lines.add("Error moving " + name);
            return false;
        }
        r.moved++;
        log.add("M\t" + dst.getAbsolutePath() + "\t" + f.getAbsolutePath());
        r.lines.add(name + "  \u2192  " + rel + (dst.getName().equals(name) ? "" : "  (as " + dst.getName() + ")"));
        return true;
    }

    /** Writes the undo log when the run changed something (keeps the previous Undo after an empty run). */
    static void saveUndo(File undoLog, List<String> log, Result r) {
        boolean any = false;
        for (String l : log) if (l.startsWith("M\t")) { any = true; break; }
        if (!any || undoLog == null) return;
        try { writeLines(undoLog, log); }
        catch (IOException e) { r.lines.add("Warning: undo log not saved"); }
    }

    public static boolean hasUndo(File undoLog) {
        return undoLog != null && undoLog.isFile() && undoLog.length() > 0;
    }

    /** Moves the last run's files back (never overwriting) and removes folders that run created, if empty. */
    public static Result undo(File undoLog) {
        Result r = new Result();
        if (!hasUndo(undoLog)) { r.lines.add("Nothing to undo"); return r; }
        List<String> lines;
        try { lines = readLines(undoLog); }
        catch (IOException e) { r.errors++; r.lines.add("Cannot read undo log"); return r; }
        List<String> dirs = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String[] p = lines.get(i).split("\t");
            if (p.length == 2 && p[0].equals("D")) { dirs.add(p[1]); continue; }
            if (p.length != 3 || !p[0].equals("M")) continue;
            File now = new File(p[1]), orig = new File(p[2]);
            if (!now.isFile()) { r.errors++; r.lines.add("Missing (moved or deleted?): " + now.getName()); continue; }
            File back = orig.exists() ? uniqueDest(orig.getParentFile(), orig.getName()) : orig;
            if (back == null || !moveFile(now, back)) { r.errors++; r.lines.add("Error restoring " + now.getName()); continue; }
            r.moved++;
            r.lines.add("\u21A9 " + back.getName());
        }
        for (String d : dirs) {
            File f = new File(d);
            String[] left = f.list();
            if (f.isDirectory() && left != null && left.length == 0) f.delete();
        }
        if (r.errors == 0) undoLog.delete();
        else {
            // keep only what could not be restored, so a second Undo can retry
            List<String> keep = new ArrayList<>();
            for (String l : lines) {
                String[] p = l.split("\t");
                if (p.length == 3 && new File(p[1]).isFile()) keep.add(l);
            }
            try { if (keep.isEmpty()) undoLog.delete(); else writeLines(undoLog, keep); } catch (IOException ignored) {}
        }
        return r;
    }

    static void writeLines(File f, List<String> lines) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            for (String l : lines) { w.write(l); w.write('\n'); }
        }
    }

    static List<String> readLines(File f) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String l;
            while ((l = br.readLine()) != null) if (!l.isEmpty()) out.add(l);
        }
        return out;
    }
}
