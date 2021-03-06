package in.clayfish.pyry.utils;

import in.clayfish.pyry.models.Tweet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Static utility-class for helper functions. All the functions are thread-safe.
 *
 * @author shuklaalok7
 * @since 16/01/16
 */
public abstract class AppUtils {
    private static final Logger logger = LogManager.getLogger(AppUtils.class);

    private static final CSVFormat CUSTOM = CSVFormat.DEFAULT.withQuote(null);
    private static ApplicationProperties props;
    private static AtomicLong counter;
    private static boolean initialized = false;

    /**
     * It's necessary to call this method before other methods can be called.
     *
     * @param props ApplicationProperties to initialize this utility with
     * @return {@code true}, if it has initialized successfully, {@code false} otherwise
     * @throws IOException
     */
    public static synchronized boolean initialize(ApplicationProperties props) throws IOException {
        Objects.requireNonNull(props);
        initialized = true;

        AppUtils.props = props;
        counter = new AtomicLong(getLastConversationId());

        return true;
    }

    /**
     * @return An incrementing number to be used as conversationId
     */
    public static synchronized long generateConversationId() {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        return counter.incrementAndGet();
    }

    /**
     * @param file  The CSV file to read
     * @param start The line number to start reading from
     * @param end   The line number to read up to
     * @return List of CSVRecord which are read from the given file
     * @throws IOException
     */
    public static synchronized List<CSVRecord> readCsvFile(final File file, final long start, final long end) throws IOException {
        if (!file.exists() || start < 0 || end < 1) {
            throw new IllegalArgumentException(String.format(
                    "%s should exist, start(%d) should be greater than -1 and end(%d) should be greater than 0", file.getName(), start, end));
        }

        CSVParser csvParser = new CSVParser(new FileReader(file), CUSTOM);

        return StreamSupport.stream(csvParser.spliterator(), false).skip(start).limit(end - start).collect(Collectors.toList());
    }

    /**
     * @param file File to read record from
     * @return The first record from the given file
     * @throws IOException
     */
    public static synchronized CSVRecord readFirstRecord(final File file) throws IOException {
        return readNthRecord(file, 0);
    }

    /**
     * @param file File to read record from
     * @param n    Line number of the record to read
     * @return nth record from the given file
     * @throws IOException
     */
    public static synchronized CSVRecord readNthRecord(final File file, long n) throws IOException {
        List<CSVRecord> result = readCsvFile(file, n, n + 1);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }

        return null;
    }

    /**
     * @param file File to read record from
     * @return The last record from the given file
     * @throws IOException
     */
    public static synchronized CSVRecord readLastRecord(final File file) throws IOException {
        if (file.exists()) {
            long count = getLineCount(file);

            if (count > 0) {
                return readNthRecord(file, count - 1);
            }
        }
        return null;
    }

    /**
     * @param file       File to write into
     * @param objects    objects to write
     * @param <T>        extends {@link Object}
     * @throws IOException
     */
    public static synchronized <T> void appendToCsv(final File file, final List<T> objects) throws IOException {
        writeToCsv(file, objects, true);
    }

    /**
     * @param file      File to write into
     * @param object    object to write
     * @param append    if {@code true}, will append below the last line in the given file
     * @param <T>       extends {@link Object}
     * @throws IOException
     */
    public static synchronized <T> void writeToCsv(final File file, final T object, final boolean append) throws IOException {
        if (!file.exists()) {
            boolean created = file.createNewFile();

            if (!created) {
                throw new FileNotFoundException(file.getName() + " does not exist, nor could be created");
            }
        }
        CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(file, append), CUSTOM);
        if (object instanceof String) {
            logger.debug("Printing " + object);
            csvPrinter.print(object);
            csvPrinter.println();
        } else {
            csvPrinter.printRecord(Collections.singleton(object));
        }

        csvPrinter.flush();
    }

    /**
     * @param file    file to write CSV records in
     * @param objects objects to write in the given file
     * @param append  Whether to append in the given file
     * @param <T>     extend Object
     */
    public static synchronized <T> void writeToCsv(final File file, final List<T> objects, final boolean append) throws IOException {
        if (!file.exists()) {
            boolean created = file.createNewFile();

            if (!created) {
                throw new FileNotFoundException(file.getName() + " does not exist, nor could be created");
            }
        }
        CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(file, append), CUSTOM);

        for (Object object : objects) {
            csvPrinter.printRecord(Collections.singleton(object));
        }

        csvPrinter.flush();
    }

    /**
     * @param step         {@code 1} or {@code 2}
     * @param threadNumber Serial of spawned thread
     * @return newly created file
     * @throws IOException
     */
    public static synchronized File createNewOutputFile(final int step, final int threadNumber) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        if (threadNumber > props.getNumberOfConcurrentThreads()) {
            throw new IllegalStateException(String.format("Thread %d: Threads should be less than maximum number of threads(%d)", threadNumber,
                    props.getNumberOfConcurrentThreads()));
        }

        String prefix = getOutputFilePrefix(step);
        int currentIndex = getCurrentOutputFileIndex(prefix);
        File newOutputFile = new File(
                String.format("%s/%s%d-%d.csv", props.getOutputFolder().getPath(), prefix, threadNumber, currentIndex + 1));
        boolean created = newOutputFile.createNewFile();

        if (!created) {
            throw new IllegalStateException(String.format("Thread %d: Cannot create new output file for %s", threadNumber,
                    getOutputFilePrefix(step)));
        }
        return newOutputFile;
    }

    /**
     * @param step {@code 1} or {@code 2}
     * @return newly created output file
     * @throws IOException
     */
    public static synchronized File createNewOutputFile(final int step) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        String prefix = getOutputFilePrefix(step);
        int currentIndex = getCurrentOutputFileIndex(prefix);
        File newOutputFile = new File(String.format("%s/%s%d.csv", props.getOutputFolder().getPath(), prefix, currentIndex + 1));
        boolean created = newOutputFile.createNewFile();

        if (!created) {
            throw new IllegalStateException("Cannot create new output file for " + getOutputFilePrefix(step));
        }
        return newOutputFile;
    }

    /**
     * @param step         {@code 1} or {@code 2}
     * @param threadNumber The number of thread spawned
     * @return current output file
     */
    public static synchronized File getCurrentOutputFile(final int step, final int threadNumber) {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        if (threadNumber > props.getNumberOfConcurrentThreads()) {
            throw new IllegalStateException(String.format("Thread %d: Threads should be less than maximum number of threads(%d)", threadNumber,
                    props.getNumberOfConcurrentThreads()));
        }

        String prefix = getOutputFilePrefix(step);
        int currentIndex = getCurrentOutputFileIndex(prefix);

        if (currentIndex == 0) {
            currentIndex++;
        }

        return new File(String.format("%s/%s%d-%d.csv", props.getOutputFolder().getPath(), prefix, threadNumber, currentIndex));
    }

    /**
     * @param step {@code 1} or {@code 2}
     * @return get output file which is being currently in use
     */
    public static synchronized File getCurrentOutputFile(final int step) {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        String prefix = getOutputFilePrefix(step);
        int currentIndex = getCurrentOutputFileIndex(prefix);

        if (currentIndex == 0) {
            currentIndex++;
        }

        return new File(String.format("%s/%s%d.csv", props.getOutputFolder().getPath(), prefix, currentIndex));
    }

    /**
     * @param step {@code 1} or {@code 2}
     * @return index of current output file
     */
    public static synchronized int getCurrentOutputFileIndex(final int step) {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }
        return getCurrentOutputFileIndex(getOutputFilePrefix(step));
    }

    /**
     * @param prefix prefix obtained from {@link #getOutputFilePrefix(int)}
     * @return index of current output file
     */
    public static synchronized int getCurrentOutputFileIndex(final String prefix) {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        int maxIndex = 0;
        for (File firstLevelOutputFile : props.getOutputFolder().listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".csv"))) {
            String[] nameParts = firstLevelOutputFile.getName().split(IConstants.MINUS);
            int index = Converter.TO_INT.apply(nameParts[nameParts.length - 1].replace(".csv", IConstants.BLANK));

            if (index > maxIndex) {
                maxIndex = index;
            }
        }

        return maxIndex;
    }

    /**
     * @param step {@code 1} or {@code 2}
     * @return latest tweet id which was fetched
     * @throws IOException
     */
    public static synchronized long getLatestTweetIdFetched(final int step) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        long latestTweetId = Long.MIN_VALUE;
        final String prefix = getOutputFilePrefix(step);
        for (File outputFile : props.getOutputFolder().listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".csv"))) {
            CSVParser csvParser = new CSVParser(new FileReader(outputFile), CUSTOM);
            long maxTweetId = StreamSupport.stream(csvParser.spliterator(), false).mapToLong(csvRecord -> getTweetId(csvRecord, step)).max()
                    .orElse(Long.MIN_VALUE);

            if (maxTweetId > latestTweetId) {
                latestTweetId = maxTweetId;
            }
        }

        return latestTweetId;
    }

    /**
     * @param file The file to count the lines in
     * @return Number of lines in the given CSV file
     * @throws IOException
     */
    public static synchronized long getLineCount(File file) throws IOException {
        Objects.requireNonNull(file);
        if (file.exists()) {
            CSVParser csvParser = new CSVParser(new FileReader(file), CUSTOM);
            return StreamSupport.stream(csvParser.spliterator(), false).count();
        }
        return 0;
    }

    /**
     * @param step {@code 1} or {@code 2}
     * @return Get the oldest tweet id fetched
     * @throws IOException
     */
    public static synchronized long getOldestTweetIdFetched(final int step) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        long oldestTweetId = Long.MAX_VALUE;
        final String prefix = getOutputFilePrefix(step);
        for (File outputFile : props.getOutputFolder().listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".csv"))) {
            CSVParser csvParser = new CSVParser(new FileReader(outputFile), CUSTOM);
            long minTweetId = StreamSupport.stream(csvParser.spliterator(), false).mapToLong(csvRecord -> getTweetId(csvRecord, step)).min()
                    .orElse(Long.MAX_VALUE);

            if (minTweetId < oldestTweetId) {
                oldestTweetId = minTweetId;
            }
        }

        return oldestTweetId;
    }

    /**
     * It looks in the second-level-*-*.csv files for the largest conversationId
     *
     * @return Last used conversationId
     * @throws IOException
     */
    private static synchronized long getLastConversationId() throws IOException {
        if (!initialized) {
            throw new IllegalStateException("AppUtils is not initialized. Please call AppUtils.initialize(props) first");
        }

        long lastConversationId = 0;
        final String prefix = getOutputFilePrefix(2);
        for (File outputFile : props.getOutputFolder().listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".csv"))) {
            CSVParser csvParser = new CSVParser(new FileReader(outputFile), CUSTOM);
            long maxConversationId = StreamSupport.stream(csvParser.spliterator(), false)
                    .map(csvRecord -> StreamSupport.stream(csvRecord.spliterator(), false).reduce((s, s2) -> s + "," + s2).orElse(IConstants.BLANK))
                    .filter(record -> !record.isEmpty()).map(recordString -> new Tweet().fromRecord(recordString)).mapToLong(Tweet::getConversationId).max()
                    .orElse(0);

            if (lastConversationId < maxConversationId) {
                lastConversationId = maxConversationId;
            }
        }

        return lastConversationId;
    }

    /**
     * This implementation is not complete for step 2
     *
     * @param record The record from which the tweetID is extracted
     * @param step   {@code 1} or {@code 2} as per {@link ApplicationProperties#getStep()}
     * @return Extracted tweet id
     */
    private static long getTweetId(final CSVRecord record, final int step) {
        switch (step) {
            case 1:
                return Long.parseLong(record.get(0).trim());

            case 2:
                // Left to implement
                break;

            default:
                throw new IllegalArgumentException("Step should be 1 or 2, found " + step);
        }
        return 0;
    }

    /**
     * @param step The step for which the prefix is required
     * @return The file-prefix to use
     */
    private synchronized static String getOutputFilePrefix(final int step) {
        switch (step) {
            case 1:
                return "first-level-";

            case 2:
                return "second-level-";

            default:
                throw new IllegalArgumentException("Wrong step value: " + step);
        }
    }
}
