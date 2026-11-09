/*
 * Test.java - single-file test runner for the Java practice problems.
 *
 *   java Test.java              run every problem that has a tests/ folder
 *   java Test.java 01-01-01     run one problem (Q_01_01_01, the folder name, also works)
 *   java Test.java --no-color   (add to either of the above if the terminal shows odd characters)
 *
 * Needs a JDK (Java 11 or newer). Nothing else: no javac on the PATH, no Python, no build tool.
 * Run it from the repository root (the folder that contains this file and tests/).
 *
 * How it works, per problem UU-CC-QQ:
 *   1. compiles every .java file in Unit_UU/Chapter_CC/Q_UU_CC_QQ/. Main.java must declare the package
 *      Unit_UU.Chapter_CC.Q_UU_CC_QQ (the folder path), which is what lets VS Code's Run button tell the
 *      problems apart.
 *   2. runs that package's Main once per tests/UU-CC-QQ/*.in file, feeding the file to standard input
 *   3. compares standard output with the matching .out file
 *
 * Tests are grouped into batches by file name: sample-01 -> batch "sample", 1-01 -> batch "1", and so on.
 * "sample" runs first and aborts everything on failure; a failure in a numbered batch skips the rest
 * of that batch only.
 */

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {

    // ------------------------------------------------------------------------------ settings

    /** Per-test time limit in seconds. A problem can override it with tests/UU-CC-QQ/timeout.txt. */
    static final double DEFAULT_TIMEOUT_SECONDS = 2;
    static final double MIN_TIMEOUT_SECONDS = 1;
    static final double MAX_TIMEOUT_SECONDS = 10;

    /** Relative tolerance used when comparing decimal numbers. */
    static final double REL_TOLERANCE = 1e-9;

    /** Safety limits so a runaway program cannot flood memory or the screen. */
    static final int MAX_CAPTURED_BYTES = 1_000_000;
    static final int MAX_SHOWN_CHARS = 2_000;

    /** While this text is still in Main.java, "run all" treats the problem as not started. */
    static final String STARTER_MARKER = "TODO: write your solution";

    /**
     * Flags for the JVM that runs the student's program. The locale is pinned so that output such as
     * String.format("%.2f", x) is "3.14" on every computer, never "3,14".
     */
    static final List<String> JVM_ARGS = Arrays.asList(
            "-Xmx512m",
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-Duser.language=en", "-Duser.country=US");

    // ------------------------------------------------------------------------------ colours

    static String GREEN = "", RED = "", YELLOW = "", RESET = "";

    static void enableColour() {
        GREEN = "\033[92m";
        RED = "\033[91m";
        YELLOW = "\033[93m";
        RESET = "\033[0m";
    }

    /** Colour by default where it is known to work; --color / --no-color override this. */
    static boolean terminalProbablySupportsColour() {
        if (System.getenv("NO_COLOR") != null || "dumb".equals(System.getenv("TERM"))) {
            return false;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (!windows) {
            return true;
        }
        // Windows Terminal, VS Code's terminal, ConEmu etc. understand colour codes; old consoles do not.
        return System.getenv("WT_SESSION") != null || System.getenv("TERM_PROGRAM") != null
                || System.getenv("ANSICON") != null || "ON".equals(System.getenv("ConEmuANSI"));
    }

    static void say(String s) {
        System.out.println(s);
    }

    static void sayColoured(String s, String colour) {
        System.out.println(colour + s + RESET);
    }

    // ------------------------------------------------------------------------------ comparing output

    static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    static boolean tokensMatch(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        boolean expInt = INTEGER.matcher(expected).matches();
        boolean actInt = INTEGER.matcher(actual).matches();
        if (expInt && actInt) {
            // whole numbers are compared exactly, however large (a long overflow must not slip through)
            return new BigInteger(expected.startsWith("+") ? expected.substring(1) : expected)
                    .equals(new BigInteger(actual.startsWith("+") ? actual.substring(1) : actual));
        }
        if (NUMBER.matcher(expected).matches() && NUMBER.matcher(actual).matches()) {
            double e = Double.parseDouble(expected);
            double a = Double.parseDouble(actual);
            return e == a || Math.abs(e - a) <= REL_TOLERANCE * Math.max(Math.abs(e), Math.abs(a));
        }
        return false;
    }

    static String[] tokens(String line) {
        String t = line.strip();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }

    /**
     * Same rules as the Python runner: leading/trailing blank space is ignored, lines are compared in
     * order, and each line is compared token by token (so extra spaces between tokens do not matter).
     * Decimal numbers match within a tiny relative tolerance; whole numbers must match exactly.
     */
    static boolean compareOutputs(String expected, String actual) {
        List<String> expLines = expected.strip().lines().collect(Collectors.toList());
        List<String> actLines = actual.strip().lines().collect(Collectors.toList());
        if (expLines.size() != actLines.size()) {
            return false;
        }
        for (int i = 0; i < expLines.size(); i++) {
            String[] e = tokens(expLines.get(i));
            String[] a = tokens(actLines.get(i));
            if (e.length != a.length) {
                return false;
            }
            for (int j = 0; j < e.length; j++) {
                if (!tokensMatch(e[j], a[j])) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------ compiling

    static final class Compiled {
        final Path classDir;
        final String mainClass;
        final List<String> errors;

        Compiled(Path classDir, String mainClass, List<String> errors) {
            this.classDir = classDir;
            this.mainClass = mainClass;
            this.errors = errors;
        }

        boolean ok() {
            return errors.isEmpty();
        }
    }

    static Path tempRoot;

    // ------------------------------------------------------------------------------ layout

    /** "01-02-03" -> Unit_01/Chapter_02/Q_01_02_03 (every part must be a valid Java identifier). */
    static Path problemFolder(String problemId) {
        String[] p = problemId.split("-");
        return Paths.get("Unit_" + p[0], "Chapter_" + p[1], "Q_" + p[0] + "_" + p[1] + "_" + p[2]);
    }

    /** "01-02-03" -> Unit_01.Chapter_02.Q_01_02_03, the package every Main.java in that folder must declare. */
    static String packageName(String problemId) {
        String[] p = problemId.split("-");
        return "Unit_" + p[0] + ".Chapter_" + p[1] + ".Q_" + p[0] + "_" + p[1] + "_" + p[2];
    }

    static final Pattern FOLDER_NAME = Pattern.compile("^[Qq]_(\\d+)_(\\d+)_(\\d+)$");

    /** Accepts 01-02-03 or the folder name Q_01_02_03 and returns the ID, or the text unchanged. */
    static String normaliseProblemId(String text) {
        java.util.regex.Matcher m = FOLDER_NAME.matcher(text);
        return m.matches() ? m.group(1) + "-" + m.group(2) + "-" + m.group(3) : text;
    }

    /** Finds which package (if any) the compiled Main class ended up in, for a helpful error message. */
    static String findMainPackage(Path classDir) throws IOException {
        try (Stream<Path> walk = Files.walk(classDir)) {
            for (Path p : walk.sorted().collect(Collectors.toList())) {
                if (p.getFileName().toString().equals("Main.class")) {
                    Path parent = classDir.relativize(p).getParent();
                    return parent == null ? "" : parent.toString().replace('/', '.').replace('\\', '.');
                }
            }
        }
        return null;
    }

    static Compiled compile(JavaCompiler compiler, Path problemFolder, String problemId) throws IOException {
        Path out = Files.createTempDirectory(tempRoot, problemId + "-");
        List<Path> sources;
        try (Stream<Path> s = Files.list(problemFolder)) {
            sources = s.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean success;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            List<String> options = Arrays.asList("-d", out.toString(), "-encoding", "UTF-8", "-proc:none");
            success = compiler.getTask(null, fm, diagnostics, options, null, fm.getJavaFileObjectsFromPaths(sources)).call();
        }

        List<String> errors = new ArrayList<>();
        int shown = 0;
        int total = 0;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            total++;
            if (shown >= 8) {
                continue;
            }
            shown++;
            errors.add(describe(d));
        }
        if (total > shown) {
            errors.add("... and " + (total - shown) + " more error(s).");
        }
        if (!success && errors.isEmpty()) {
            errors.add("The code could not be compiled.");
        }
        String expectedPackage = packageName(problemId);
        String mainClass = expectedPackage + ".Main";
        if (success && !Files.exists(out.resolve(expectedPackage.replace('.', '/')).resolve("Main.class"))) {
            String found = findMainPackage(out);
            if (found == null) {
                errors.add("Main.java compiled, but no class named Main was found.\n"
                        + "The file must declare `public class Main`.");
            } else if (found.isEmpty()) {
                errors.add("Main.java has no package line.\n"
                        + "Put this as the very first line of Main.java:\n"
                        + "    package " + expectedPackage + ";");
            } else {
                errors.add("Main.java declares `package " + found + ";` but this problem's folder needs:\n"
                        + "    package " + expectedPackage + ";\n"
                        + "Change the first line of Main.java to match.");
            }
        }
        return new Compiled(out, mainClass, errors);
    }

    static String describe(Diagnostic<? extends JavaFileObject> d) {
        String file = d.getSource() == null ? "" : Paths.get(d.getSource().getName()).getFileName().toString();
        StringBuilder sb = new StringBuilder();
        sb.append(file).append(":").append(d.getLineNumber()).append(": error: ").append(d.getMessage(Locale.ENGLISH));
        try {
            if (d.getSource() != null && d.getLineNumber() > 0) {
                List<String> lines = Files.readAllLines(Paths.get(d.getSource().getName()), StandardCharsets.UTF_8);
                if (d.getLineNumber() <= lines.size()) {
                    sb.append("\n    ").append(lines.get((int) d.getLineNumber() - 1));
                    if (d.getColumnNumber() > 0) {
                        sb.append("\n    ").append(" ".repeat((int) d.getColumnNumber() - 1)).append("^");
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // the message alone is still useful
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------ running a program

    static final class RunResult {
        boolean timedOut;
        int exitCode;
        String stdout = "";
        String stderr = "";
        double millis;
        String launchError;
    }

    /** Reads a stream on its own thread (so a full pipe can never block the child), keeping a bounded copy. */
    static final class Capture extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        Capture(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    synchronized (buffer) {
                        int room = MAX_CAPTURED_BYTES - buffer.size();
                        if (room > 0) {
                            buffer.write(chunk, 0, Math.min(room, n));
                        }
                        if (n > room) {
                            truncated = true;
                        }
                    }
                }
            } catch (IOException ignored) {
                // the process was killed; keep what we have
            }
        }

        String text() {
            synchronized (buffer) {
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8)
                        + (truncated ? "\n...[output cut off]" : "");
            }
        }
    }

    static String javaExecutable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Paths.get(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    static RunResult runProgram(Path classDir, String mainClass, Path workingDir, byte[] input, double timeoutSeconds) {
        RunResult r = new RunResult();
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable());
        cmd.addAll(JVM_ARGS);
        cmd.add("-cp");
        cmd.add(classDir.toAbsolutePath().toString());
        cmd.add(mainClass);

        long start = System.nanoTime();
        try {
            Process p = new ProcessBuilder(cmd).directory(workingDir.toFile()).start();
            Capture out = new Capture(p.getInputStream());
            Capture err = new Capture(p.getErrorStream());
            out.start();
            err.start();

            Thread feeder = new Thread(() -> {
                try (OutputStream os = p.getOutputStream()) {
                    os.write(input);
                    os.flush();
                } catch (IOException ignored) {
                    // the program exited without reading all of its input; that is fine
                }
            });
            feeder.setDaemon(true);
            feeder.start();

            boolean finished = p.waitFor((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
            if (!finished) {
                r.timedOut = true;
                p.destroyForcibly();
                p.waitFor();
            }
            r.millis = (System.nanoTime() - start) / 1e6;
            out.join(2000);
            err.join(2000);
            r.exitCode = finished ? p.exitValue() : -1;
            r.stdout = out.text();
            r.stderr = err.text();
        } catch (IOException e) {
            r.launchError = e.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            r.launchError = "Interrupted";
        }
        return r;
    }

    // ------------------------------------------------------------------------------ reporting helpers

    static String clip(String s) {
        if (s.length() <= MAX_SHOWN_CHARS) {
            return s;
        }
        return s.substring(0, MAX_SHOWN_CHARS) + "\n... (" + (s.length() - MAX_SHOWN_CHARS) + " more characters not shown)";
    }

    /** JAVA_TOOL_OPTIONS makes every JVM print a "Picked up ..." line; it is noise for students. */
    static String cleanErrorText(String stderr) {
        return Arrays.stream(stderr.strip().split("\\R"))
                .filter(line -> !line.startsWith("Picked up "))
                .collect(Collectors.joining("\n"))
                .strip();
    }

    static String ms(double millis) {
        return String.format(Locale.ROOT, "%.0f ms", millis);
    }

    static double getTimeout(Path testFolder) {
        Path file = testFolder.resolve("timeout.txt");
        if (Files.exists(file)) {
            try {
                double t = Double.parseDouble(Files.readString(file).strip());
                return Math.max(MIN_TIMEOUT_SECONDS, Math.min(t, MAX_TIMEOUT_SECONDS));
            } catch (IOException | NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return DEFAULT_TIMEOUT_SECONDS;
    }

    // ------------------------------------------------------------------------------ running tests

    /** Runs one test case, prints the result, and returns true if it passed. */
    static boolean runOneTest(String testId, Path inFile, Path outFile, Path classDir, String mainClass,
                              Path workingDir, double timeout) throws IOException {
        byte[] input = Files.readAllBytes(inFile);
        String inputText = new String(input, StandardCharsets.UTF_8);
        String expected = new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8).strip();

        RunResult r = runProgram(classDir, mainClass, workingDir, input, timeout);
        String actual = r.stdout.strip();
        boolean outputOk = !r.timedOut && r.launchError == null && compareOutputs(expected, actual);
        boolean passed = outputOk && r.exitCode == 0;
        double shownMillis = r.timedOut ? timeout * 1000 : r.millis;

        if (passed) {
            say(testId + ": " + GREEN + "PASS " + RESET + "(" + ms(shownMillis) + ")");
            return true;
        }

        say(testId + ": " + RED + "FAIL " + RESET + "(" + ms(shownMillis) + ")");
        if (r.launchError != null) {
            sayColoured("\nCould not start the program:", YELLOW);
            say(r.launchError);
        } else if (r.timedOut) {
            String limit = timeout == Math.rint(timeout) ? String.valueOf((long) timeout) : String.valueOf(timeout);
            sayColoured("\nTime Limit Exceeded! (limit: " + limit + " s)", YELLOW);
            say("Check for an infinite loop, or a Scanner that is still waiting for more input than the test provides.");
            say("\nFor Input");
            say(clip(inputText));
        } else {
            if (!outputOk) {
                sayColoured("\nOutput does not match expected output:", YELLOW);
            } else {
                sayColoured("\nThe output matched, but the program ended with an error (exit code " + r.exitCode + "):", YELLOW);
            }
            sayColoured("\nFor Input", YELLOW);
            say(clip(inputText));
            sayColoured("\nExpected Output:", YELLOW);
            say(clip(expected));
            sayColoured("\nActual Output:", YELLOW);
            say(clip(actual));
            String errText = cleanErrorText(r.stderr);
            if (!errText.isEmpty()) {
                sayColoured("\nError Output:", RED);
                say(clip(errText));
            }
        }
        say("=".repeat(40));
        return false;
    }

    static List<String> orderedBatches(Map<String, List<String>> batches) {
        List<String> order = new ArrayList<>(batches.keySet());
        order.sort((a, b) -> {
            int ka = a.equals("sample") ? 0 : 1;
            int kb = b.equals("sample") ? 0 : 1;
            if (ka != kb) {
                return ka - kb;
            }
            long na = a.matches("\\d{1,9}") ? Long.parseLong(a) : Long.MAX_VALUE;
            long nb = b.matches("\\d{1,9}") ? Long.parseLong(b) : Long.MAX_VALUE;
            int c = Long.compare(na, nb);
            return c != 0 ? c : a.compareTo(b);
        });
        return order;
    }

    /** Returns {passed, failed}. */
    static int[] runIoTests(Path testFolder, Path classDir, String mainClass, Path workingDir) throws IOException {
        Map<String, List<String>> batches = new TreeMap<>();
        try (Stream<Path> files = Files.list(testFolder)) {
            for (Path p : files.sorted().collect(Collectors.toList())) {
                String name = p.getFileName().toString();
                if (!name.endsWith(".in")) {
                    continue;
                }
                int dash = name.lastIndexOf('-');
                String batch = dash > 0 ? name.substring(0, dash) : name;
                batches.computeIfAbsent(batch, k -> new ArrayList<>()).add(name.substring(0, name.length() - 3));
            }
        }
        if (batches.isEmpty()) {
            return new int[]{0, 0};
        }

        double timeout = getTimeout(testFolder);
        int passed = 0;
        int failed = 0;

        for (String batch : orderedBatches(batches)) {
            say("=".repeat(40));
            say("Running batch " + batch + " tests...");
            List<String> ids = batches.get(batch);
            ids.sort(Comparator.naturalOrder());
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                Path outFile = testFolder.resolve(id + ".out");
                if (!Files.exists(outFile)) {
                    say("Missing expected output file: " + outFile);
                    continue;
                }
                if (runOneTest(id, testFolder.resolve(id + ".in"), outFile, classDir, mainClass, workingDir, timeout)) {
                    passed++;
                    continue;
                }
                failed++;
                int skipped = ids.size() - i - 1;
                if (batch.equals("sample")) {
                    if (skipped > 0) {
                        say("Skipped " + skipped + " remaining sample test(s).");
                    }
                    say("Sample tests failed. Aborting further testing.");
                    return new int[]{passed, failed};
                }
                if (skipped > 0) {
                    say("Skipped " + skipped + " remaining test(s) in batch " + batch + ".");
                }
                break;
            }
        }
        return new int[]{passed, failed};
    }

    static final class Outcome {
        final String status; // PASS, FAIL or SKIP
        final int passed;
        final int failed;

        Outcome(String status, int passed, int failed) {
            this.status = status;
            this.passed = passed;
            this.failed = failed;
        }
    }

    static Outcome runTestsForProblem(JavaCompiler compiler, String problemId, boolean runningAll) {
        if (!problemId.matches("\\d+-\\d+-\\d+")) {
            say("Skipping " + problemId + ": that is not a problem ID.");
            say("Expected format: <unit>-<chapter>-<problem>, for example 01-02-01");
            return new Outcome("SKIP", 0, 0);
        }
        Path folder = problemFolder(problemId);
        Path mainFile = folder.resolve("Main.java");
        if (!Files.exists(mainFile)) {
            say("Skipping " + problemId + ": student file not found: " + mainFile);
            return new Outcome("SKIP", 0, 0);
        }
        Path testFolder = Paths.get("tests", problemId);
        if (!Files.isDirectory(testFolder)) {
            say("Test folder not found: " + testFolder);
            return new Outcome("SKIP", 0, 0);
        }

        try {
            if (runningAll && Files.readString(mainFile).contains(STARTER_MARKER)) {
                say("Skipping " + problemId + ": Main.java is still the starter file.");
                return new Outcome("SKIP", 0, 0);
            }

            say("\nRunning tests for " + problemId + "...");
            Compiled compiled = compile(compiler, folder, problemId);
            if (!compiled.ok()) {
                say(problemId + ": " + RED + "COMPILE ERROR" + RESET);
                for (String e : compiled.errors) {
                    say(e);
                }
                return new Outcome("FAIL", 0, 1);
            }

            int[] result = runIoTests(testFolder, compiled.classDir, compiled.mainClass, folder);
            String status = result[1] > 0 ? "FAIL" : "PASS";
            if (!runningAll) {
                say("=".repeat(40));
                if (status.equals("PASS")) {
                    sayColoured(problemId + ": all " + result[0] + " tests passed.", GREEN);
                } else {
                    sayColoured(problemId + ": " + result[0] + " passed, " + result[1] + " failed.", RED);
                }
            }
            return new Outcome(status, result[0], result[1]);
        } catch (IOException e) {
            say("Problem while testing " + problemId + ": " + e);
            return new Outcome("FAIL", 0, 1);
        }
    }

    static int compareProblemIds(String a, String b) {
        String[] pa = a.split("-");
        String[] pb = b.split("-");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            boolean na = pa[i].matches("\\d{1,9}");
            boolean nb = pb[i].matches("\\d{1,9}");
            int c = (na && nb) ? Integer.compare(Integer.parseInt(pa[i]), Integer.parseInt(pb[i])) : pa[i].compareTo(pb[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }

    // ------------------------------------------------------------------------------ main

    static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp files; the OS will clean up eventually
        }
    }

    static void usage() {
        say("Usage:");
        say("  java Test.java              run every problem that has tests");
        say("  java Test.java 01-01-01     run a single problem (Q_01_01_01, the folder name, also works)");
        say("Options: --no-color  --color");
    }

    public static void main(String[] args) throws Exception {
        String problem = null;
        Boolean colour = null;
        for (String a : args) {
            if (a.equals("--no-color")) {
                colour = false;
            } else if (a.equals("--color")) {
                colour = true;
            } else if (a.equals("-h") || a.equals("--help")) {
                usage();
                return;
            } else if (problem == null && !a.startsWith("-")) {
                problem = normaliseProblemId(a);
            } else {
                usage();
                System.exit(2);
            }
        }
        if (colour == null ? terminalProbablySupportsColour() : colour) {
            enableColour();
        }

        if (!Files.isDirectory(Paths.get("tests"))) {
            say("Could not find the tests folder. Run this from the repository root,");
            say("the folder that contains Test.java and tests/.");
            System.exit(2);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            say("This Java installation cannot compile code: it has the runtime (JRE) but not the JDK.");
            say("Install a JDK (Java 11 or newer) and try again.");
            System.exit(2);
        }

        tempRoot = Files.createTempDirectory("java-practice-");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(tempRoot)));

        if (problem != null) {
            Outcome o = runTestsForProblem(compiler, problem, false);
            System.exit(o.status.equals("FAIL") ? 1 : 0);
        }

        say("Running all tests...");
        List<String> ids;
        try (Stream<Path> dirs = Files.list(Paths.get("tests"))) {
            ids = dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                    .sorted(Test::compareProblemIds).collect(Collectors.toList());
        }

        int questionsPassed = 0;
        int questionsFailed = 0;
        int questionsSkipped = 0;
        int casesPassed = 0;
        int casesFailed = 0;
        for (String id : ids) {
            Outcome o = runTestsForProblem(compiler, id, true);
            switch (o.status) {
                case "PASS":
                    questionsPassed++;
                    break;
                case "FAIL":
                    questionsFailed++;
                    break;
                default:
                    questionsSkipped++;
            }
            casesPassed += o.passed;
            casesFailed += o.failed;
            say("-".repeat(50));
        }

        say("\n" + "=".repeat(40));
        say("           TEST SUMMARY");
        say("=".repeat(40));
        say("QUESTIONS CHECKED:      " + ids.size());
        say("Questions Fully Passed: " + GREEN + questionsPassed + RESET);
        say("Questions Failed:       " + RED + questionsFailed + RESET);
        say("Questions Skipped:      " + YELLOW + questionsSkipped + RESET);
        say("-".repeat(40));
        say("Total Test Cases Passed: " + GREEN + casesPassed + RESET);
        say("Total Test Cases Failed: " + RED + casesFailed + RESET);
        say("=".repeat(40));
        System.exit(questionsFailed == 0 ? 0 : 1);
    }
}
