package com.guberan.lucenefx;

import javafx.concurrent.Task;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Multipart;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Index task, index all files in docPath.<br>
 * Use Apache's Tika to convert documents to text.
 */
public class IndexTask extends Task<List<IndexTask.IndexingError>> {
    private static final Logger log = LoggerFactory.getLogger(IndexTask.class);

    private static final long REFRESH_MILLIS = 250;

    private final Directory dir;
    private final Path docPath;
    private final Path indexPath;

    private final IndexWriter writer;
    private final Tika tika;

    private int dirCount;
    private int fCount;
    private int fProcessed;
    private final ArrayList<IndexingError> errorList = new ArrayList<>();
    private long millis;
    private boolean create;

    /**
     * IndexTask
     *
     * @param docPath   path to document directory
     * @param indexPath path to index directory
     * @param indexDir  directory containing the index files
     */
    public IndexTask(Path docPath, Path indexPath, Directory indexDir) throws IOException {
        this.docPath = docPath;
        this.indexPath = indexPath;
        this.dir = indexDir;

        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

        create = !Files.exists(indexPath);

        // Add new documents to an existing index
        iwc.setOpenMode(create ? OpenMode.CREATE : OpenMode.CREATE_OR_APPEND);
        iwc.setRAMBufferSizeMB(256.0);

        writer = new IndexWriter(dir, iwc);

        // using tika facade class
        tika = new Tika();

        // time at last updateProgress() (GUI update)
        millis = 0;
    }

    /**
     * inc file processed count and update Task progress
     */
    protected void incProcessed() {
        fProcessed++;

        // refresh GUI only after a minimum of <REFRESH_MILLIS> milliseconds
        if (System.currentTimeMillis() - millis > REFRESH_MILLIS) {
            updateProgress(fProcessed, fCount);
            NumberFormat fmt = NumberFormat.getIntegerInstance();
            updateMessage(LuceneFx.tr("IndexTask.process", "", fmt.format(fProcessed), fmt.format(fCount),
                    fmt.format(dirCount)));
            millis = System.currentTimeMillis();
        }
    }

    /**
     * call (main Task method)
     */
    @Override
    protected List<IndexingError> call() throws Exception {
        long start = System.currentTimeMillis();

        // index all files
        updateMessage(LuceneFx.tr("IndexTask.examine"));
        Files.walkFileTree(docPath, new CountVisitor());

        NumberFormat fmt = NumberFormat.getIntegerInstance();
        updateMessage(LuceneFx.tr("IndexTask.process", "", 0, fmt.format(fCount), fmt.format(dirCount)));
        Files.walkFileTree(docPath, new IndexFileVisitor());

        updateMessage(LuceneFx.tr("IndexTask.consolidate"));

        Duration d = Duration.ofMillis(System.currentTimeMillis() - start);
        log.info("reindex time {} for '{}'", d, docPath);

        // NOTE: if you want to maximize search performance,
        // you can optionally call forceMerge here. This can be
        // a terribly costly operation, so generally it's only
        // worth it when your index is relatively static
        // (ie you're done adding documents to it)
        writer.forceMerge(1);

        // closer writer
        writer.close();

        // return a list of documents that could not be indexed
        return errorList;
    }

    /**
     * count attachments in email
     *
     * @param file email
     * @return count
     */
    private int countAttachments(Path file) {
        int count = 0;
        try (InputStream input = Files.newInputStream(file)) {
            MimeMessage message = new MimeMessage(null, input);
            Object content = message.getContent();
            if (content instanceof Multipart multipart) {
                count = multipart.getCount() - 1;
            }
        } catch (Exception e) {
            log.debug("Could not count attachments of " + file);
        }
        return count;
    }

    /**
     * indexFile
     *
     * @param file index file
     */
    protected void indexFile(Path file, BasicFileAttributes attrs) {
        try {
            // make a new, empty document
            Document doc = new Document();

            // Add the path of the file as a field named "path". Use a
            // field that is indexed (i.e. searchable), but don't tokenize
            // the field into separate words and don't index term frequency
            // or positional information:
            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);

            // Add the last modified date of the file a field named "modified".
            // Use a LongPoint that is indexed (i.e. efficiently filterable with
            // PointRangeQuery). This indexes to millisecond resolution, which
            // is often too fine. You could instead create a number based on
            // year/month/day/hour/minutes/seconds, down the resolution you require.
            // For example the long value 2011021714 would mean
            // February 17, 2011, 2-3 PM.
            long lastModified = attrs.lastModifiedTime().toMillis();
            doc.add(new SortedNumericDocValuesField("modified", lastModified));
            // doc.add(new NumericDocValuesField("modified", lastModified));
            doc.add(new LongPoint("modified", lastModified));
            doc.add(new StoredField("modified", lastModified));

            // Add the contents of the file to a field named "contents". Specify a Reader,
            // so that the text of the file is tokenized and indexed, but not stored.
            // Note that FileReader expects the file to be in UTF-8 encoding.
            // If that's not the case searching for special characters will fail.
            // doc.add(new TextField("contents", new BufferedReader(new
            // InputStreamReader(stream, StandardCharsets.UTF_8))))
            // doc.add(new TextField("contents", "test", Store.NO)
            doc.add(new TextField("contents", tika.parseToString(file), Field.Store.NO));

            // if e-mail, count attachments
            int attachments = 0;
            if (file.toString().toLowerCase().endsWith(".eml")) {
                attachments = countAttachments(file);
            }
            doc.add(new SortedNumericDocValuesField("attachments", attachments));
            doc.add(new IntPoint("attachments", attachments));
            doc.add(new StoredField("attachments", attachments));

            if (create) { // writer.getConfig().getOpenMode() == OpenMode.CREATE
                // New index, so we just add the document (no old document can be there):
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                writer.updateDocument(new Term("path", file.toString()), doc);
            }

        } catch (Exception e) {

            // document could not be indexed
            log.warn("An error occurred while indexing " + file, e);
            errorList.add(new IndexingError(file, e));
        }
    }

    /**
     * class to report errors
     */
    public static class IndexingError {
        Path file;
        Exception exc;

        public IndexingError(Path f, Exception e) {
            this.file = f;
            this.exc = e;
        }
    }

    /**
     * A {@code FileVisitor} that indexes files
     */
    public class IndexFileVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {

            // if index directory is inside document directory, skip it
            if (indexPath != null && indexPath.equals(dir))
                return FileVisitResult.SKIP_SUBTREE;

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

            if (isCancelled())
                return FileVisitResult.TERMINATE;

            indexFile(file, attrs);
            incProcessed();
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * A {@code FileVisitor} that count each file and directory visited
     */
    public class CountVisitor implements FileVisitor<Path> {

        public CountVisitor() {
            dirCount = 0;
            fCount = 0;
            fProcessed = 0;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {

            // if index directory is inside document directory, skip it
            if (indexPath != null && indexPath.equals(dir))
                return FileVisitResult.SKIP_SUBTREE;

            dirCount++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            fCount++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }

}
