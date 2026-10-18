package storage;

import exceptions.FigureFormatException;
import geometry2d.Circle;
import geometry2d.Figure;
import geometry2d.Rectangle;
import geometry3d.Cylinder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Бинарное хранилище на стандартной сериализации Java.
 *
 * <p>В файл пишется один объект — {@link ArrayList} с фигурами. При чтении проверяется,
 * что это именно список, а все его элементы — {@link Figure}. Дополнительно включён
 * белый список классов ({@link ObjectInputFilter}): десериализация чужих файлов
 * может выполнить посторонний код, поэтому разрешены только классы этой работы.
 */
public class BinaryFigureStorage implements FigureStorage {

    private static final Set<Class<?>> ALLOWED_CLASSES =
            Set.of(ArrayList.class, Circle.class, Rectangle.class, Cylinder.class);

    @Override
    public List<Figure> read(Path path) throws IOException, FigureFormatException, ClassNotFoundException {
        Objects.requireNonNull(path, "path");
        try (InputStream file = Files.newInputStream(path);
             ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(file))) {
            in.setObjectInputFilter(BinaryFigureStorage::filter);

            Object object = in.readObject();
            if (!(object instanceof List<?> list)) {
                String actual = object == null ? "null" : object.getClass().getName();
                throw new FigureFormatException("Файл содержит объект типа " + actual + ", а не список фигур");
            }

            List<Figure> figures = new ArrayList<>(list.size());
            int index = 0;
            for (Object element : list) {
                index++;
                if (!(element instanceof Figure figure)) {
                    String actual = element == null ? "null" : element.getClass().getName();
                    throw new FigureFormatException("Элемент списка №" + index + " (" + actual + ") не является фигурой");
                }
                figures.add(figure);
            }
            return figures;
        }
    }

    @Override
    public void write(Path path, List<Figure> figures) throws IOException, FigureFormatException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(figures, "figures");
        for (int i = 0; i < figures.size(); i++) {
            if (figures.get(i) == null) {
                throw new FigureFormatException("Элемент списка №" + (i + 1) + " равен null");
            }
        }

        // Копия в ArrayList: сериализуемая реализация списка известна заранее.
        List<Figure> copy = new ArrayList<>(figures);
        try (OutputStream file = Files.newOutputStream(path);
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(file))) {
            out.writeObject(copy);
        }
    }

    private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
        Class<?> type = info.serialClass();
        if (type == null || type.isArray()) {
            // Проверки лимитов потока и массивов; элементы массивов проверяются как отдельные объекты.
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ALLOWED_CLASSES.contains(type)
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
    }
}
