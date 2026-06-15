package com.hivemq.platform.demo.console;

public class ConsoleProgress {

    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    // carriage-return + ANSI "erase entire line" so every repaint starts from a clean line
    private static final String CLEAR_LINE = "\r" + (char) 27 + "[2K";
    private static final long FRAME_MILLIS = 100;

    // true only for a real interactive terminal; otherwise (piped, redirected, CI) we degrade to
    // plain lines and never emit carriage returns / ANSI, so logs and files stay clean.
    private final boolean interactive = detectInteractive();
    private final int total;
    private final Object lock = new Object();

    private int step = 0;
    private volatile boolean spinning = false;
    private volatile String spinnerLabel = "";
    private Thread spinnerThread;

    public ConsoleProgress(final int total) {
        this.total = total;
    }

    // System.console() is unreliable on Java 22+ / GraalVM native (often non-null when redirected, or
    // null on a real tty), so prefer Console#isTerminal and fall back to the TERM env var for native.
    private static boolean detectInteractive() {
        final var console = System.console();
        if (console != null) {
            return console.isTerminal();
        }
        final var term = System.getenv("TERM");
        return term != null && !term.isBlank() && !"dumb".equals(term);
    }

    // advance the step counter and print a permanent "[n/total] label" header (no spinner)
    public void phase(final String label) {
        stopSpinner();
        synchronized (lock) {
            step++;
            printLine(header(label));
        }
    }

    // advance the step counter and keep a live spinner until the next phase()/done()/fail()
    public void phaseSpinning(final String label) {
        stopSpinner();
        synchronized (lock) {
            step++;
            spinnerLabel = header(label);
            if (!interactive) {
                printLine(spinnerLabel);
            }
        }
        if (interactive) {
            startSpinner();
        }
    }

    // print a permanent line; if a spinner is live it is cleared first and re-rendered afterwards
    public void log(final String message) {
        synchronized (lock) {
            printLine(message);
        }
    }

    public void done(final String message) {
        stopSpinner();
        synchronized (lock) {
            printLine(message);
        }
    }

    public void fail(final String message) {
        stopSpinner();
        synchronized (lock) {
            printLine("✗ " + message);
        }
    }

    private String header(final String label) {
        return "[" + step + "/" + total + "] " + label;
    }

    private void printLine(final String line) {
        if (interactive) {
            System.out.print(CLEAR_LINE);
        }
        System.out.println(line);
        System.out.flush();
    }

    private void startSpinner() {
        spinning = true;
        spinnerThread = new Thread(
                () -> {
                    var i = 0;
                    while (spinning) {
                        synchronized (lock) {
                            System.out.print(CLEAR_LINE + FRAMES[i % FRAMES.length] + " " + spinnerLabel);
                            System.out.flush();
                        }
                        i++;
                        try {
                            Thread.sleep(FRAME_MILLIS);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                },
                "console-progress");
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    private void stopSpinner() {
        if (!spinning) {
            return;
        }
        spinning = false;
        final var thread = spinnerThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(200);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (lock) {
            if (interactive) {
                System.out.print(CLEAR_LINE);
                System.out.flush();
            }
        }
    }
}
