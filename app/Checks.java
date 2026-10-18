package app;

import exceptions.FigureFormatException;
import exceptions.InvalidFigureException;
import geometry2d.Circle;
import geometry2d.Figure;
import geometry2d.Rectangle;
import geometry3d.Cylinder;
import storage.BinaryFigureStorage;
import storage.FigureStorage;
import storage.TextFigureStorage;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Воспроизводимые проверки из чек-листа приёмки. Запуск из корня проекта:
 * {@code java -cp build app.Checks data}. Для каждой проверки печатается ожидаемый
 * или фактический результат; код завершения 1, если хотя бы одна проверка не прошла.
 */
public final class Checks {

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class Captured {
        final int exitCode;
        final String out;
        final String err;

        Captured(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }
    }

    private static int passed = 0;
    private static int failed = 0;

    private Checks() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
        Path tmp = Files.createTempDirectory("lab3-checks");
        try {
            checkGeometry();
            checkInvalidParameters();
            checkImmutability();
            checkFinally(dataDir);
            checkTextReading(dataDir, tmp);
            checkEmptyLists(dataDir, tmp);
            checkFileErrors(tmp);
            checkRoundTrip(tmp);
            checkBinaryErrors(tmp);
            checkMainScenario(dataDir, tmp);
        } finally {
            deleteRecursively(tmp);
        }
        System.out.println();
        System.out.println("Итого: пройдено " + passed + ", провалено " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- проверки

    private static void checkGeometry() {
        section("Площади, периметры, объёмы, toString");
        Circle circle = new Circle(2.5);
        checkNear("Circle(2.5).area()", Math.PI * 6.25, circle.area());
        checkNear("Circle(2.5).perimeter()", 5 * Math.PI, circle.perimeter());
        Rectangle rectangle = new Rectangle(3, 4);
        checkNear("Rectangle(3, 4).area()", 12, rectangle.area());
        checkNear("Rectangle(3, 4).perimeter()", 14, rectangle.perimeter());
        checkNear("Cylinder(Circle(2), 5).volume()", 20 * Math.PI, new Cylinder(new Circle(2), 5).volume());
        checkNear("Cylinder(Rectangle(3, 4), 2).volume()", 24, new Cylinder(new Rectangle(3, 4), 2).volume());
        checkEquals("Circle.toString()", "Circle(radius=2.5)", circle.toString());
        checkEquals("Rectangle.toString()", "Rectangle(sideA=3.0, sideB=4.0)", rectangle.toString());
        checkEquals("Cylinder.toString()", "Cylinder(base=Circle(radius=2.0), height=5.0)",
                new Cylinder(new Circle(2), 5).toString());
    }

    private static void checkInvalidParameters() {
        section("Конструкторы отклоняют недопустимые значения (InvalidFigureException)");
        double[] circleValues = {0, -0.0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double v : circleValues) {
            InvalidFigureException e = expectThrows("new Circle(" + v + ")",
                    InvalidFigureException.class, () -> new Circle(v));
            if (e != null) {
                checkEquals("  параметр в исключении", "radius", e.getParameter());
            }
        }
        double[] otherValues = {0, -2, Double.NaN, Double.POSITIVE_INFINITY};
        for (double v : otherValues) {
            expectThrows("new Rectangle(" + v + ", 1)", InvalidFigureException.class, () -> new Rectangle(v, 1));
            expectThrows("new Rectangle(1, " + v + ")", InvalidFigureException.class, () -> new Rectangle(1, v));
            expectThrows("new Cylinder(Circle(1), " + v + ")", InvalidFigureException.class,
                    () -> new Cylinder(new Circle(1), v));
        }
        InvalidFigureException e = expectThrows("new Cylinder(null, 1)",
                InvalidFigureException.class, () -> new Cylinder(null, 1));
        if (e != null) {
            checkEquals("  параметр в исключении", "base", e.getParameter());
        }
    }

    private static void checkImmutability() {
        section("Поля private final, сеттеров нет");
        for (Class<?> type : List.of(Circle.class, Rectangle.class, Cylinder.class)) {
            boolean fieldsOk = true;
            for (Field field : type.getDeclaredFields()) {
                int m = field.getModifiers();
                if (!Modifier.isStatic(m) && !(Modifier.isPrivate(m) && Modifier.isFinal(m))) {
                    fieldsOk = false;
                }
            }
            boolean noSetters = Arrays.stream(type.getMethods()).noneMatch(m -> m.getName().startsWith("set"));
            checkTrue(type.getSimpleName() + ": все поля private final", fieldsOk, "");
            checkTrue(type.getSimpleName() + ": публичных set-методов нет", noSetters, "");
        }
    }

    private static void checkFinally(Path dataDir) {
        section("finally: успешное выполнение и исключение");
        String output = captureOut(() -> {
            FinallyDemo.readFirstLine(dataDir.resolve("figures.txt"));
            FinallyDemo.createCircle(1.5);
            FinallyDemo.readFirstLine(Path.of("no-such-file.txt"));
            FinallyDemo.createCircle(-1);
        });
        for (String line : output.split("\\R")) {
            System.out.println("       | " + line);
        }
        int count = output.split("\\[finally]", -1).length - 1;
        checkEquals("блок finally выполнен 4 раза (2 успеха + 2 исключения)", 4, count);
    }

    private static void checkTextReading(Path dataDir, Path tmp) throws Exception {
        section("Текстовое чтение: корректный файл и ошибки формата (FigureFormatException)");
        FigureStorage text = new TextFigureStorage();
        List<Figure> expected = List.of(new Circle(2.5), new Rectangle(3, 4), new Circle(1),
                new Rectangle(10.5, 0.5), new Circle(7.25));
        checkEquals("data/figures.txt (круги и прямоугольники, комментарий и пустая строка пропущены)",
                expected, text.read(dataDir.resolve("figures.txt")));

        expectBadTextFile(text, dataDir.resolve("bad-unknown-type.txt"), 2, null);
        expectBadTextFile(text, dataDir.resolve("bad-param-count.txt"), 2, null);
        expectBadTextFile(text, dataDir.resolve("bad-number.txt"), 2, NumberFormatException.class);
        expectBadTextFile(text, dataDir.resolve("bad-negative.txt"), 2, InvalidFigureException.class);
        System.out.println("       (при ошибке загрузка отклоняется целиком: уже прочитанные фигуры не возвращаются)");

        Path bom = tmp.resolve("bom.txt");
        byte[] bomBytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "CIRCLE;2\n# комментарий в UTF-8\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bomBytes.length + body.length];
        System.arraycopy(bomBytes, 0, withBom, 0, bomBytes.length);
        System.arraycopy(body, 0, withBom, bomBytes.length, body.length);
        Files.write(bom, withBom);
        checkEquals("UTF-8 с BOM и кириллицей", List.of(new Circle(2)), text.read(bom));

        Path cp1251 = tmp.resolve("cp1251.txt");
        Files.write(cp1251, "# комментарий\nCIRCLE;2\n".getBytes(Charset.forName("windows-1251")));
        expectThrows("файл не в UTF-8 (windows-1251) отклоняется", IOException.class, () -> text.read(cp1251));
    }

    private static void checkEmptyLists(Path dataDir, Path tmp) throws Exception {
        section("Пустой файл и пустой список");
        FigureStorage text = new TextFigureStorage();
        FigureStorage binary = new BinaryFigureStorage();
        checkEquals("пустой data/empty.txt -> пустой список", List.<Figure>of(), text.read(dataDir.resolve("empty.txt")));

        for (FigureStorage storage : List.of(text, binary)) {
            String name = storage.getClass().getSimpleName();
            Path file = tmp.resolve("empty-list-" + name);
            storage.write(file, List.of());
            checkEquals(name + ": сохранение пустого списка и чтение", List.<Figure>of(), storage.read(file));
        }
    }

    private static void checkFileErrors(Path tmp) throws Exception {
        section("Отсутствующий файл и ошибка записи");
        FigureStorage text = new TextFigureStorage();
        FigureStorage binary = new BinaryFigureStorage();
        Path missing = tmp.resolve("missing-file");
        expectThrows("текстовое чтение несуществующего файла", NoSuchFileException.class, () -> text.read(missing));
        expectThrows("бинарное чтение несуществующего файла", NoSuchFileException.class, () -> binary.read(missing));
        // Путь существующего каталога: открыть его как файл для записи нельзя.
        expectThrows("текстовая запись по пути каталога", IOException.class, () -> text.write(tmp, List.of(new Circle(1))));
        expectThrows("бинарная запись по пути каталога", IOException.class, () -> binary.write(tmp, List.of(new Circle(1))));
    }

    private static void checkRoundTrip(Path tmp) throws Exception {
        section("Запись и повторное чтение в обоих форматах через общий интерфейс FigureStorage");
        List<Figure> sample = List.of(new Circle(1.5), new Rectangle(2, 3), new Circle(0.1),
                new Rectangle(1e-3, 12345.678), new Circle(1e10));
        List<FigureStorage> storages = List.of(new TextFigureStorage(), new BinaryFigureStorage());
        for (FigureStorage storage : storages) {
            String name = storage.getClass().getSimpleName();
            Path file = tmp.resolve("roundtrip-" + name);
            storage.write(file, sample);
            List<Figure> back = storage.read(file);
            checkEquals(name + ": параметры и порядок списка совпадают", sample, back);

            boolean sameTypes = true;
            boolean sameResults = back.size() == sample.size();
            for (int i = 0; sameResults && i < sample.size(); i++) {
                sameTypes &= sample.get(i).getClass() == back.get(i).getClass();
                sameResults &= Double.compare(sample.get(i).area(), back.get(i).area()) == 0
                        && Double.compare(sample.get(i).perimeter(), back.get(i).perimeter()) == 0;
            }
            checkTrue(name + ": конкретные типы фигур сохранены", sameTypes, "");
            checkTrue(name + ": площади и периметры совпадают", sameResults, "");

            Path overwritten = tmp.resolve("overwrite-" + name);
            storage.write(overwritten, sample);
            storage.write(overwritten, List.of(new Circle(9)));
            checkEquals(name + ": повторная запись заменяет содержимое", List.of(new Circle(9)),
                    storage.read(overwritten));
        }

        Path textFile = tmp.resolve("lines.txt");
        new TextFigureStorage().write(textFile, List.of(new Circle(1.5), new Rectangle(2, 3)));
        checkEquals("содержимое текстового файла (UTF-8)", List.of("CIRCLE;1.5", "RECTANGLE;2.0;3.0"),
                Files.readAllLines(textFile, StandardCharsets.UTF_8));

        Figure unsupported = new Figure() {
            @Override
            public double area() {
                return 1;
            }

            @Override
            public double perimeter() {
                return 1;
            }
        };
        Path guarded = tmp.resolve("guarded.txt");
        TextFigureStorage textStorage = new TextFigureStorage();
        textStorage.write(guarded, sample);
        expectThrows("текстовая запись неподдерживаемого типа фигуры", FigureFormatException.class,
                () -> textStorage.write(guarded, List.of(new Circle(1), unsupported)));
        checkEquals("  прежний файл не повреждён", sample, textStorage.read(guarded));
    }

    private static void checkBinaryErrors(Path tmp) throws Exception {
        section("Бинарное чтение: повреждённый файл и объект другого типа");
        BinaryFigureStorage binary = new BinaryFigureStorage();

        Path garbage = tmp.resolve("garbage.bin");
        Files.writeString(garbage, "это совсем не сериализованный объект", StandardCharsets.UTF_8);
        expectThrows("файл с произвольным текстом", StreamCorruptedException.class, () -> binary.read(garbage));

        Path zeroBytes = tmp.resolve("zero.bin");
        Files.write(zeroBytes, new byte[0]);
        expectThrows("файл нулевой длины", EOFException.class, () -> binary.read(zeroBytes));

        Path valid = tmp.resolve("valid.bin");
        binary.write(valid, List.of(new Circle(1), new Rectangle(2, 3), new Circle(4)));
        byte[] whole = Files.readAllBytes(valid);
        Path truncated = tmp.resolve("truncated.bin");
        Files.write(truncated, Arrays.copyOf(whole, whole.length / 2));
        expectThrows("обрезанный файл", IOException.class, () -> binary.read(truncated));

        Path notList = tmp.resolve("not-list.bin");
        Files.write(notList, serialize(new Circle(1)));
        expectThrows("в файле одиночная фигура, а не список", FigureFormatException.class, () -> binary.read(notList));

        Path notFigures = tmp.resolve("not-figures.bin");
        Files.write(notFigures, serialize(new ArrayList<>(List.of(new Cylinder(new Circle(1), 2)))));
        expectThrows("в списке элемент, не являющийся фигурой (Cylinder)", FigureFormatException.class,
                () -> binary.read(notFigures));

        Path stringList = tmp.resolve("string-list.bin");
        Files.write(stringList, serialize(new ArrayList<>(List.of("строка"))));
        expectThrows("в списке строка: элемент не является фигурой", FigureFormatException.class,
                () -> binary.read(stringList));

        Path foreign = tmp.resolve("foreign.bin");
        Files.write(foreign, serialize(new ArrayList<>(List.of(42))));
        expectThrows("в списке посторонний класс (Integer): отсекает белый список ObjectInputFilter",
                InvalidClassException.class, () -> binary.read(foreign));

        // Подделка: в потоке радиус 1.0 заменяется на -1.0. Конструктор при десериализации
        // не вызывается, поэтому спасает проверка в readObject().
        byte[] forged = serialize(new ArrayList<>(List.of(new Circle(1.0))));
        byte[] one = ByteBuffer.allocate(8).putDouble(1.0).array();
        byte[] minusOne = ByteBuffer.allocate(8).putDouble(-1.0).array();
        int position = indexOf(forged, one);
        checkTrue("в потоке найдено значение радиуса", position >= 0, "смещение " + position);
        System.arraycopy(minusOne, 0, forged, position, minusOne.length);
        Path forgedFile = tmp.resolve("forged.bin");
        Files.write(forgedFile, forged);
        InvalidObjectException e = expectThrows("подделанный файл с радиусом -1",
                InvalidObjectException.class, () -> binary.read(forgedFile));
        if (e != null) {
            checkTrue("  причина сохранена: InvalidFigureException", e.getCause() instanceof InvalidFigureException,
                    String.valueOf(e.getCause()));
        }
        System.out.println("       (ClassNotFoundException объявлен в throws интерфейса и обрабатывается в Main)");
    }

    private static void checkMainScenario(Path dataDir, Path tmp) throws Exception {
        section("Сценарий Main");
        FigureStorage text = new TextFigureStorage();
        FigureStorage binary = new BinaryFigureStorage();

        Path out = tmp.resolve("main-out.bin");
        Captured ok = runMain(dataDir.resolve("figures.txt").toString(), out.toString(), "5");
        printIndented(ok);
        checkEquals("код завершения", 0, ok.exitCode);
        checkTrue("выведены площади и периметры", ok.out.contains("площадь =") && ok.out.contains("периметр ="), "");
        checkTrue("выведены основание, высота и объём цилиндра",
                ok.out.contains("Основание цилиндра") && ok.out.contains("Высота цилиндра: 5.0")
                        && ok.out.contains("Объём цилиндра"), "");
        checkTrue("сообщение о записи выведено", ok.out.contains("Список сохранён"), "");
        checkEquals("бинарный файл содержит тот же список", text.read(dataDir.resolve("figures.txt")), binary.read(out));

        Path emptyOut = tmp.resolve("main-empty.bin");
        Captured empty = runMain(dataDir.resolve("empty.txt").toString(), emptyOut.toString());
        printIndented(empty);
        checkEquals("пустой список: код завершения", 0, empty.exitCode);
        checkTrue("пустой список: цилиндр пропущен", empty.out.contains("Список пуст") && !empty.out.contains("Объём"), "");
        checkEquals("пустой список: бинарный файл пуст", List.<Figure>of(), binary.read(emptyOut));

        Captured bad = runMain(dataDir.resolve("bad-number.txt").toString(), tmp.resolve("never.bin").toString());
        printIndented(bad);
        checkEquals("некорректная строка: код завершения", 1, bad.exitCode);
        checkTrue("некорректная строка: указан номер строки", bad.err.contains("Строка 2"), "");
        checkTrue("некорректная строка: выходной файл не создан", Files.notExists(tmp.resolve("never.bin")), "");

        Captured missing = runMain(tmp.resolve("missing.txt").toString(), tmp.resolve("x.bin").toString());
        printIndented(missing);
        checkEquals("нет входного файла: код завершения", 1, missing.exitCode);

        Path badHeightOut = tmp.resolve("bad-height.bin");
        Captured badHeight = runMain(dataDir.resolve("figures.txt").toString(), badHeightOut.toString(), "-5");
        printIndented(badHeight);
        checkEquals("недопустимая высота: код завершения", 3, badHeight.exitCode);
        checkEquals("недопустимая высота: список всё равно сохранён", text.read(dataDir.resolve("figures.txt")),
                binary.read(badHeightOut));

        Captured notNumber = runMain(dataDir.resolve("figures.txt").toString(), tmp.resolve("y.bin").toString(), "abc");
        printIndented(notNumber);
        checkEquals("высота не число: код завершения", 2, notNumber.exitCode);
    }

    // ---------------------------------------------------------------- вспомогательные методы

    private static void expectBadTextFile(FigureStorage storage, Path file, int expectedLine,
                                          Class<? extends Throwable> expectedCause) {
        FigureFormatException e = expectThrows(file.getFileName().toString(), FigureFormatException.class,
                () -> storage.read(file));
        if (e == null) {
            return;
        }
        checkEquals("  номер строки в исключении", expectedLine, e.getLineNumber());
        if (expectedCause != null) {
            checkTrue("  исходная причина сохранена: " + expectedCause.getSimpleName(),
                    expectedCause.isInstance(e.getCause()), String.valueOf(e.getCause()));
        }
    }

    private static Captured runMain(String... args) {
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            int code = Main.run(args, new Random(42));
            return new Captured(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }

    private static String captureOut(Runnable action) {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(oldOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void printIndented(Captured captured) {
        for (String line : captured.out.split("\\R")) {
            if (!line.isEmpty()) {
                System.out.println("       | " + line);
            }
        }
        for (String line : captured.err.split("\\R")) {
            if (!line.isEmpty()) {
                System.out.println("       ! " + line);
            }
        }
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title);
    }

    private static void ok(String title, String details) {
        passed++;
        System.out.println("[OK]   " + title + (details.isEmpty() ? "" : " -> " + details));
    }

    private static void fail(String title, String details) {
        failed++;
        System.out.println("[FAIL] " + title + " -> " + details);
    }

    private static void checkTrue(String title, boolean condition, String details) {
        if (condition) {
            ok(title, details);
        } else {
            fail(title, "условие не выполнено " + details);
        }
    }

    private static void checkEquals(String title, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            ok(title, String.valueOf(actual));
        } else {
            fail(title, "ожидалось " + expected + ", получено " + actual);
        }
    }

    private static void checkNear(String title, double expected, double actual) {
        if (Math.abs(expected - actual) < 1e-9) {
            ok(title, String.valueOf(actual));
        } else {
            fail(title, "ожидалось " + expected + ", получено " + actual);
        }
    }

    /** Проверяет, что действие выбрасывает исключение заданного типа; возвращает его или null. */
    private static <T extends Throwable> T expectThrows(String title, Class<T> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception e) {
            if (type.isInstance(e)) {
                ok(title, e.getClass().getSimpleName() + ": " + e.getMessage());
                return type.cast(e);
            }
            fail(title, "ожидалось " + type.getSimpleName() + ", получено " + e);
            return null;
        }
        fail(title, "исключение не выброшено, ожидалось " + type.getSimpleName());
        return null;
    }
}
